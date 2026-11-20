"""End-to-end API scenario against a running, freshly seeded stack (stdlib only).

Usage:  py -3.13 tests/e2e-api/scenario.py [base_url]
Covers: customer lookup + eligibility + submit (Idempotency-Key replay) -> label -> warehouse scan (double scan)
-> inspect (grade C) -> complete -> auto refund; finance race (two reviewers, one wins); RBAC; reconciliation.
Exit code 0 = all checks passed.
"""

from __future__ import annotations

import json
import os
import pathlib
import sys
import threading
import urllib.error
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000").rstrip("/") + "/api/v1"
failures: list[str] = []


def _secret(name: str) -> str:
    """Secret from the environment, else from .env at the repo root (never hard-coded; see .env.example)."""
    value = os.environ.get(name)
    if value:
        return value
    for env_file in (pathlib.Path(__file__).resolve().parents[2] / ".env", pathlib.Path.cwd() / ".env"):
        if env_file.is_file():
            for line in env_file.read_text(encoding="utf-8").splitlines():
                if line.startswith(name + "="):
                    return line.split("=", 1)[1].strip()
    raise SystemExit(f"{name} is not set (export it or create .env from .env.example)")


PASSWORD = _secret("RR_SEED_STAFF_PASSWORD")


def call(method, path, token=None, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None), dict(r.headers)
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, (json.loads(raw) if raw else None), dict(e.headers)


def check(cond, label, detail=""):
    print(("  PASS " if cond else "  FAIL ") + label + ("" if cond else f"  -> {detail}"))
    if not cond:
        failures.append(label)


def staff(username):
    s, b, _ = call("POST", "/auth/staff/login", body={"username": username, "password": PASSWORD})
    assert s == 200, (s, b)
    return b["access_token"]


print("== Customer journey (RR-EDGE-001, delivered exactly 30 days ago)")
s, b, _ = call("POST", "/auth/customer/lookup", body={"order_number": "RR-EDGE-001", "email": "EDGE.customer@example.com"})
check(s == 200 and b["principal"]["role"] == "CUSTOMER", "customer lookup (case-insensitive email)", (s, b))
cust = b["access_token"]
s, b, _ = call("POST", "/auth/customer/lookup", body={"order_number": "RR-EDGE-001", "email": "wrong@example.com"})
check(s == 404 and b["error"]["code"] == "NOT_FOUND", "lookup with wrong email is a generic 404", (s, b))

s, b, _ = call("GET", "/customer/orders/RR-EDGE-008", cust)
codes = sorted(u["eligibility"]["reason_code"] for u in b["units"])
check(codes == ["CATEGORY_NOT_RETURNABLE", "ELIGIBLE"], "mixed basket: jeans eligible, innerwear not", codes)

s, b, _ = call("GET", "/customer/orders/RR-EDGE-001", cust)
unit = b["units"][0]
check(unit["eligibility"]["eligible"] and unit["eligibility"]["details"]["days_since_delivery"] == 30,
      "day 30 is eligible (inclusive)", unit["eligibility"])
key = str(uuid.uuid4())
body = {"order_number": "RR-EDGE-001", "refund_method": "ORIGINAL_PAYMENT",
        "items": [{"order_item_id": unit["order_item_id"], "unit_index": 1, "reason": "SIZE_FIT"}]}
s, ret, h = call("POST", "/customer/returns", cust, body, {"Idempotency-Key": key})
check(s == 201 and ret["status"] == "APPROVED", "submit -> APPROVED with label", (s, ret))
s2, ret2, h2 = call("POST", "/customer/returns", cust, body, {"Idempotency-Key": key})
check(s2 == 201 and ret2["id"] == ret["id"] and h2.get("Idempotent-Replayed") == "true",
      "double-submit with same key replays the same return", (s2, h2.get("Idempotent-Replayed")))
s3, b3, _ = call("POST", "/customer/returns", cust, body, {"Idempotency-Key": str(uuid.uuid4())})
check(s3 == 422 and b3["error"]["code"] == "ITEM_NOT_ELIGIBLE", "same unit again (new key) is refused", (s3, b3))
s, label, _ = call("GET", f"/customer/returns/{ret['id']}/label", cust)
check(s == 200 and label["qr_svg"].startswith("data:image/svg+xml;base64,"), "label with QR", s)

s, b, _ = call("POST", "/customer/orders/RR-EDGE-002", cust)
s, b, _ = call("GET", "/customer/orders/RR-EDGE-002", cust)
check(b["units"][0]["eligibility"]["reason_code"] == "RETURN_WINDOW_EXPIRED", "day 31 is rejected",
      b["units"][0]["eligibility"])

print("== Warehouse: scan, double scan, inspect grade C, complete -> auto refund")
wh = staff("warehouse1")
s, b, _ = call("POST", "/warehouse/scan", wh, {"qr_token": label["qr_token"]})
check(s == 200 and not b["already_received"] and b["return"]["status"] == "RECEIVED", "scan -> RECEIVED", (s, b))
s, b, _ = call("POST", "/warehouse/scan", wh, {"qr_token": label["qr_token"]})
check(s == 200 and b["already_received"], "second scan is a harmless no-op (F-06)", (s, b))
r = b["return"]
item = r["items"][0]
s, r, _ = call("PUT", f"/warehouse/returns/{r['id']}/items/{item['id']}/inspection", wh,
               {"decision": "ACCEPT", "grade": "C"}, {"If-Match": f'"{r["version"]}"'})
expected_c = (item["unit_price_paise"] + 1) // 2
check(s == 200 and r["items"][0]["refund_paise"] == expected_c, "grade C = 50% half-up", (s, r and r["items"][0]))
s, stale, _ = call("POST", f"/warehouse/returns/{r['id']}/complete-inspection", wh, None,
                   {"If-Match": '"1"', "Idempotency-Key": str(uuid.uuid4())})
check(s == 409 and stale["error"]["code"] == "STALE_VERSION", "stale If-Match -> 409 STALE_VERSION", (s, stale))
s, r, _ = call("POST", f"/warehouse/returns/{r['id']}/complete-inspection", wh, None,
               {"If-Match": f'"{r["version"]}"', "Idempotency-Key": str(uuid.uuid4())})
check(s == 200 and r["status"] == "REFUNDED", "auto refund under Rs 10k -> REFUNDED", (s, r and r["status"]))
s, b, _ = call("GET", f"/customer/returns/{r['id']}", cust)
check([t["step"] for t in b["timeline"] if t["state"] == "done"][-1] == "REFUNDED" and b["refund"]["attempts"] == [],
      "customer timeline ends Refunded; attempts redacted for customers", b["timeline"])

print("== RBAC")
s, b, _ = call("POST", "/finance/returns/" + r["id"] + "/approve", wh, {}, {"If-Match": '"1"', "Idempotency-Key": str(uuid.uuid4())})
check(s == 403 and b["error"]["code"] == "FORBIDDEN_ROLE", "warehouse cannot approve refunds", (s, b))
s, b, _ = call("GET", "/warehouse/returns", cust)
check(s == 403, "customer cannot see warehouse queue", s)
s, b, _ = call("GET", "/analytics/overview", staff("finance1"))
check(s == 403, "finance cannot open manager analytics", s)

print("== Finance race: two reviewers approve RR-DEMO-101 at once (F-04)")
f1, f2 = staff("finance1"), staff("finance2")
s, q, _ = call("GET", "/finance/queue?status=PENDING_FINANCE", f1)
demo = next(x for x in q["items"] if x["order_number"] == "RR-DEMO-101")
check(demo["requires_finance_review"] and demo["finance_review_reason"] in ("AMOUNT", "BOTH"), "> Rs 10k routed to finance",
      demo["finance_review_reason"])
results = []


def approve(tok):
    results.append(call("POST", f"/finance/returns/{demo['id']}/approve", tok, {"note": "ok"},
                        {"If-Match": f'"{demo["version"]}"', "Idempotency-Key": str(uuid.uuid4())}))


threads = [threading.Thread(target=approve, args=(t,)) for t in (f1, f2)]
[t.start() for t in threads]
[t.join() for t in threads]
statuses = sorted(x[0] for x in results)
check(statuses[0] in (200, 202) and statuses[1] == 409, "exactly one approval wins, the other gets 409", statuses)
winner = next(x for x in results if x[0] in (200, 202))[1]
check(winner["status"] in ("REFUNDED", "REFUND_PENDING") and len(winner["refund"]["attempts"]) == 1,
      "one refund attempt only", winner["refund"])

print("== Grade-C threshold case RR-DEMO-104 (24,999.00 -> 12,499.50 after grade C, still > 10k)")
demo4 = next(x for x in q["items"] if x["order_number"] == "RR-DEMO-104")
check(demo4["approved_refund_paise"] == 1_249_950 and demo4["items"][0]["grade"] == "C", "threshold applied after grade",
      demo4["approved_refund_paise"])

print("== Manager")
mgr = staff("manager1")
s, ov, _ = call("GET", "/analytics/overview?days=90", mgr)
check(s == 200 and ov["kpis"]["returns_count"] > 100 and len(ov["return_rate_by_category"]) == 6, "dashboard overview",
      s)
s, rec, _ = call("GET", "/ops/reconciliation", mgr)
check(s == 200 and rec["ok"], "reconciliation R1-R5 all zero", rec)

print()
print("ALL CHECKS PASSED" if not failures else f"{len(failures)} FAILED: {failures}")
sys.exit(1 if failures else 0)

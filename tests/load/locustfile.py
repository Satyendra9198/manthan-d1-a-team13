"""Locust "approve storm" (build plan S3/S10, ADR-001): finance reviewers race to approve the same returns,
double-clicking with a reused Idempotency-Key. The money invariant is checked when the run ends:

  * no refund was paid twice at the gateway (SUCCEEDED gateway records grouped by refund id <= 1)
  * reconciliation R1-R5 is clean (GET /api/v1/ops/reconciliation -> ok)

The process exits non-zero if either check fails, so it can gate CI.

Run (Python 3.13 venv):   pip install locust
    locust -f tests/load/locustfile.py --headless -u 10 -r 10 -t 30s --host http://localhost:8000
Or with Docker:
    docker run --rm -v "$PWD/tests/load:/mnt" locustio/locust:2.32.0 -f /mnt/locustfile.py --headless \
        -u 10 -r 10 -t 30s --host http://host.docker.internal:8000 --gateway-url http://host.docker.internal:8100
Re-seed first so the finance queue has work: docker compose run --rm app --spring.profiles.active=seed --seed.reset=true
"""

from __future__ import annotations

import collections
import json
import random
import urllib.request
import uuid

from locust import HttpUser, between, events, task

PASSWORD = "ReturnRight#2026"
API = "/api/v1"


@events.init_command_line_parser.add_listener
def _args(parser):
    parser.add_argument("--gateway-url", default="http://localhost:8100", help="mock gateway base URL")
    parser.add_argument("--gateway-admin-token", default="dev-admin-token")


class FinanceReviewer(HttpUser):
    wait_time = between(0.05, 0.3)

    def on_start(self):
        user = random.choice(["finance1", "finance2"])
        r = self.client.post(f"{API}/auth/staff/login", json={"username": user, "password": PASSWORD},
                             name="staff login")
        self.auth = {"Authorization": "Bearer " + r.json()["access_token"]}

    @task(4)
    def approve_with_double_click(self):
        queue = self.client.get(f"{API}/finance/queue?status=PENDING_FINANCE", headers=self.auth,
                                name="finance queue").json()
        items = queue.get("items", [])
        if not items:
            return
        ret = random.choice(items)
        headers = {**self.auth, "If-Match": f'"{ret["version"]}"', "Idempotency-Key": str(uuid.uuid4())}
        for _ in range(2):  # the double click: same key twice
            with self.client.post(f"{API}/finance/returns/{ret['id']}/approve", json={"note": "load test"},
                                  headers=headers, name="approve", catch_response=True) as res:
                # 200 = won (or replayed); 409 = lost the race / stale version / key in progress. All are correct.
                if res.status_code in (200, 409):
                    res.success()
                else:
                    res.failure(f"unexpected {res.status_code}: {res.text[:200]}")

    @task(1)
    def browse_queues(self):
        self.client.get(f"{API}/finance/queue?status=REFUND_FAILED", headers=self.auth, name="finance queue")


def _get(url, headers):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as res:
        return json.loads(res.read())


@events.test_stop.add_listener
def _check_money_invariants(environment, **_):
    opts = environment.parsed_options
    host = environment.host.rstrip("/")
    problems = []

    # 1. Never paid twice: gateway keys are rr-{refundId}-{attemptNo}.
    gw = _get(f"{opts.gateway_url.rstrip('/')}/admin/refunds?limit=1000",
              {"X-Admin-Token": opts.gateway_admin_token})
    paid = collections.Counter(
        r["idempotency_key"].rsplit("-", 1)[0] for r in gw["items"]
        if r["status"] == "SUCCEEDED" and r["idempotency_key"].startswith("rr-"))
    doubles = {k: n for k, n in paid.items() if n > 1}
    if doubles:
        problems.append(f"refunds paid more than once at the gateway: {doubles}")

    # 2. Reconciliation R1-R5.
    login = urllib.request.Request(f"{host}{API}/auth/staff/login", method="POST",
                                   data=json.dumps({"username": "manager1", "password": PASSWORD}).encode(),
                                   headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(login, timeout=30) as res:
        token = json.loads(res.read())["access_token"]
    rec = _get(f"{host}{API}/ops/reconciliation", {"Authorization": "Bearer " + token})
    if not rec.get("ok"):
        problems.append(f"reconciliation not clean: {rec}")

    print(f"\n[money check] gateway refunds paid: {sum(paid.values())} across {len(paid)} refunds; "
          f"reconciliation ok={rec.get('ok')}")
    if problems:
        for p in problems:
            print("[money check] FAIL", p)
        environment.process_exit_code = 1
    else:
        print("[money check] PASS: no double payment, reconciliation clean")

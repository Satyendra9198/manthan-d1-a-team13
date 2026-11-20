# ReturnRight – Requirement Assumptions

Each row resolves an ambiguity in the use case. IDs (`A-xx`) are referenced from code and tests.
To override one, change the row here, then change the tests that cite it.

**Stack decision:** the brief lists FastAPI/Python; the team chose **Spring Boot 3 + Java 21 + PostgreSQL**
(JUnit 5 replaces pytest, Flyway replaces Alembic). Everything else in the brief still applies.

| ID | Question | Options | Assumption | Why |
|----|----------|---------|------------|-----|
| A-01 | Is the window counted from the order date or the delivery date? | Order date / delivery date | **Delivery date.** An undelivered item is not yet returnable. | The customer can't return what they haven't received; this is the usual retail practice. |
| A-02 | Day 30 vs day 31: timezone and inclusiveness | UTC / store timezone; inclusive / exclusive | **Calendar days in `Asia/Kolkata`. The delivery day is day 0. Day 30 is allowed and day 31 is rejected** (electronics: day 10 allowed, day 11 rejected). | Days are counted in the customer's timezone, so a delivery at 23:59 IST doesn't lose a day to UTC. |
| A-03 | Is state kept per return or per item? | Return / item / both | **Status lives on the return. Inspection outcome, grade and accepted quantity are stored per item.** | One label and one parcel per return keeps the state machine small; item-level inspection data still allows partial acceptance. |
| A-04 | Mixed baskets (some items eligible, some not) | Reject the whole return / drop the ineligible items | **Each item is checked on its own.** The return is created with only the eligible items, and the ineligible ones are shown with their reason. A return with no eligible items is not created. | Customers get the most they're entitled to, with a clear explanation for the rest. |
| A-05 | What does "partial accept" mean? | Lower refund % / fewer units accepted | **Fewer units are accepted than were sent** (`accepted_quantity < returned_quantity`). Units that aren't accepted get no refund. | Keeps the grade rule and the acceptance rule separate. |
| A-06 | How does partial accept combine with grade C? | — | **Refund per line = unit price × accepted quantity × grade %** (A=100, B=100, C=50). A rejected line has accepted quantity 0. | One formula covers all the cases. |
| A-07 | Rounding of the 50% refund | Round / floor / ceil | **Money is stored as integer paise; the line refund is rounded down (floor) to the paisa.** | No floating point in money calculations; at most half a paisa of difference. |
| A-08 | Is the 10,000 INR finance threshold per item or per return, and before or after the grade adjustment? | — | **Per return, after the grade adjustment. Strictly above 10,000.00 INR** (1,000,000 paise) needs finance approval; exactly 10,000.00 does not. | Finance cares about the money actually paid out. |
| A-09 | Does store credit go through the gateway? | Gateway / ledger only | **Ledger only.** Store credit succeeds immediately with no gateway call. | No real money moves, so there's nothing to call the gateway for. |
| A-10 | Flag rule: count requests or completed returns? Over what window? | — | **Count return requests (excluding cancelled ones) created in the rolling 30 days up to now, including the new request.** More than 3 means the 4th request in 30 days flags the customer. | Requests show intent; waiting for completion would detect abuse too late. |
| A-11 | Does a flag block the return? | Block / mark for review | **It only marks for review.** A flagged customer's request stays in `REQUESTED` until an Operations Manager approves or rejects it; unflagged requests are auto-approved by the system. | Matches "flagged for review" in the brief without turning away good customers. |
| A-12 | Can a customer cancel? | — | **Yes, in `REQUESTED` or `APPROVED`** (before the parcel ships). | Once shipped, the warehouse owns the parcel. |
| A-13 | Does the label expire? | — | **Yes, 14 days after approval.** A scheduled job moves `APPROVED` to `CANCELLED` with reason `LABEL_EXPIRED`. (Not in the first slice.) | Stops stale labels piling up. |
| A-14 | What happens when an item is rejected after inspection? | — | **If every line is rejected, the return goes to `REJECTED` with no refund. Otherwise it continues with the accepted lines only.** Returning the rejected item to the customer is out of scope (shown as a note). | Physical logistics are out of scope for a demo. |
| A-15 | Refund idempotency | Client key / DB unique / both | **Both:** a client `Idempotency-Key` header plus a unique refund per return, and the stored key is passed to the gateway. See ADR-001. | Covers double clicks, retries and lost responses. |
| A-16 | Status after a gateway timeout | FAILED / UNKNOWN | **`UNKNOWN`, never `FAILED`.** A retry or the reconciliation job re-sends the same key. | The payment may have gone through; marking it FAILED would invite a second payment. |
| A-17 | Who receives the parcel when the carrier scan is missed? | — | **The warehouse can receive from `APPROVED` or `IN_TRANSIT`.** | The mock carrier may never send an "in transit" scan. |
| A-18 | Product categories (6) | — | `ELECTRONICS`, `APPAREL`, `HOME_KITCHEN`, `BEAUTY`, `INNERWEAR`, `PERISHABLES` | `INNERWEAR` and `PERISHABLES` are non-returnable. |

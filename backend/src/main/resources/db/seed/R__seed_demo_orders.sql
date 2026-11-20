-- Demo data for the dev profile only (see application-dev.yml). Fictional customers and emails.
-- Repeatable migration: Flyway re-runs it whenever this file changes, so it starts by clearing the
-- demo orders and every return, refund and photo row that points at them (including returns
-- created through the API while testing). Photo files on disk are not removed.
-- Dates are relative to now() so every scenario stays valid on any demo day.
-- Scenario guide: docs/api/README.md.

-- Staff accounts. Every password is Password@123 (BCrypt). Upserted, never deleted, because the
-- status history and inspections refer to them.
INSERT INTO staff_user (id, email, full_name, role, password_hash, active) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'riya.warehouse@returnright.example', 'Riya Kapoor',
     'WAREHOUSE_AGENT', '$2a$10$5CS3mdzCVN41Kw/KS093t.V1.KcKTww/MInqMFKRYBX9y63ubmn6K', true),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'arun.warehouse@returnright.example', 'Arun Pillai',
     'WAREHOUSE_AGENT', '$2a$10$jsdvJ33fsU/nA.utre98WuZIEs7MnY4K2mIO/7iPZIfKpVkkEJjLW', true),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'kavya.finance@returnright.example', 'Kavya Reddy',
     'FINANCE_REVIEWER', '$2a$10$vk8S94RNa7Zk0Hcsjd76A.Lck1WDsV5QONrn8llEDbsYQZLfidE8S', true),
    ('aaaaaaaa-0000-0000-0000-000000000004', 'manoj.ops@returnright.example', 'Manoj Desai',
     'OPERATIONS_MANAGER', '$2a$10$2O98JnjH/0JDp0f/OcgimuMB6ySC6v657jHLxhPoEFmsek52/Ylai', true)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email, full_name = EXCLUDED.full_name, role = EXCLUDED.role,
    password_hash = EXCLUDED.password_hash, active = EXCLUDED.active;

CREATE TEMP TABLE demo_order_ids (id UUID PRIMARY KEY) ON COMMIT DROP;
INSERT INTO demo_order_ids VALUES
    ('11111111-1111-1111-1111-111111111111'),
    ('22222222-2222-2222-2222-222222222222'),
    ('33333333-3333-3333-3333-333333333333'),
    ('44444444-4444-4444-4444-444444444444'),
    ('55555555-5555-5555-5555-555555555555'),
    ('66666666-6666-6666-6666-666666666666'),
    ('77777777-7777-7777-7777-777777777777'),
    ('88888888-8888-8888-8888-888888888888'),
    ('99999999-9999-9999-9999-999999999999'),
    ('abababab-abab-abab-abab-abababababab'),
    ('cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd'),
    ('efefefef-efef-efef-efef-efefefefefef');

DELETE FROM return_photo WHERE return_item_id IN (
    SELECT ri.id FROM return_item ri
    JOIN return_request r ON r.id = ri.return_id
    WHERE r.order_id IN (SELECT id FROM demo_order_ids));
DELETE FROM return_item WHERE return_id IN (
    SELECT id FROM return_request WHERE order_id IN (SELECT id FROM demo_order_ids));
DELETE FROM refund WHERE return_id IN (
    SELECT id FROM return_request WHERE order_id IN (SELECT id FROM demo_order_ids));
DELETE FROM return_status_change WHERE return_id IN (
    SELECT id FROM return_request WHERE order_id IN (SELECT id FROM demo_order_ids));
DELETE FROM return_request WHERE order_id IN (SELECT id FROM demo_order_ids);
DELETE FROM order_item WHERE order_id IN (SELECT id FROM demo_order_ids);
DELETE FROM customer_order WHERE id IN (SELECT id FROM demo_order_ids);

INSERT INTO customer_order (id, order_number, customer_email, customer_name, placed_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 'RR-10482', 'aditi.sharma@example.com',
     'Aditi Sharma', now() - INTERVAL '20 days'),
    ('22222222-2222-2222-2222-222222222222', 'RR-10577', 'rahul.verma@example.com',
     'Rahul Verma', now() - INTERVAL '2 days'),
    ('33333333-3333-3333-3333-333333333333', 'RR-10601', 'priya.nair@example.com',
     'Priya Nair', now() - INTERVAL '12 days'),
    ('44444444-4444-4444-4444-444444444444', 'RR-10655', 'vikram.singh@example.com',
     'Vikram Singh', now() - INTERVAL '10 days'),
    ('55555555-5555-5555-5555-555555555555', 'RR-10702', 'meera.iyer@example.com',
     'Meera Iyer', now() - INTERVAL '25 days'),
    ('66666666-6666-6666-6666-666666666666', 'RR-10388', 'arjun.mehta@example.com',
     'Arjun Mehta', now() - INTERVAL '50 days'),
    ('77777777-7777-7777-7777-777777777777', 'RR-10750', 'karan.kapoor@example.com',
     'Karan Kapoor', now() - INTERVAL '14 days'),
    ('88888888-8888-8888-8888-888888888888', 'RR-10760', 'neha.gupta@example.com',
     'Neha Gupta', now() - INTERVAL '18 days'),
    ('99999999-9999-9999-9999-999999999999', 'RR-10770', 'sanjay.rao@example.com',
     'Sanjay Rao', now() - INTERVAL '16 days'),
    ('abababab-abab-abab-abab-abababababab', 'RR-10780', 'divya.menon@example.com',
     'Divya Menon', now() - INTERVAL '9 days'),
    ('cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd', 'RR-10703', 'meera.iyer@example.com',
     'Meera Iyer', now() - INTERVAL '8 days'),
    ('efefefef-efef-efef-efef-efefefefefef', 'RR-10790', 'farah.khan@example.com',
     'Farah Khan', now() - INTERVAL '7 days');

INSERT INTO order_item (id, order_id, line_number, sku, product_name, variant, category,
                        unit_price_paise, quantity, delivered_at) VALUES
    -- RR-10482: mixed basket matching the Figma "Which items are you returning?" screen.
    ('11111111-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 1,
     'APP-LINEN-M-SAND', 'Linen shirt', 'M / Sand', 'APPAREL', 189900, 1,
     now() - INTERVAL '5 days'),
    ('11111111-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 2,
     'HK-MUG-SET-4', 'Ceramic mug set', 'Set of 4', 'HOME_KITCHEN', 124900, 1,
     now() - INTERVAL '5 days'),
    ('11111111-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 3,
     'ELE-EARBUDS-PRO', 'Wireless earbuds Pro', 'Black', 'ELECTRONICS', 499900, 1,
     now() - INTERVAL '15 days'),
    ('11111111-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 4,
     'INW-BRIEFS-3PK', 'Cotton briefs (3-pack)', 'M', 'INNERWEAR', 59900, 1,
     now() - INTERVAL '5 days'),

    -- RR-10577: not delivered yet, so nothing is returnable.
    ('22222222-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 1,
     'BEA-SERUM-30', 'Vitamin C serum', '30 ml', 'BEAUTY', 79900, 2, NULL),

    -- RR-10601: happy path with quantities above 1 and electronics still inside 10 days.
    ('33333333-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 1,
     'APP-TEE-WHT-L', 'Cotton T-shirt', 'L / White', 'APPAREL', 59900, 3,
     now() - INTERVAL '3 days'),
    ('33333333-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 2,
     'BEA-SPF50-50', 'Sunscreen SPF 50', '50 ml', 'BEAUTY', 64900, 2,
     now() - INTERVAL '3 days'),
    ('33333333-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333', 3,
     'ELE-WATCH-S2', 'Smartwatch S2', '42 mm / Graphite', 'ELECTRONICS', 1249900, 1,
     now() - INTERVAL '8 days'),
    ('33333333-0000-0000-0000-000000000004', '33333333-3333-3333-3333-333333333333', 4,
     'PER-MANGO-3KG', 'Alphonso mangoes', '3 kg box', 'PERISHABLES', 149900, 1,
     now() - INTERVAL '3 days'),

    -- RR-10655: earlier returns exist (see return_request below).
    ('44444444-0000-0000-0000-000000000001', '44444444-4444-4444-4444-444444444444', 1,
     'APP-RUN-SHOE-9', 'Running shoes', 'UK 9 / Blue', 'APPAREL', 349900, 2,
     now() - INTERVAL '6 days'),
    ('44444444-0000-0000-0000-000000000002', '44444444-4444-4444-4444-444444444444', 2,
     'HK-DESK-LAMP', 'LED desk lamp', 'Warm white', 'HOME_KITCHEN', 219900, 1,
     now() - INTERVAL '6 days'),
    ('44444444-0000-0000-0000-000000000003', '44444444-4444-4444-4444-444444444444', 3,
     'ELE-SPEAKER-MINI', 'Bluetooth speaker', 'Mini / Red', 'ELECTRONICS', 299900, 1,
     now() - INTERVAL '4 days'),

    -- RR-10702: three items already on recent returns; returning the fourth flags the customer.
    ('55555555-0000-0000-0000-000000000001', '55555555-5555-5555-5555-555555555555', 1,
     'APP-KURTA-M', 'Cotton kurta', 'M / Indigo', 'APPAREL', 149900, 1,
     now() - INTERVAL '22 days'),
    ('55555555-0000-0000-0000-000000000002', '55555555-5555-5555-5555-555555555555', 2,
     'APP-DUPATTA', 'Silk dupatta', 'Maroon', 'APPAREL', 189900, 1,
     now() - INTERVAL '22 days'),
    ('55555555-0000-0000-0000-000000000003', '55555555-5555-5555-5555-555555555555', 3,
     'HK-BEDSHEET-Q', 'Cotton bedsheet', 'Queen / Grey', 'HOME_KITCHEN', 179900, 1,
     now() - INTERVAL '22 days'),
    ('55555555-0000-0000-0000-000000000004', '55555555-5555-5555-5555-555555555555', 4,
     'APP-LINEN-TROUSER', 'Linen trousers', '32 / Olive', 'APPAREL', 229900, 1,
     now() - INTERVAL '22 days'),

    -- RR-10388: every window has closed.
    ('66666666-0000-0000-0000-000000000001', '66666666-6666-6666-6666-666666666666', 1,
     'APP-DENIM-JKT', 'Denim jacket', 'L / Blue', 'APPAREL', 399900, 1,
     now() - INTERVAL '45 days'),
    ('66666666-0000-0000-0000-000000000002', '66666666-6666-6666-6666-666666666666', 2,
     'ELE-POWERBANK', 'Power bank 20000 mAh', 'Black', 'ELECTRONICS', 249900, 1,
     now() - INTERVAL '45 days'),

    -- RR-10750: parcel received at the warehouse, waiting for inspection.
    ('77777777-0000-0000-0000-000000000001', '77777777-7777-7777-7777-777777777777', 1,
     'APP-WOOL-SWTR-M', 'Wool sweater', 'M / Charcoal', 'APPAREL', 249900, 1,
     now() - INTERVAL '10 days'),
    ('77777777-0000-0000-0000-000000000002', '77777777-7777-7777-7777-777777777777', 2,
     'APP-WALLET-BRN', 'Leather wallet', 'Brown', 'APPAREL', 129900, 2,
     now() - INTERVAL '10 days'),

    -- RR-10760: refunded at 50% after a grade C inspection (Figma "949.50 refunded").
    ('88888888-0000-0000-0000-000000000001', '88888888-8888-8888-8888-888888888888', 1,
     'APP-LINEN-L-WHT', 'Linen shirt', 'L / White', 'APPAREL', 189900, 1,
     now() - INTERVAL '15 days'),

    -- RR-10770: rejected after inspection as used or worn.
    ('99999999-0000-0000-0000-000000000001', '99999999-9999-9999-9999-999999999999', 1,
     'APP-RUN-SHORTS', 'Running shorts', 'M / Black', 'APPAREL', 129900, 1,
     now() - INTERVAL '13 days'),

    -- RR-10780: laptop refund above 10,000 rupees, waiting for finance.
    ('abababab-0000-0000-0000-000000000001', 'abababab-abab-abab-abab-abababababab', 1,
     'ELE-LAPTOP-14', 'Laptop 14 inch', '16 GB / 512 GB', 'ELECTRONICS', 5499900, 1,
     now() - INTERVAL '7 days'),

    -- RR-10703: Meera's latest return, held for operations review (flagged customer).
    ('cdcdcdcd-0000-0000-0000-000000000001', 'cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd', 1,
     'APP-SAREE-COT', 'Cotton saree', 'Mustard', 'APPAREL', 349900, 1,
     now() - INTERVAL '5 days'),

    -- RR-10790: parcel on its way to the warehouse.
    ('efefefef-0000-0000-0000-000000000001', 'efefefef-efef-efef-efef-efefefefefef', 1,
     'HK-VASE-CER', 'Ceramic vase', 'Sage green', 'HOME_KITCHEN', 219900, 1,
     now() - INTERVAL '5 days');

INSERT INTO return_request (id, rma_number, order_id, customer_email, status, refund_method,
                            flagged_for_review, created_at, approved_at, version) VALUES
    -- RR-10655: 1 of 2 running shoes on an approved return; the lamp fully returned; the
    -- speaker's return was cancelled, so it is returnable again.
    ('44444444-aaaa-0000-0000-000000000001', 'RMA-DEMO-00001',
     '44444444-4444-4444-4444-444444444444', 'vikram.singh@example.com', 'APPROVED',
     'ORIGINAL_PAYMENT', false, now() - INTERVAL '2 days', now() - INTERVAL '2 days', 0),
    ('44444444-aaaa-0000-0000-000000000002', 'RMA-DEMO-00002',
     '44444444-4444-4444-4444-444444444444', 'vikram.singh@example.com', 'CANCELLED',
     'STORE_CREDIT', false, now() - INTERVAL '1 day', now() - INTERVAL '1 day', 0),

    -- RR-10702: three requests in the last 30 days.
    ('55555555-aaaa-0000-0000-000000000001', 'RMA-DEMO-00003',
     '55555555-5555-5555-5555-555555555555', 'meera.iyer@example.com', 'APPROVED',
     'ORIGINAL_PAYMENT', false, now() - INTERVAL '20 days', now() - INTERVAL '20 days', 0),
    ('55555555-aaaa-0000-0000-000000000002', 'RMA-DEMO-00004',
     '55555555-5555-5555-5555-555555555555', 'meera.iyer@example.com', 'IN_TRANSIT',
     'STORE_CREDIT', false, now() - INTERVAL '9 days', now() - INTERVAL '9 days', 0),
    ('55555555-aaaa-0000-0000-000000000003', 'RMA-DEMO-00005',
     '55555555-5555-5555-5555-555555555555', 'meera.iyer@example.com', 'APPROVED',
     'ORIGINAL_PAYMENT', false, now() - INTERVAL '2 days', now() - INTERVAL '2 days', 0);

INSERT INTO return_request (id, rma_number, order_id, customer_email, status, refund_method,
                            flagged_for_review, created_at, approved_at, version, inspected_at,
                            inspected_by, refund_amount_paise, rejection_reason,
                            rejection_note) VALUES
    ('77777777-aaaa-0000-0000-000000000001', 'RMA-DEMO-00006',
     '77777777-7777-7777-7777-777777777777', 'karan.kapoor@example.com', 'RECEIVED',
     'ORIGINAL_PAYMENT', false, now() - INTERVAL '6 days', now() - INTERVAL '6 days', 0,
     NULL, NULL, NULL, NULL, NULL),
    ('88888888-aaaa-0000-0000-000000000001', 'RMA-DEMO-00007',
     '88888888-8888-8888-8888-888888888888', 'neha.gupta@example.com', 'REFUNDED',
     'ORIGINAL_PAYMENT', false, now() - INTERVAL '10 days', now() - INTERVAL '10 days', 0,
     now() - INTERVAL '3 days', 'aaaaaaaa-0000-0000-0000-000000000001', 94950, NULL, NULL),
    ('99999999-aaaa-0000-0000-000000000001', 'RMA-DEMO-00008',
     '99999999-9999-9999-9999-999999999999', 'sanjay.rao@example.com', 'REJECTED',
     'STORE_CREDIT', false, now() - INTERVAL '9 days', now() - INTERVAL '9 days', 0,
     now() - INTERVAL '2 days', 'aaaaaaaa-0000-0000-0000-000000000002', 0, 'USED_OR_WORN',
     'Sweat marks and pilling on the waistband.'),
    ('abababab-aaaa-0000-0000-000000000001', 'RMA-DEMO-00009',
     'abababab-abab-abab-abab-abababababab', 'divya.menon@example.com',
     'AWAITING_FINANCE_APPROVAL', 'ORIGINAL_PAYMENT', false, now() - INTERVAL '5 days',
     now() - INTERVAL '5 days', 0, now() - INTERVAL '1 day',
     'aaaaaaaa-0000-0000-0000-000000000001', 5499900, NULL, NULL),
    ('cdcdcdcd-aaaa-0000-0000-000000000001', 'RMA-DEMO-00010',
     'cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd', 'meera.iyer@example.com', 'REQUESTED',
     'STORE_CREDIT', true, now() - INTERVAL '1 day', NULL, 0, NULL, NULL, NULL, NULL, NULL),
    ('efefefef-aaaa-0000-0000-000000000001', 'RMA-DEMO-00011',
     'efefefef-efef-efef-efef-efefefefefef', 'farah.khan@example.com', 'IN_TRANSIT',
     'STORE_CREDIT', false, now() - INTERVAL '3 days', now() - INTERVAL '3 days', 0,
     NULL, NULL, NULL, NULL, NULL);

INSERT INTO return_item (id, return_id, order_item_id, quantity, reason, customer_comment) VALUES
    ('44444444-bbbb-0000-0000-000000000001', '44444444-aaaa-0000-0000-000000000001',
     '44444444-0000-0000-0000-000000000001', 1, 'TOO_SMALL', 'Need a size 10.'),
    ('44444444-bbbb-0000-0000-000000000002', '44444444-aaaa-0000-0000-000000000001',
     '44444444-0000-0000-0000-000000000002', 1, 'CHANGED_MY_MIND', NULL),
    ('44444444-bbbb-0000-0000-000000000003', '44444444-aaaa-0000-0000-000000000002',
     '44444444-0000-0000-0000-000000000003', 1, 'CHANGED_MY_MIND', NULL),
    ('55555555-bbbb-0000-0000-000000000001', '55555555-aaaa-0000-0000-000000000001',
     '55555555-0000-0000-0000-000000000001', 1, 'TOO_BIG', NULL),
    ('55555555-bbbb-0000-0000-000000000002', '55555555-aaaa-0000-0000-000000000002',
     '55555555-0000-0000-0000-000000000002', 1, 'CHANGED_MY_MIND', NULL),
    ('55555555-bbbb-0000-0000-000000000003', '55555555-aaaa-0000-0000-000000000003',
     '55555555-0000-0000-0000-000000000003', 1, 'TOO_SMALL', NULL);

INSERT INTO return_item (id, return_id, order_item_id, quantity, reason, customer_comment, grade,
                         accepted_quantity, rejection_reason, inspection_note) VALUES
    ('77777777-bbbb-0000-0000-000000000001', '77777777-aaaa-0000-0000-000000000001',
     '77777777-0000-0000-0000-000000000001', 1, 'TOO_BIG', 'Fits like an XL.',
     NULL, NULL, NULL, NULL),
    ('77777777-bbbb-0000-0000-000000000002', '77777777-aaaa-0000-0000-000000000001',
     '77777777-0000-0000-0000-000000000002', 2, 'CHANGED_MY_MIND', NULL,
     NULL, NULL, NULL, NULL),
    ('88888888-bbbb-0000-0000-000000000001', '88888888-aaaa-0000-0000-000000000001',
     '88888888-0000-0000-0000-000000000001', 1, 'TOO_SMALL', NULL,
     'C', 1, NULL, 'Light stain on the cuff.'),
    ('99999999-bbbb-0000-0000-000000000001', '99999999-aaaa-0000-0000-000000000001',
     '99999999-0000-0000-0000-000000000001', 1, 'CHANGED_MY_MIND', NULL,
     NULL, 0, 'USED_OR_WORN', NULL),
    ('abababab-bbbb-0000-0000-000000000001', 'abababab-aaaa-0000-0000-000000000001',
     'abababab-0000-0000-0000-000000000001', 1, 'CHANGED_MY_MIND', 'Sealed box, never used.',
     'A', 1, NULL, NULL),
    ('cdcdcdcd-bbbb-0000-0000-000000000001', 'cdcdcdcd-aaaa-0000-0000-000000000001',
     'cdcdcdcd-0000-0000-0000-000000000001', 1, 'CHANGED_MY_MIND', NULL,
     NULL, NULL, NULL, NULL),
    ('efefefef-bbbb-0000-0000-000000000001', 'efefefef-aaaa-0000-0000-000000000001',
     'efefefef-0000-0000-0000-000000000001', 1, 'DAMAGED_ON_ARRIVAL', 'Chipped at the rim.',
     NULL, NULL, NULL, NULL);

INSERT INTO refund (id, return_id, amount_paise, method, idempotency_key, status,
                    gateway_transaction_id, failure_reason) VALUES
    ('88888888-cccc-0000-0000-000000000001', '88888888-aaaa-0000-0000-000000000001', 94950,
     'ORIGINAL_PAYMENT', 'return-88888888-aaaa-0000-0000-000000000001', 'SUCCEEDED',
     'MOCK-TXN-DEMO-1', NULL);

-- Status history behind each customer timeline: (RMA, status, how long ago, actor role, actor).
INSERT INTO return_status_change (id, return_id, status, changed_at, actor_role, actor_id)
SELECT gen_random_uuid(), r.id, h.status, now() - h.ago, h.actor_role, h.actor_id::uuid
FROM (VALUES
    ('RMA-DEMO-00001', 'REQUESTED', INTERVAL '2 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00001', 'APPROVED', INTERVAL '2 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00002', 'REQUESTED', INTERVAL '1 day', 'CUSTOMER', NULL),
    ('RMA-DEMO-00002', 'APPROVED', INTERVAL '1 day', 'SYSTEM', NULL),
    ('RMA-DEMO-00002', 'CANCELLED', INTERVAL '20 hours', 'CUSTOMER', NULL),
    ('RMA-DEMO-00003', 'REQUESTED', INTERVAL '20 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00003', 'APPROVED', INTERVAL '20 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00004', 'REQUESTED', INTERVAL '9 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00004', 'APPROVED', INTERVAL '9 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00004', 'IN_TRANSIT', INTERVAL '7 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00005', 'REQUESTED', INTERVAL '2 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00005', 'APPROVED', INTERVAL '2 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00006', 'REQUESTED', INTERVAL '6 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00006', 'APPROVED', INTERVAL '6 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00006', 'IN_TRANSIT', INTERVAL '4 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00006', 'RECEIVED', INTERVAL '1 day', 'WAREHOUSE_AGENT',
     'aaaaaaaa-0000-0000-0000-000000000002'),
    ('RMA-DEMO-00007', 'REQUESTED', INTERVAL '10 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00007', 'APPROVED', INTERVAL '10 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00007', 'IN_TRANSIT', INTERVAL '8 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00007', 'RECEIVED', INTERVAL '5 days', 'WAREHOUSE_AGENT',
     'aaaaaaaa-0000-0000-0000-000000000001'),
    ('RMA-DEMO-00007', 'INSPECTED', INTERVAL '3 days', 'WAREHOUSE_AGENT',
     'aaaaaaaa-0000-0000-0000-000000000001'),
    ('RMA-DEMO-00007', 'REFUND_PENDING', INTERVAL '3 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00007', 'REFUNDED', INTERVAL '3 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00008', 'REQUESTED', INTERVAL '9 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00008', 'APPROVED', INTERVAL '9 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00008', 'IN_TRANSIT', INTERVAL '7 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00008', 'RECEIVED', INTERVAL '4 days', 'WAREHOUSE_AGENT',
     'aaaaaaaa-0000-0000-0000-000000000002'),
    ('RMA-DEMO-00008', 'REJECTED', INTERVAL '2 days', 'WAREHOUSE_AGENT',
     'aaaaaaaa-0000-0000-0000-000000000002'),
    ('RMA-DEMO-00009', 'REQUESTED', INTERVAL '5 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00009', 'APPROVED', INTERVAL '5 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00009', 'RECEIVED', INTERVAL '2 days', 'WAREHOUSE_AGENT',
     'aaaaaaaa-0000-0000-0000-000000000001'),
    ('RMA-DEMO-00009', 'INSPECTED', INTERVAL '1 day', 'WAREHOUSE_AGENT',
     'aaaaaaaa-0000-0000-0000-000000000001'),
    ('RMA-DEMO-00009', 'AWAITING_FINANCE_APPROVAL', INTERVAL '1 day', 'SYSTEM', NULL),
    ('RMA-DEMO-00010', 'REQUESTED', INTERVAL '1 day', 'CUSTOMER', NULL),
    ('RMA-DEMO-00011', 'REQUESTED', INTERVAL '3 days', 'CUSTOMER', NULL),
    ('RMA-DEMO-00011', 'APPROVED', INTERVAL '3 days', 'SYSTEM', NULL),
    ('RMA-DEMO-00011', 'IN_TRANSIT', INTERVAL '1 day', 'SYSTEM', NULL)
) AS h(rma, status, ago, actor_role, actor_id)
JOIN return_request r ON r.rma_number = h.rma;

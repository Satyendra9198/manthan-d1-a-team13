-- Demo data for the dev profile only (see application-dev.yml). Fictional customers and emails.
-- Repeatable migration: Flyway re-runs it whenever this file changes, so it starts by clearing the
-- demo orders and every return, refund and photo row that points at them (including returns
-- created through the API while testing). Photo files on disk are not removed.
-- Dates are relative to now() so every scenario stays valid on any demo day.
-- Scenario guide: docs/api/README.md.

CREATE TEMP TABLE demo_order_ids (id UUID PRIMARY KEY) ON COMMIT DROP;
INSERT INTO demo_order_ids VALUES
    ('11111111-1111-1111-1111-111111111111'),
    ('22222222-2222-2222-2222-222222222222'),
    ('33333333-3333-3333-3333-333333333333'),
    ('44444444-4444-4444-4444-444444444444'),
    ('55555555-5555-5555-5555-555555555555'),
    ('66666666-6666-6666-6666-666666666666');

DELETE FROM return_photo WHERE return_item_id IN (
    SELECT ri.id FROM return_item ri
    JOIN return_request r ON r.id = ri.return_id
    WHERE r.order_id IN (SELECT id FROM demo_order_ids));
DELETE FROM return_item WHERE return_id IN (
    SELECT id FROM return_request WHERE order_id IN (SELECT id FROM demo_order_ids));
DELETE FROM refund WHERE return_id IN (
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
     'Arjun Mehta', now() - INTERVAL '50 days');

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
     now() - INTERVAL '45 days');

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

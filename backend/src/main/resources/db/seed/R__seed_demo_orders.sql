-- Demo data for the dev profile only (see application-dev.yml). Fictional customers and emails.
-- Repeatable migration: Flyway re-runs it whenever this file changes, so it starts by clearing.
-- Delivery dates are relative to now() so every scenario stays valid on any demo day.
DELETE FROM order_item WHERE order_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222');
DELETE FROM customer_order WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222');

-- RR-10482: mixed basket matching the Figma "Which items are you returning?" screen.
INSERT INTO customer_order (id, order_number, customer_email, customer_name, placed_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'RR-10482', 'aditi.sharma@example.com',
        'Aditi Sharma', now() - INTERVAL '20 days');

INSERT INTO order_item (id, order_id, line_number, sku, product_name, variant, category,
                        unit_price_paise, quantity, delivered_at)
VALUES
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
     now() - INTERVAL '5 days');

-- RR-10577: not delivered yet, so nothing is returnable.
INSERT INTO customer_order (id, order_number, customer_email, customer_name, placed_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'RR-10577', 'rahul.verma@example.com',
        'Rahul Verma', now() - INTERVAL '2 days');

INSERT INTO order_item (id, order_id, line_number, sku, product_name, variant, category,
                        unit_price_paise, quantity, delivered_at)
VALUES
    ('22222222-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 1,
     'BEA-SERUM-30', 'Vitamin C serum', '30 ml', 'BEAUTY', 79900, 2, NULL);

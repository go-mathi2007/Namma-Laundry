-- Laundry Management System - Realistic Seed Data
USE laundry_db;

-- Reset data cleanly for idempotent seeding
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE order_status_history;
TRUNCATE TABLE invoices;
TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;
TRUNCATE TABLE price_list;
TRUNCATE TABLE customers;
TRUNCATE TABLE settings;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. System Settings
INSERT INTO settings (setting_key, setting_value) VALUES
  ('TAX_RATE_PERCENT', '18'),
  ('CURRENCY_SYMBOL', '₹'),
  ('SHOP_NAME', 'Namma Laundry & Dry Care'),
  ('SHOP_ADDRESS', '42 Anna Salai, Teynampet, Chennai 600018');

-- 2. Full Price List (8 item types x 3 service types = 24 entries)
INSERT INTO price_list (item_type, service_type, unit_price) VALUES
  ('SHIRT',    'WASH_FOLD',   40.00),
  ('SHIRT',    'DRY_CLEAN',   90.00),
  ('SHIRT',    'STEAM_PRESS', 25.00),
  ('TROUSERS', 'WASH_FOLD',   50.00),
  ('TROUSERS', 'DRY_CLEAN',  110.00),
  ('TROUSERS', 'STEAM_PRESS', 30.00),
  ('SAREE',    'WASH_FOLD',  120.00),
  ('SAREE',    'DRY_CLEAN',  260.00),
  ('SAREE',    'STEAM_PRESS', 80.00),
  ('JACKET',   'WASH_FOLD',  140.00),
  ('JACKET',   'DRY_CLEAN',  280.00),
  ('JACKET',   'STEAM_PRESS', 90.00),
  ('BEDSHEET', 'WASH_FOLD',   90.00),
  ('BEDSHEET', 'DRY_CLEAN',  180.00),
  ('BEDSHEET', 'STEAM_PRESS', 55.00),
  ('TOWEL',    'WASH_FOLD',   35.00),
  ('TOWEL',    'DRY_CLEAN',   75.00),
  ('TOWEL',    'STEAM_PRESS', 20.00),
  ('DELICATE', 'WASH_FOLD',   85.00),
  ('DELICATE', 'DRY_CLEAN',  190.00),
  ('DELICATE', 'STEAM_PRESS', 60.00),
  ('BLANKET',  'WASH_FOLD',  220.00),
  ('BLANKET',  'DRY_CLEAN',  380.00),
  ('BLANKET',  'STEAM_PRESS',120.00);

-- 3. Customers (8 customers)
INSERT INTO customers (customer_id, name, phone, email, address, created_at) VALUES
  (1, 'Aarav Sundaram',  '9840112233', 'aarav.sundaram@example.com',  '14 Luz Church Rd, Mylapore, Chennai',     '2026-11-10 09:15:00'),
  (2, 'Meera Krishnan',  '9840223344', 'meera.k@example.com',         '28 Besant Avenue, Adyar, Chennai',        '2026-11-11 10:30:00'),
  (3, 'Vikram Nair',     '9840334455', 'vikram.nair@example.com',     '7 Boat Club Rd, RA Puram, Chennai',       '2026-11-12 11:45:00'),
  (4, 'Priya Raman',     '9840445566', 'priya.raman@example.com',     '102 Habibullah Rd, T. Nagar, Chennai',    '2026-11-13 14:20:00'),
  (5, 'Karthik Raja',    '9840556677', 'karthik.raja@example.com',    '55 2nd Main Rd, Anna Nagar, Chennai',     '2026-11-14 16:05:00'),
  (6, 'Divya Subramanian','9840667788','divya.s@example.com',         '19 Cathedral Rd, Gopalapuram, Chennai',   '2026-11-15 09:50:00'),
  (7, 'Rohan Deshmukh',  '9840778899', 'rohan.d@example.com',         '81 Harrington Rd, Chetpet, Chennai',      '2026-11-16 12:10:00'),
  (8, 'Ananya Menon',    '9840889900', 'ananya.menon@example.com',    '33 East Coast Rd, Thiruvanmiyur, Chennai','2026-11-17 15:40:00');

-- 4. Orders (12 orders across all 6 statuses; ordered by created_at for deterministic FIFO)
INSERT INTO orders (order_id, customer_id, status, notes, created_at, updated_at, delivered_at) VALUES
  (1,  1, 'DELIVERED',        'Light starch on cotton shirts',          '2026-11-17 09:00:00', '2026-11-17 17:30:00', '2026-11-17 17:30:00'),
  (2,  2, 'DELIVERED',        'Handle silk saree border gently',        '2026-11-17 10:15:00', '2026-11-18 11:00:00', '2026-11-18 11:00:00'),
  (3,  3, 'CANCELLED',        'Customer rescheduled pickup',            '2026-11-17 11:30:00', '2026-11-17 12:10:00', NULL),
  (4,  4, 'READY_FOR_PICKUP', 'Call before delivery',                   '2026-11-18 09:10:00', '2026-11-19 10:00:00', NULL),
  (5,  5, 'READY_FOR_PICKUP', 'Fold sweaters flat, no hangers',         '2026-11-18 10:25:00', '2026-11-19 11:15:00', NULL),
  (6,  6, 'IRONING',          'Medium steam on linen trousers',         '2026-11-18 14:00:00', '2026-11-19 12:00:00', NULL),
  (7,  7, 'IRONING',          'Pack shirts individually',               '2026-11-18 15:20:00', '2026-11-19 12:30:00', NULL),
  (8,  8, 'WASHING',          'Use mild fragrance detergent',           '2026-11-19 08:30:00', '2026-11-19 13:00:00', NULL),
  (9,  1, 'WASHING',          'Blanket cycle on gentle warm',           '2026-11-19 09:45:00', '2026-11-19 13:20:00', NULL),
  (10, 2, 'QUEUED',           'Check collar cuff stain on white shirt', '2026-11-19 14:10:00', '2026-11-19 14:10:00', NULL),
  (11, 3, 'QUEUED',           'Express fold requested',                 '2026-11-19 14:45:00', '2026-11-19 14:45:00', NULL),
  (12, 4, 'QUEUED',           'Keep dupatta wrapped in tissue',         '2026-11-19 15:30:00', '2026-11-19 15:30:00', NULL);

-- 5. Order Items (unit_price matches price_list; line_total = quantity * unit_price)
INSERT INTO order_items (order_id, item_type, service_type, quantity, unit_price, line_total, special_instructions) VALUES
  -- Order 1: 4*40 + 2*50 = 260.00
  (1,  'SHIRT',    'WASH_FOLD',   4,  40.00, 160.00, 'Light starch'),
  (1,  'TROUSERS', 'WASH_FOLD',   2,  50.00, 100.00, 'Standard fold'),
  -- Order 2: 2*260 + 1*190 = 710.00
  (2,  'SAREE',    'DRY_CLEAN',   2, 260.00, 520.00, 'Zari care'),
  (2,  'DELICATE', 'DRY_CLEAN',   1, 190.00, 190.00, 'No high heat'),
  -- Order 3 (Cancelled): 2*90 = 180.00
  (3,  'SHIRT',    'DRY_CLEAN',   2,  90.00, 180.00, NULL),
  -- Order 4 (Ready, PAID): 3*25 + 2*180 = 435.00
  (4,  'SHIRT',    'STEAM_PRESS', 3,  25.00,  75.00, 'Hanger pack'),
  (4,  'BEDSHEET', 'DRY_CLEAN',   2, 180.00, 360.00, 'King size'),
  -- Order 5 (Ready, UNPAID - tests deliver warning): 1*280 + 2*35 = 350.00
  (5,  'JACKET',   'DRY_CLEAN',   1, 280.00, 280.00, 'Spot clean sleeves'),
  (5,  'TOWEL',    'WASH_FOLD',   2,  35.00,  70.00, 'Softener'),
  -- Order 6 (Ironing): 3*30 + 2*40 = 170.00
  (6,  'TROUSERS', 'STEAM_PRESS', 3,  30.00,  90.00, 'Sharp crease'),
  (6,  'SHIRT',    'WASH_FOLD',   2,  40.00,  80.00, NULL),
  -- Order 7 (Ironing): 5*25 + 1*80 = 205.00
  (7,  'SHIRT',    'STEAM_PRESS', 5,  25.00, 125.00, 'Individual sleeves'),
  (7,  'SAREE',    'STEAM_PRESS', 1,  80.00,  80.00, 'Box fold'),
  -- Order 8 (Washing): 3*90 + 4*35 = 410.00
  (8,  'BEDSHEET', 'WASH_FOLD',   3,  90.00, 270.00, 'Hot rinse'),
  (8,  'TOWEL',    'WASH_FOLD',   4,  35.00, 140.00, NULL),
  -- Order 9 (Washing): 2*220 = 440.00
  (9,  'BLANKET',  'WASH_FOLD',   2, 220.00, 440.00, 'Double woollen'),
  -- Order 10 (Queued): 3*40 + 2*110 = 340.00
  (10, 'SHIRT',    'WASH_FOLD',   3,  40.00, 120.00, 'Collar pre-treat'),
  (10, 'TROUSERS', 'DRY_CLEAN',   2, 110.00, 220.00, NULL),
  -- Order 11 (Queued): 1*380 + 2*85 = 550.00
  (11, 'BLANKET',  'DRY_CLEAN',   1, 380.00, 380.00, 'Comforter bag'),
  (11, 'DELICATE', 'WASH_FOLD',   2,  85.00, 170.00, 'Cold water'),
  -- Order 12 (Queued): 1*260 + 2*60 = 380.00
  (12, 'SAREE',    'DRY_CLEAN',   1, 260.00, 260.00, 'Tissue wrap'),
  (12, 'DELICATE', 'STEAM_PRESS', 2,  60.00, 120.00, 'Low steam');

-- 6. Invoices (tax_rate = 18.00%, tax_amount = ROUND(subtotal * 0.18, 2), total = subtotal + tax - discount)
INSERT INTO invoices (order_id, invoice_number, subtotal, tax_rate, tax_amount, discount, total_amount, payment_status, payment_method, paid_at, created_at) VALUES
  (1,  'INV-2026-0001', 260.00, 18.00,  46.80,  0.00, 306.80, 'PAID',   'UPI',  '2026-11-17 17:28:00', '2026-11-17 09:00:00'),
  (2,  'INV-2026-0002', 710.00, 18.00, 127.80, 50.00, 787.80, 'PAID',   'CARD', '2026-11-18 10:55:00', '2026-11-17 10:15:00'),
  (3,  'INV-2026-0003', 180.00, 18.00,  32.40,  0.00, 212.40, 'UNPAID', NULL,   NULL,                  '2026-11-17 11:30:00'),
  (4,  'INV-2026-0004', 435.00, 18.00,  78.30,  0.00, 513.30, 'PAID',   'CASH', '2026-11-19 10:05:00', '2026-11-18 09:10:00'),
  (5,  'INV-2026-0005', 350.00, 18.00,  63.00,  0.00, 413.00, 'UNPAID', NULL,   NULL,                  '2026-11-18 10:25:00'),
  (6,  'INV-2026-0006', 170.00, 18.00,  30.60,  0.00, 200.60, 'UNPAID', NULL,   NULL,                  '2026-11-18 14:00:00'),
  (7,  'INV-2026-0007', 205.00, 18.00,  36.90,  0.00, 241.90, 'PAID',   'UPI',  '2026-11-18 15:25:00', '2026-11-18 15:20:00'),
  (8,  'INV-2026-0008', 410.00, 18.00,  73.80, 20.00, 463.80, 'UNPAID', NULL,   NULL,                  '2026-11-19 08:30:00'),
  (9,  'INV-2026-0009', 440.00, 18.00,  79.20,  0.00, 519.20, 'UNPAID', NULL,   NULL,                  '2026-11-19 09:45:00'),
  (10, 'INV-2026-0010', 340.00, 18.00,  61.20,  0.00, 401.20, 'UNPAID', NULL,   NULL,                  '2026-11-19 14:10:00'),
  (11, 'INV-2026-0011', 550.00, 18.00,  99.00,  0.00, 649.00, 'PAID',   'CARD', '2026-11-19 14:46:00', '2026-11-19 14:45:00'),
  (12, 'INV-2026-0012', 380.00, 18.00,  68.40,  0.00, 448.40, 'UNPAID', NULL,   NULL,                  '2026-11-19 15:30:00');

-- 7. Order Status History
INSERT INTO order_status_history (order_id, old_status, new_status, changed_at) VALUES
  (1,  NULL,               'QUEUED',           '2026-11-17 09:00:00'),
  (1,  'QUEUED',           'WASHING',          '2026-11-17 10:00:00'),
  (1,  'WASHING',          'IRONING',          '2026-11-17 13:00:00'),
  (1,  'IRONING',          'READY_FOR_PICKUP', '2026-11-17 15:30:00'),
  (1,  'READY_FOR_PICKUP', 'DELIVERED',        '2026-11-17 17:30:00'),
  (2,  NULL,               'QUEUED',           '2026-11-17 10:15:00'),
  (2,  'QUEUED',           'WASHING',          '2026-11-17 12:00:00'),
  (2,  'WASHING',          'IRONING',          '2026-11-17 16:00:00'),
  (2,  'IRONING',          'READY_FOR_PICKUP', '2026-11-18 09:00:00'),
  (2,  'READY_FOR_PICKUP', 'DELIVERED',        '2026-11-18 11:00:00'),
  (3,  NULL,               'QUEUED',           '2026-11-17 11:30:00'),
  (3,  'QUEUED',           'CANCELLED',        '2026-11-17 12:10:00'),
  (4,  NULL,               'QUEUED',           '2026-11-18 09:10:00'),
  (4,  'QUEUED',           'WASHING',          '2026-11-18 11:00:00'),
  (4,  'WASHING',          'IRONING',          '2026-11-18 15:00:00'),
  (4,  'IRONING',          'READY_FOR_PICKUP', '2026-11-19 10:00:00'),
  (5,  NULL,               'QUEUED',           '2026-11-18 10:25:00'),
  (5,  'QUEUED',           'WASHING',          '2026-11-18 13:00:00'),
  (5,  'WASHING',          'IRONING',          '2026-11-19 08:30:00'),
  (5,  'IRONING',          'READY_FOR_PICKUP', '2026-11-19 11:15:00'),
  (6,  NULL,               'QUEUED',           '2026-11-18 14:00:00'),
  (6,  'QUEUED',           'WASHING',          '2026-11-18 16:30:00'),
  (6,  'WASHING',          'IRONING',          '2026-11-19 12:00:00'),
  (7,  NULL,               'QUEUED',           '2026-11-18 15:20:00'),
  (7,  'QUEUED',           'WASHING',          '2026-11-19 09:00:00'),
  (7,  'WASHING',          'IRONING',          '2026-11-19 12:30:00'),
  (8,  NULL,               'QUEUED',           '2026-11-19 08:30:00'),
  (8,  'QUEUED',           'WASHING',          '2026-11-19 13:00:00'),
  (9,  NULL,               'QUEUED',           '2026-11-19 09:45:00'),
  (9,  'QUEUED',           'WASHING',          '2026-11-19 13:20:00'),
  (10, NULL,               'QUEUED',           '2026-11-19 14:10:00'),
  (11, NULL,               'QUEUED',           '2026-11-19 14:45:00'),
  (12, NULL,               'QUEUED',           '2026-11-19 15:30:00');

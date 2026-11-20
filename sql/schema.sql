-- Laundry Management System - MySQL 8 Database Schema
-- Charset & Collation: utf8mb4 for full Unicode support (including currency symbols)

CREATE DATABASE IF NOT EXISTS laundry_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE laundry_db;

-- 1. Customers
CREATE TABLE IF NOT EXISTS customers (
  customer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(150) NULL,
  address VARCHAR(300) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_customers_phone UNIQUE (phone),
  INDEX idx_customers_name (name)
) ENGINE=InnoDB;

-- 2. Price List (8 item types x 3 service types)
CREATE TABLE IF NOT EXISTS price_list (
  price_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_type ENUM(
    'SHIRT',
    'TROUSERS',
    'SAREE',
    'JACKET',
    'BEDSHEET',
    'TOWEL',
    'DELICATE',
    'BLANKET'
  ) NOT NULL,
  service_type ENUM('WASH_FOLD', 'DRY_CLEAN', 'STEAM_PRESS') NOT NULL,
  unit_price DECIMAL(10, 2) NOT NULL,
  CONSTRAINT uq_price_item_service UNIQUE (item_type, service_type),
  CONSTRAINT chk_price_non_negative CHECK (unit_price >= 0.00)
) ENGINE=InnoDB;

-- 3. Orders
CREATE TABLE IF NOT EXISTS orders (
  order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  status ENUM(
    'QUEUED',
    'WASHING',
    'IRONING',
    'READY_FOR_PICKUP',
    'DELIVERED',
    'CANCELLED'
  ) NOT NULL DEFAULT 'QUEUED',
  notes VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delivered_at TIMESTAMP NULL DEFAULT NULL,
  CONSTRAINT fk_orders_customer
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  INDEX idx_orders_status_created (status, created_at),
  INDEX idx_orders_customer (customer_id)
) ENGINE=InnoDB;

-- 4. Order Items (unit_price is snapshotted at order creation time)
CREATE TABLE IF NOT EXISTS order_items (
  item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  item_type ENUM(
    'SHIRT',
    'TROUSERS',
    'SAREE',
    'JACKET',
    'BEDSHEET',
    'TOWEL',
    'DELICATE',
    'BLANKET'
  ) NOT NULL,
  service_type ENUM('WASH_FOLD', 'DRY_CLEAN', 'STEAM_PRESS') NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10, 2) NOT NULL,
  line_total DECIMAL(12, 2) NOT NULL,
  special_instructions VARCHAR(255) NULL,
  CONSTRAINT fk_order_items_order
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT chk_order_items_qty CHECK (quantity > 0),
  CONSTRAINT chk_order_items_price CHECK (unit_price >= 0.00),
  CONSTRAINT chk_order_items_total CHECK (line_total >= 0.00),
  INDEX idx_order_items_order (order_id)
) ENGINE=InnoDB;

-- 5. Invoices (1-to-1 with orders)
CREATE TABLE IF NOT EXISTS invoices (
  invoice_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  invoice_number VARCHAR(40) NOT NULL,
  subtotal DECIMAL(12, 2) NOT NULL,
  tax_rate DECIMAL(5, 2) NOT NULL,
  tax_amount DECIMAL(12, 2) NOT NULL,
  discount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  total_amount DECIMAL(12, 2) NOT NULL,
  payment_status ENUM('UNPAID', 'PAID') NOT NULL DEFAULT 'UNPAID',
  payment_method ENUM('CASH', 'CARD', 'UPI') NULL DEFAULT NULL,
  paid_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_invoices_order UNIQUE (order_id),
  CONSTRAINT uq_invoices_number UNIQUE (invoice_number),
  CONSTRAINT fk_invoices_order
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT chk_invoices_amounts CHECK (
    subtotal >= 0.00 AND tax_rate >= 0.00 AND tax_amount >= 0.00
    AND discount >= 0.00 AND total_amount >= 0.00
  ),
  INDEX idx_invoices_payment_status (payment_status)
) ENGINE=InnoDB;

-- 6. Order Status History (audit trail for every stage transition)
CREATE TABLE IF NOT EXISTS order_status_history (
  history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  old_status VARCHAR(30) NULL,
  new_status VARCHAR(30) NOT NULL,
  changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_history_order
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  INDEX idx_history_order_time (order_id, changed_at)
) ENGINE=InnoDB;

-- 7. System Settings
CREATE TABLE IF NOT EXISTS settings (
  setting_key VARCHAR(64) PRIMARY KEY,
  setting_value VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

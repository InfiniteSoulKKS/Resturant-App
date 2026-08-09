-- MySQL DDL Schema for SavoryStay Culinary Operations System

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, -- Encoded via BCryptPasswordEncoder
    role VARCHAR(30) DEFAULT 'ROLE_CUSTOMER',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS menu_items (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    preparation_time_minutes INT,
    chef_special BOOLEAN DEFAULT FALSE,
    available BOOLEAN DEFAULT TRUE,
    image_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(64) PRIMARY KEY,
    order_number VARCHAR(20) NOT NULL UNIQUE,
    table_number INT NOT NULL,
    guests INT NOT NULL,
    time_slot VARCHAR(20) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    user_id VARCHAR(64),
    total_amount DECIMAL(10, 2) NOT NULL,
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    payment_method VARCHAR(50),
    order_status VARCHAR(20) DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS payments (
    transaction_id VARCHAR(100) PRIMARY KEY,
    order_id VARCHAR(64),
    gateway VARCHAR(30) NOT NULL, -- STRIPE, PAYPAL, UPI, CASH
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    payment_status VARCHAR(30) DEFAULT 'PAID',
    card_last4 VARCHAR(4),
    client_secret VARCHAR(255),
    gateway_raw_response TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64),
    menu_item_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id)
);

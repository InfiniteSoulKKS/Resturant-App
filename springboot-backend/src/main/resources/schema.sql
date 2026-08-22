-- MySQL DDL Schema for SavoryStay Multi-Restaurant Platform

CREATE TABLE IF NOT EXISTS restaurants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(60) UNIQUE,
    description TEXT,
    address VARCHAR(255),
    city VARCHAR(80),
    cuisine VARCHAR(60),
    phone VARCHAR(20),
    email VARCHAR(100),
    logo_url TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    currency VARCHAR(10) DEFAULT 'INR',
    owner_id VARCHAR(64),
    auto_join_customers BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) UNIQUE, -- Nullable: staff without an email can be created via phone
    password_hash VARCHAR(255) NOT NULL, -- Encoded via BCryptPasswordEncoder
    role VARCHAR(30) DEFAULT 'ROLE_CUSTOMER', -- ROLE_SUPER_ADMIN, ROLE_ADMIN, ROLE_MANAGER, ROLE_CHEF, ROLE_CUSTOMER
    phone VARCHAR(15) UNIQUE,
    restaurant_id VARCHAR(64),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS menu_items (
    id VARCHAR(64) PRIMARY KEY,
    restaurant_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    image_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'Available',
    is_veg BOOLEAN DEFAULT TRUE,
    spice_level VARCHAR(20) DEFAULT 'Medium',
    prep_minutes INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ingredients (
    id VARCHAR(64) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0, -- optimistic locking (concurrent stock writes)
    restaurant_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    normalized_name VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    category VARCHAR(50),
    stock_quantity DECIMAL(12, 3) NOT NULL DEFAULT 0,
    reorder_level DECIMAL(12, 3) DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
    UNIQUE KEY uk_ingredient_restaurant_normalized (restaurant_id, normalized_name)
);

CREATE TABLE IF NOT EXISTS menu_item_ingredients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_item_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    restaurant_id VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    quantity_per_unit DECIMAL(12, 3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE,
    FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_menu_item_ingredient (menu_item_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(64) PRIMARY KEY,
    order_number VARCHAR(20) NOT NULL UNIQUE,
    restaurant_id VARCHAR(64) NOT NULL,
    order_type VARCHAR(20) NOT NULL DEFAULT 'PICKUP', -- PICKUP, DINE_IN, PRE_ORDER
    table_number INT,
    guests INT,
    time_slot VARCHAR(100),   -- human-readable slot labels (e.g. "Tomorrow 30 Mins (Ready by 07:45 PM)")
    pickup_time VARCHAR(100), -- ISO datetime for pre-orders, or pickup labels for PICKUP
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(30),
    customer_email VARCHAR(100),
    user_id VARCHAR(64),
    total_amount DECIMAL(10, 2) NOT NULL,
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    payment_method VARCHAR(50),
    order_status VARCHAR(20) DEFAULT 'NEW', -- NEW, PREPARING, PACKED_READY, COMPLETED, DECLINED, CANCELLED
    cancelled_by VARCHAR(64),
    cancel_reason VARCHAR(500),
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    -- Hot query paths: "my orders" (user) and kitchen boards (restaurant)
    INDEX idx_orders_user (user_id),
    INDEX idx_orders_restaurant (restaurant_id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    menu_item_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    notes VARCHAR(255),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payments (
    transaction_id VARCHAR(100) PRIMARY KEY,
    order_id VARCHAR(64),
    gateway VARCHAR(30) NOT NULL, -- STRIPE, PAYPAL, UPI, CASH, MOCK
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'INR',
    payment_status VARCHAR(30) DEFAULT 'PAID',
    card_last4 VARCHAR(4),
    client_secret VARCHAR(255),
    gateway_raw_response TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    restaurant_id VARCHAR(64),
    order_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    message TEXT,
    type VARCHAR(30),
    channel VARCHAR(60) DEFAULT 'APP', -- APP or comma list e.g. "APP,SMS,WHATSAPP"
    is_read BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, DELIVERED, FAILED, READ
    attempt_count INT DEFAULT 0,
    sent_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Append-only audit trail for order status transitions (no UPDATE/DELETE by convention)
CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(64),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

-- Append-only inventory movement ledger (no UPDATE/DELETE by convention)
CREATE TABLE IF NOT EXISTS inventory_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_id VARCHAR(64) NOT NULL,
    delta DECIMAL(12, 3) NOT NULL, -- negative = consumption, positive = restock
    reason VARCHAR(30) NOT NULL,   -- ORDER_CONSUMED, MANUAL_RESTOCK, WASTAGE, MANUAL_CORRECTION
    reference_id VARCHAR(64),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inventory_id) REFERENCES ingredients(id) ON DELETE CASCADE
);

-- Transactional Outbox (event backbone for async dispatch)
CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(64),
    event_type VARCHAR(50) NOT NULL, -- order.created, order.status.changed, inventory.stock.low, ...
    payload TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    retry_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED
    failed_at TIMESTAMP NULL,
    -- The OutboxPoller scans unpublished rows every 3s
    INDEX idx_outbox_unpublished (published_at)
);

-- Dead-letter audit: notifications that exhausted Kafka retries and were sent
-- to a -dlt topic. Populated by the @DltHandler consumers for replay/debugging.
CREATE TABLE IF NOT EXISTS failed_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_topic VARCHAR(100),
    received_topic VARCHAR(100),
    event_type VARCHAR(50),
    aggregate_id VARCHAR(64),
    payload TEXT,
    error TEXT,
    failed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Scheduled pricing (current price = latest rule with effective_from <= now())
CREATE TABLE IF NOT EXISTS price_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_item_id VARCHAR(64) NOT NULL,
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    effective_from TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE
);

-- Weekly operating hours per restaurant day (1=Monday .. 7=Sunday).
-- closed=true is a full weekly holiday; a partial day (e.g. open till 14:00)
-- is modeled via close_time. Any closed period on a day blocks pre-orders for
-- that day entirely; same-day PICKUP/DINE_IN are unaffected.
CREATE TABLE IF NOT EXISTS restaurant_operating_hours (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id VARCHAR(64) NOT NULL,
    day_of_week INT NOT NULL,
    open_time TIME,
    close_time TIME,
    closed BOOLEAN DEFAULT FALSE,
    UNIQUE KEY uk_op_hours_day (restaurant_id, day_of_week),
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

-- Per-restaurant pre-order rules: orders for date D close at cutoff_time on D-1
-- (business timezone); customers can order up to advance_days ahead.
CREATE TABLE IF NOT EXISTS preorder_settings (
    restaurant_id VARCHAR(64) PRIMARY KEY,
    cutoff_time TIME NOT NULL DEFAULT '09:00:00',
    advance_days INT NOT NULL DEFAULT 7,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

-- Recurring weekly availability: which weekdays a dish can be pre-ordered.
-- No rows = dish available every day (backward compatible).
CREATE TABLE IF NOT EXISTS dish_availability (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id VARCHAR(64) NOT NULL,
    menu_item_id VARCHAR(64) NOT NULL,
    day_of_week INT NOT NULL,
    UNIQUE KEY uk_dish_avail_day (menu_item_id, day_of_week),
    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE
);

-- Manager overrides for a specific date + dish (OPEN / CLOSE).
-- Precedence: CLOSE > OPEN > weekly schedule; restaurant closure always blocks.
CREATE TABLE IF NOT EXISTS dish_slot_override (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id VARCHAR(64) NOT NULL,
    menu_item_id VARCHAR(64) NOT NULL,
    target_date DATE NOT NULL,
    action VARCHAR(10) NOT NULL,
    UNIQUE KEY uk_dish_slot_date (menu_item_id, target_date),
    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE
);

-- Customer–restaurant membership: tracks which restaurants a customer belongs to.
-- A single customer account may be a member of multiple restaurants.
CREATE TABLE IF NOT EXISTS customer_restaurant (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    restaurant_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(100),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_restaurant (customer_id, restaurant_id),
    FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS refunds (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    payment_id VARCHAR(100) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'INR',
    refund_status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED, PROCESSING, COMPLETED, FAILED
    provider_refund_id VARCHAR(100),
    reason VARCHAR(500),
    initiated_by VARCHAR(64),
    restaurant_id VARCHAR(64),
    gateway VARCHAR(30),
    requested_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (payment_id) REFERENCES payments(transaction_id) ON DELETE CASCADE,
    INDEX idx_refunds_order (order_id),
    INDEX idx_refunds_restaurant (restaurant_id)
);

-- General-purpose append-only audit trail for all business mutations
CREATE TABLE IF NOT EXISTS audit_trail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64),
    actor_role VARCHAR(30),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    reason VARCHAR(500),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_restaurant (restaurant_id),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_actor (actor_user_id)
);

CREATE TABLE IF NOT EXISTS otp_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    purpose VARCHAR(20) DEFAULT 'REGISTRATION', -- LOGIN (validated account send) vs REGISTRATION
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    attempt_count INT DEFAULT 0
);

-- Таблица пользователей
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Таблица счетов
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_number VARCHAR(34) NOT NULL UNIQUE, -- IBAN format
    currency_code VARCHAR(3) NOT NULL DEFAULT 'RUB',
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Таблица транзакций (переводов)
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    from_account_id BIGINT NOT NULL REFERENCES accounts(id),
    to_account_id BIGINT NOT NULL REFERENCES accounts(id),
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'RUB',
    status transaction_status NOT NULL DEFAULT 'PENDING',
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    exchange_rate DECIMAL(10,6) DEFAULT 1.0,
    fee_amount DECIMAL(15,2) DEFAULT 0.00
);

-- Таблица для хранения истории изменений баланса
CREATE TABLE balance_history (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    transaction_id BIGINT REFERENCES transactions(id),
    old_balance DECIMAL(15,2) NOT NULL,
    new_balance DECIMAL(15,2) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    operation_type operation_type NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица курсов валют
CREATE TABLE exchange_rates (
    id BIGSERIAL PRIMARY KEY,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(10,6) NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(from_currency, to_currency, effective_date)
);

-- Таблица комиссий
CREATE TABLE fees (
    id BIGSERIAL PRIMARY KEY,
    fee_type VARCHAR(50) NOT NULL, -- 'PERCENTAGE', 'FIXED', 'TIERED'
    from_amount DECIMAL(15,2),
    to_amount DECIMAL(15,2),
    percentage_rate DECIMAL(5,3), -- 1.5% = 1.500
    fixed_amount DECIMAL(15,2),
    min_fee DECIMAL(15,2) DEFAULT 0,
    max_fee DECIMAL(15,2),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'RUB',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для аудита (логирование действий)
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    table_name VARCHAR(50),
    record_id BIGINT,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Кастомные типы для статусов и операций
CREATE TYPE transaction_status AS ENUM (
    'PENDING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
    'REVERSED'
);

CREATE TYPE operation_type AS ENUM (
    'DEPOSIT',
    'WITHDRAWAL',
    'TRANSFER',
    'FEE',
    'ADJUSTMENT'
);

-- Индексы для оптимизации
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_transactions_from_account_id ON transactions(from_account_id);
CREATE INDEX idx_transactions_to_account_id ON transactions(to_account_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_balance_history_account_id ON balance_history(account_id);
CREATE INDEX idx_balance_history_transaction_id ON balance_history(transaction_id);
CREATE INDEX idx_exchange_rates_currency ON exchange_rates(from_currency, to_currency);
CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггеры для автоматического обновления updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Триггер для логирования изменений баланса
CREATE OR REPLACE FUNCTION log_balance_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.balance != NEW.balance THEN
        INSERT INTO balance_history (account_id, old_balance, new_balance, amount, operation_type)
        VALUES (NEW.id, OLD.balance, NEW.balance, NEW.balance - OLD.balance, 'ADJUSTMENT');
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER log_balance_changes AFTER UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION log_balance_change();

-- Ограничения
ALTER TABLE transactions ADD CONSTRAINT chk_different_accounts
    CHECK (from_account_id != to_account_id);

ALTER TABLE accounts ADD CONSTRAINT chk_positive_balance
    CHECK (balance >= 0);

-- Начальные данные
INSERT INTO exchange_rates (from_currency, to_currency, rate, effective_date) VALUES
('USD', 'RUB', 90.5, CURRENT_DATE),
('EUR', 'RUB', 98.2, CURRENT_DATE),
('RUB', 'USD', 0.0110, CURRENT_DATE),
('RUB', 'EUR', 0.0102, CURRENT_DATE);

INSERT INTO fees (fee_type, from_amount, to_amount, percentage_rate, fixed_amount, currency_code) VALUES
('PERCENTAGE', 0, 1000, 1.5, 0, 'RUB'),
('PERCENTAGE', 1000, 10000, 1.0, 0, 'RUB'),
('PERCENTAGE', 10000, NULL, 0.5, 0, 'RUB');
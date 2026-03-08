CREATE SCHEMA IF NOT EXISTS bank;

-- clients
CREATE TABLE bank.users (
    id UUID PRIMARY KEY, -- [index]
    user_name VARCHAR(255), -- [index]
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- clients accounts
CREATE TABLE bank.accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES bank.users(id) ON DELETE CASCADE,
    account_number VARCHAR(34) NOT NULL UNIQUE, -- [index] IBAN format
    currency_code VARCHAR(3) NOT NULL DEFAULT 'RUB',
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- clients transactions
CREATE TABLE bank.transactions (
    id UUID PRIMARY KEY,
    from_account VARCHAR(34) NOT NULL REFERENCES bank.accounts(account_number), -- [index]
    to_account VARCHAR(34) NOT NULL REFERENCES bank.accounts(account_number), -- [index]
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'RUB',
    exchange_rate DECIMAL(15,2) DEFAULT 1.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- history of clients transactions
CREATE TABLE bank.balance_history (
    id UUID PRIMARY KEY,
    account_number VARCHAR(34) REFERENCES bank.accounts(account_number),
    old_balance DECIMAL(15,2) NOT NULL,
    new_balance DECIMAL(15,2) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- users (id via gen_random_uuid() for new rows)
INSERT INTO bank.users (id, user_name, email, phone, created_at, updated_at, is_active) VALUES
('e4224dc9-ac32-4682-a43c-d7cfc791af5b', 'James Bond', '007@bank.local', '+7(900)007-45-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
('3c4987b6-c696-4768-97e2-f79622a49e6b', 'Judge Dredd', 'ImTheLaw@bank.local', '+7(903)123-45-02', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
('bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476', 'Ernest Hemingway', 'AFarewellToArms@bank.local', '+7(900)123-45-03', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true);

-- accounts (id UUID, user_id -> users.id)
INSERT INTO bank.accounts (id, user_id, account_number, currency_code, balance, created_at, updated_at, is_active) VALUES
(gen_random_uuid(), 'e4224dc9-ac32-4682-a43c-d7cfc791af5b', '8901201001', 'RUB', 1000.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), '3c4987b6-c696-4768-97e2-f79622a49e6b', '8901201002', 'RUB', 1000.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), 'bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476', '8901201003', 'RUB', 1000.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true);

-- balance_history: начальные записи по счёту (при создании аккаунта)
INSERT INTO bank.balance_history (id, account_number, old_balance, new_balance, amount, created_at) VALUES
(gen_random_uuid(), '8901201001', 0.00, 1000.00, 1000.00, CURRENT_TIMESTAMP),
(gen_random_uuid(), '8901201002', 0.00, 1000.00, 1000.00, CURRENT_TIMESTAMP),
(gen_random_uuid(), '8901201003', 0.00, 1000.00, 1000.00, CURRENT_TIMESTAMP);

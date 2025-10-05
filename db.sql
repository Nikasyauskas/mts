CREATE SCHEMA IF NOT EXISTS bank;

-- clients/accounts
CREATE TABLE bank.users (
    id UUID PRIMARY KEY,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
--    PRIMARY KEY(id, account_number)
);

INSERT INTO bank.users (id, account_number, balance, created_at, updated_at, is_active) VALUES
(gen_random_uuid(),'8901201001',0.00,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,true),
(gen_random_uuid(),'8901201002',100.00,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,true),
(gen_random_uuid(),'8901201003',200.00,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,true)
;

CREATE TABLE bank.transactions (
    id UUID,
    from_account_id VARCHAR(34) NOT NULL,
    to_account_id VARCHAR(34) NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
);

INSERT INTO bank.transactions (id, from_account_id, to_account_id, amount, created_at) VALUES
('e4224dc9-ac32-4682-a43c-d7cfc791af5b','8901201001','8901201002',50.00,CURRENT_TIMESTAMP)
;


-- Таблица для хранения истории изменений баланса
CREATE TABLE balance_history (
    id UUID,
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    transaction_id BIGINT REFERENCES transactions(id),
    old_balance DECIMAL(15,2) NOT NULL,
    new_balance DECIMAL(15,2) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    operation_type operation_type NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- | e4224dc9-ac32-4682-a43c-d7cfc791af5b | 8901201001
-- | 3c4987b6-c696-4768-97e2-f79622a49e6b | 8901201002
-- | bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476 | 8901201003

-- Индексы для оптимизации
-- CREATE INDEX idx_accounts_user_id ON users(user_id);

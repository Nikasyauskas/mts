CREATE SCHEMA IF NOT EXISTS bank;

-- clients/accounts
CREATE TABLE bank.users (
    id UUID PRIMARY KEY,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

INSERT INTO bank.users (id, account_number, balance, created_at, updated_at, is_active) VALUES
('e4224dc9-ac32-4682-a43c-d7cfc791af5b','8901201001',1000.00,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,true),
('3c4987b6-c696-4768-97e2-f79622a49e6b','8901201002',1000.00,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,true),
('bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476','8901201003',1000.00,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,true)
;
-- gen_random_uuid()

CREATE TABLE bank.transactions (
    id UUID,
    from_account_id VARCHAR(34) NOT NULL,
    to_account_id VARCHAR(34) NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для хранения истории изменений баланса
CREATE TABLE bank.balance_history (
    id UUID,
    account_number VARCHAR(34),
    old_balance DECIMAL(15,2),
    new_balance DECIMAL(15,2),
    amount DECIMAL(15,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- когда аккаунт будет создаваться необходимо, чтобы в hist заносились default'ные значения
INSERT INTO bank.balance_history(id,account_number,old_balance,new_balance,amount,created_at) VALUES
('e4224dc9-ac32-4682-a43c-d7cfc791af5b','8901201001',0.00,1000.00,1000.00,CURRENT_TIMESTAMP),
('3c4987b6-c696-4768-97e2-f79622a49e6b','8901201002',0.00,1000.00,1000.00,CURRENT_TIMESTAMP),
('bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476','8901201003',0.00,1000.00,1000.00,CURRENT_TIMESTAMP)
;


-- | e4224dc9-ac32-4682-a43c-d7cfc791af5b | 8901201001
-- | 3c4987b6-c696-4768-97e2-f79622a49e6b | 8901201002
-- | bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476 | 8901201003

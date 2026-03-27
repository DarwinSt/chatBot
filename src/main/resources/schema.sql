-- ============================================================
-- Esquema alineado con las entidades JPA (financebot)
-- Ejecutar conectado a la base de datos objetivo (p. ej. financesbot).
-- Crea tipos enum si no existen y tablas con IF NOT EXISTS.
-- ============================================================

-- ---------- Tipos enum (requeridos por columnas NAMED_ENUM) ----------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'account_type') THEN
        CREATE TYPE account_type AS ENUM ('CORRIENTE', 'AHORROS', 'EFECTIVO', 'BILLETERA_DIGITAL');
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'category_type') THEN
        CREATE TYPE category_type AS ENUM ('INGRESO', 'GASTO', 'DEUDA');
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'expense_type') THEN
        CREATE TYPE expense_type AS ENUM ('FIJO', 'VARIABLE');
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'debt_status') THEN
        CREATE TYPE debt_status AS ENUM ('ACTIVA', 'PAGADA', 'VENCIDA', 'CANCELADA');
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'reminder_type') THEN
        CREATE TYPE reminder_type AS ENUM ('PAGO_DEUDA', 'PAGO_TARJETA', 'GASTO_FIJO', 'GENERAL');
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'telegram_conversation_state') THEN
        CREATE TYPE telegram_conversation_state AS ENUM ('INACTIVO', 'ESPERANDO_MONTO_GASTO');
    END IF;
END$$;

-- ---------- Tablas ----------
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type account_type NOT NULL,
    initial_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    current_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type category_type NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_categories_name_type UNIQUE (name, type)
);

CREATE TABLE IF NOT EXISTS credit_cards (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    total_limit NUMERIC(19,2) NOT NULL,
    used_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    statement_cutoff_day SMALLINT,
    payment_due_day SMALLINT,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS debts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    pending_amount NUMERIC(19,2) NOT NULL,
    start_date DATE NOT NULL,
    due_date DATE,
    creditor VARCHAR(150),
    notes VARCHAR(500),
    status debt_status NOT NULL DEFAULT 'ACTIVA',
    category_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_debts_category_id FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE TABLE IF NOT EXISTS incomes (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19,2) NOT NULL,
    income_date DATE NOT NULL,
    origin VARCHAR(150),
    description VARCHAR(500),
    category_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incomes_category_id FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_incomes_destination_account_id FOREIGN KEY (destination_account_id) REFERENCES accounts (id)
);

CREATE TABLE IF NOT EXISTS expenses (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19,2) NOT NULL,
    expense_date DATE NOT NULL,
    description VARCHAR(500),
    expense_type expense_type NOT NULL,
    category_id BIGINT NOT NULL,
    payment_account_id BIGINT,
    credit_card_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expenses_category_id FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_expenses_payment_account_id FOREIGN KEY (payment_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_expenses_credit_card_id FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id)
);

CREATE TABLE IF NOT EXISTS credit_card_payments (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19,2) NOT NULL,
    payment_date DATE NOT NULL,
    notes VARCHAR(500),
    source_account_id BIGINT NOT NULL,
    credit_card_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_card_payments_source_account_id FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_credit_card_payments_credit_card_id FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id)
);

CREATE TABLE IF NOT EXISTS debt_payments (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19,2) NOT NULL,
    payment_date DATE NOT NULL,
    notes VARCHAR(500),
    source_account_id BIGINT NOT NULL,
    debt_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_debt_payments_source_account_id FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_debt_payments_debt_id FOREIGN KEY (debt_id) REFERENCES debts (id)
);

CREATE TABLE IF NOT EXISTS transfers (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19,2) NOT NULL,
    transfer_date DATE NOT NULL,
    description VARCHAR(500),
    source_account_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfers_source_account_id FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transfers_destination_account_id FOREIGN KEY (destination_account_id) REFERENCES accounts (id)
);

CREATE TABLE IF NOT EXISTS reminders (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    reminder_type reminder_type NOT NULL,
    reminder_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS telegram_chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL UNIQUE,
    current_state telegram_conversation_state NOT NULL DEFAULT 'INACTIVO',
    pending_command VARCHAR(100),
    context_data TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

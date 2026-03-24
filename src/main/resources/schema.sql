-- Ajustes incrementales para alinear la BD existente `financesbot`
-- con el modelo Java actual. No recrea tablas ni elimina datos.
-- Ejecutar en pgAdmin sobre la base `financesbot`.

BEGIN;

-- ============================================================
-- 1) ENUMS usados por el codigo Java
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_enum e ON e.enumtypid = t.oid
        WHERE t.typname = 'debt_status' AND e.enumlabel = 'CANCELLED'
    ) THEN
        ALTER TYPE debt_status ADD VALUE 'CANCELLED';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_enum e ON e.enumtypid = t.oid
        WHERE t.typname = 'expense_type' AND e.enumlabel = 'ACCOUNT'
    ) THEN
        ALTER TYPE expense_type ADD VALUE 'ACCOUNT';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_enum e ON e.enumtypid = t.oid
        WHERE t.typname = 'expense_type' AND e.enumlabel = 'CREDIT_CARD'
    ) THEN
        ALTER TYPE expense_type ADD VALUE 'CREDIT_CARD';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_enum e ON e.enumtypid = t.oid
        WHERE t.typname = 'telegram_conversation_state' AND e.enumlabel = 'CONVERSATION'
    ) THEN
        ALTER TYPE telegram_conversation_state ADD VALUE 'CONVERSATION';
    END IF;
END
$$;

-- ============================================================
-- 2) Tipos y longitudes de columnas
-- ============================================================

-- credit_cards: dias como SMALLINT
ALTER TABLE credit_cards
    ALTER COLUMN statement_cutoff_day TYPE SMALLINT USING statement_cutoff_day::SMALLINT,
    ALTER COLUMN payment_due_day TYPE SMALLINT USING payment_due_day::SMALLINT;

-- telegram_chat_sessions: chat_id como BIGINT
ALTER TABLE telegram_chat_sessions
    ALTER COLUMN chat_id TYPE BIGINT USING chat_id::BIGINT;

-- debts: longitudes
ALTER TABLE debts
    ALTER COLUMN name TYPE VARCHAR(150),
    ALTER COLUMN creditor TYPE VARCHAR(150);

-- incomes: longitud de origin
ALTER TABLE incomes
    ALTER COLUMN origin TYPE VARCHAR(150);

-- reminders: title y reminder_date como DATE
ALTER TABLE reminders
    ALTER COLUMN title TYPE VARCHAR(150),
    ALTER COLUMN reminder_date TYPE DATE USING reminder_date::DATE;

COMMIT;

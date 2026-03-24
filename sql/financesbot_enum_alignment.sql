-- Alineacion incremental de enums para compatibilidad con el codigo Java actual.
-- No recrea tablas ni borra datos.
-- Ejecutar en la base: financesbot

BEGIN;

-- 1) debt_status: el codigo usa CANCELLED en validaciones/flujo.
ALTER TYPE debt_status ADD VALUE IF NOT EXISTS 'CANCELLED';

-- 2) expense_type: el codigo persiste ACCOUNT y CREDIT_CARD.
ALTER TYPE expense_type ADD VALUE IF NOT EXISTS 'ACCOUNT';
ALTER TYPE expense_type ADD VALUE IF NOT EXISTS 'CREDIT_CARD';

-- 3) telegram_conversation_state: el codigo usa IDLE y CONVERSATION.
ALTER TYPE telegram_conversation_state ADD VALUE IF NOT EXISTS 'CONVERSATION';

COMMIT;

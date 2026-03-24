package com.financebot.enums;

/**
 * Estado general del chat para Telegram.
 * <p>
 * La BD corporativa no incluye el literal {@code CONVERSATION}; por compatibilidad usamos
 * {@code WAITING_EXPENSE_AMOUNT} como marcador técnico de "hay conversación activa".
 */
public enum TelegramConversationState {
    IDLE,
    WAITING_EXPENSE_AMOUNT;

    /**
     * Alias semántico usado por el código existente para indicar flujo activo.
     * Se persiste como WAITING_EXPENSE_AMOUNT (valor válido en la BD actual).
     */
    public static final TelegramConversationState CONVERSATION = WAITING_EXPENSE_AMOUNT;
}

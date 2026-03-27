package com.financebot.enums;

/**
 * Estado del chat en Telegram. Los literales coinciden con el enum PostgreSQL {@code telegram_conversation_state}.
 * <p>
 * {@link #CONVERSATION} es un alias de {@link #ESPERANDO_MONTO_GASTO} para marcar flujo activo.
 */
public enum TelegramConversationState {
    INACTIVO,
    ESPERANDO_MONTO_GASTO;

    public static final TelegramConversationState CONVERSATION = ESPERANDO_MONTO_GASTO;
}

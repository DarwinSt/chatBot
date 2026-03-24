package com.financebot.enums;

/**
 * {@code CONVERSATION} indica un flujo multi-paso; el paso concreto vive en {@code contextData} (JSON).
 */
public enum TelegramConversationState {
    IDLE,
    CONVERSATION
}

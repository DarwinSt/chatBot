package com.financebot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request mínimo para {@code sendMessage} de la Bot API.
 */
public record TelegramSendMessageRequest(
        @JsonProperty("chat_id") String chatId,
        String text
) {
}

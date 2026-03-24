package com.financebot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChatDto(
        Long id,
        String type,
        String title,
        String username,
        String first_name,
        String last_name
) {
}

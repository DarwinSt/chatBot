package com.financebot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateDto(
        Long update_id,
        TelegramMessageDto message
) {
}

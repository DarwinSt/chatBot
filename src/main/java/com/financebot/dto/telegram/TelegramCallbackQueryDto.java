package com.financebot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackQueryDto(
        String id,
        TelegramUserDto from,
        TelegramMessageDto message,
        String data
) {
}

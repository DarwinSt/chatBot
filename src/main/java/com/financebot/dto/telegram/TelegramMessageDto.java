package com.financebot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessageDto(
        Integer message_id,
        TelegramUserDto from,
        TelegramChatDto chat,
        Long date,
        String text
) {
}

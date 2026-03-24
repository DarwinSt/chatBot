package com.financebot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUserDto(
        Long id,
        Boolean is_bot,
        String first_name,
        String last_name,
        String username
) {
}

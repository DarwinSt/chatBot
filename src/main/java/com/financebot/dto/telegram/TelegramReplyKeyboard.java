package com.financebot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Reply keyboard simple para botones persistentes en Telegram.
 */
public record TelegramReplyKeyboard(
        List<List<TelegramKeyboardButton>> keyboard,
        @JsonProperty("resize_keyboard") boolean resizeKeyboard,
        @JsonProperty("one_time_keyboard") boolean oneTimeKeyboard,
        @JsonProperty("input_field_placeholder") String inputFieldPlaceholder
) {
}

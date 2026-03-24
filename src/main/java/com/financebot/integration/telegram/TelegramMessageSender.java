package com.financebot.integration.telegram;

import org.springframework.stereotype.Component;

/**
 * Punto único para enviar texto al usuario vía Telegram (adaptador de salida).
 */
@Component
public class TelegramMessageSender {

    private final TelegramApiClient telegramApiClient;

    public TelegramMessageSender(TelegramApiClient telegramApiClient) {
        this.telegramApiClient = telegramApiClient;
    }

    public void sendText(String chatId, String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.length() > 4000 ? text.substring(0, 3997) + "..." : text;
        telegramApiClient.sendMessage(chatId, trimmed);
    }
}

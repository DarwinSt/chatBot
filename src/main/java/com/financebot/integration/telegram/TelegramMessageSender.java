package com.financebot.integration.telegram;

import org.springframework.stereotype.Component;

import java.util.Map;

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
        sendText(chatId, text, null);
    }

    public void sendText(String chatId, String text, Object replyMarkup) {
        if (text == null) {
            return;
        }
        String trimmed = text.length() > 4000 ? text.substring(0, 3997) + "..." : text;
        telegramApiClient.sendMessage(chatId, trimmed, replyMarkup);
    }

    public void answerCallback(String callbackQueryId) {
        telegramApiClient.answerCallbackQuery(callbackQueryId);
    }

    public void registerWebhook(String webhookUrl, String webhookSecret) {
        telegramApiClient.setWebhook(webhookUrl, webhookSecret);
    }

    public Map<String, Object> getWebhookInfo() {
        return telegramApiClient.getWebhookInfo();
    }
}

package com.financebot.service.telegram;

import com.financebot.config.TelegramProperties;
import org.springframework.stereotype.Component;

/**
 * Valida el header {@code X-Telegram-Bot-Api-Secret-Token} si {@code TELEGRAM_WEBHOOK_SECRET} está definido.
 */
@Component
public class TelegramWebhookSecretValidator {

    private final TelegramProperties telegramProperties;

    public TelegramWebhookSecretValidator(TelegramProperties telegramProperties) {
        this.telegramProperties = telegramProperties;
    }

    public boolean isValid(String headerValue) {
        String expected = telegramProperties.webhookSecret();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(headerValue);
    }
}

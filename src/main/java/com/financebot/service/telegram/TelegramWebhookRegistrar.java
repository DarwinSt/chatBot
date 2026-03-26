package com.financebot.service.telegram;

import com.financebot.config.TelegramProperties;
import com.financebot.integration.telegram.TelegramMessageSender;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component
public class TelegramWebhookRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramMessageSender messageSender;
    private final TelegramProperties telegramProperties;
    private final String appBaseUrl;

    public TelegramWebhookRegistrar(
            TelegramMessageSender messageSender,
            TelegramProperties telegramProperties,
            @Value("${app.base-url:}") String appBaseUrl) {
        this.messageSender = messageSender;
        this.telegramProperties = telegramProperties;
        this.appBaseUrl = appBaseUrl;
    }

    @PostConstruct
    public void registerOnStartup() {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return;
        }
        String base = appBaseUrl.trim();
        String webhookUrl = base.replaceAll("/+$", "") + "/api/telegram/webhook";
        if (!base.toLowerCase().startsWith("https://")) {
            log.warn(
                    "No se registra webhook automático: app.base-url debe ser HTTPS (Telegram lo exige). Valor actual: {}. "
                            + "Ejemplo local: APP_BASE_URL=https://TU-TUNEL.ngrok-free.app "
                            + "Tras eso, reinicia y los botones inline recibirán callback_query.",
                    base
            );
        } else {
            messageSender.registerWebhook(webhookUrl, telegramProperties.webhookSecret());
        }
        Map<String, Object> info = messageSender.getWebhookInfo();
        Object result = info.get("result");
        if (result instanceof Map<?, ?> r) {
            log.info(
                    "Telegram webhook activo: url={}, allowed_updates={}, last_error_message={}",
                    r.get("url"),
                    r.get("allowed_updates"),
                    r.get("last_error_message")
            );
        } else {
            log.info("Telegram getWebhookInfo result no disponible: {}", info);
        }
    }
}

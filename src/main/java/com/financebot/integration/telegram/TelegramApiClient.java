package com.financebot.integration.telegram;

import com.financebot.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP mínimo para la Bot API de Telegram ({@code sendMessage}).
 */
@Component
public class TelegramApiClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiClient.class);

    private final RestClient restClient;

    public TelegramApiClient(TelegramProperties properties) {
        String token = properties.botToken();
        if (token == null || token.isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN no configurado: no se enviarán mensajes a Telegram.");
            this.restClient = null;
        } else {
            String baseUrl = "https://api.telegram.org/bot" + token + "/";
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            // Evita que la llamada a Telegram (especialmente answerCallbackQuery) bloquee y cause timeouts.
            requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
            requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
            this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        }
    }

    public boolean isConfigured() {
        return restClient != null;
    }

    public void sendMessage(String chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void sendMessage(String chatId, String text, Object replyMarkup) {
        if (restClient == null) {
            log.warn("Omitiendo sendMessage: TELEGRAM_BOT_TOKEN no configurado. chatId={}, text={}", chatId, text);
            return;
        }
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            if (replyMarkup != null) {
                payload.put("reply_markup", replyMarkup);
            }
            restClient.post()
                    .uri("sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.error(
                    "Telegram API respondió error en sendMessage. status={}, body={}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );
        } catch (RestClientException ex) {
            log.error("Error al llamar a Telegram sendMessage: {}", ex.getMessage());
        }
    }

    public void answerCallbackQuery(String callbackQueryId) {
        if (restClient == null) {
            log.warn("Omitiendo answerCallbackQuery: TELEGRAM_BOT_TOKEN no configurado. callbackQueryId={}", callbackQueryId);
            return;
        }
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }
        try {
            log.info("answerCallbackQuery callback_query_id={}", callbackQueryId);
            restClient.post()
                    .uri("answerCallbackQuery")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("callback_query_id", callbackQueryId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("answerCallbackQuery OK callback_query_id={}", callbackQueryId);
        } catch (RestClientResponseException ex) {
            log.error(
                    "Telegram API respondió error en answerCallbackQuery. status={}, body={}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );
        } catch (RestClientException ex) {
            log.error("Error al llamar a Telegram answerCallbackQuery: {}", ex.getMessage());
        }
    }

    public void setWebhook(String webhookUrl, String webhookSecret) {
        if (restClient == null) {
            log.warn("Omitiendo setWebhook: TELEGRAM_BOT_TOKEN no configurado.");
            return;
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Omitiendo setWebhook: webhookUrl vacío.");
            return;
        }
        String trimmed = webhookUrl.trim();
        if (!trimmed.toLowerCase().startsWith("https://")) {
            log.warn(
                    "Omitiendo setWebhook: Telegram exige HTTPS. URL recibida: {}. "
                            + "Define APP_BASE_URL con una URL pública https (p. ej. túnel ngrok/cloudflare) o despliega en servidor con certificado.",
                    trimmed
            );
            return;
        }
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("url", webhookUrl);
            payload.put("allowed_updates", List.of("message", "callback_query"));
            if (webhookSecret != null && !webhookSecret.isBlank()) {
                payload.put("secret_token", webhookSecret);
            }
            restClient.post()
                    .uri("setWebhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook de Telegram registrado en {}", webhookUrl);
        } catch (RestClientResponseException ex) {
            log.error(
                    "Telegram API respondió error en setWebhook. status={}, body={}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );
        } catch (RestClientException ex) {
            log.error("Error al llamar a Telegram setWebhook: {}", ex.getMessage());
        }
    }

    public Map<String, Object> getWebhookInfo() {
        if (restClient == null) {
            return Map.of();
        }
        try {
            // Telegram devuelve { "ok": true, "result": {...} }
            Object resp = restClient.post()
                    .uri("getWebhookInfo")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(Object.class);
            if (resp instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
        } catch (RestClientResponseException ex) {
            log.error("Telegram API respondió error en getWebhookInfo. status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            log.error("Error al llamar a Telegram getWebhookInfo: {}", ex.getMessage());
        }
        return Map.of();
    }
}

package com.financebot.integration.telegram;

import com.financebot.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
            this.restClient = RestClient.builder().baseUrl(baseUrl).build();
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
}

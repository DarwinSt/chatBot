package com.financebot.integration.telegram;

import com.financebot.config.TelegramProperties;
import com.financebot.dto.telegram.TelegramSendMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        if (restClient == null) {
            log.debug("Omitiendo sendMessage (cliente no configurado): {}", text);
            return;
        }
        try {
            restClient.post()
                    .uri("sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TelegramSendMessageRequest(chatId, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Error al llamar a Telegram sendMessage: {}", ex.getMessage());
        }
    }
}

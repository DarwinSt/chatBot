package com.financebot.controller;

import com.financebot.dto.telegram.TelegramUpdateDto;
import com.financebot.service.telegram.TelegramUpdateService;
import com.financebot.service.telegram.TelegramWebhookSecretValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de Telegram. Solo recibe el update y delega; sin lógica de negocio financiera.
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private final TelegramWebhookSecretValidator secretValidator;
    private final TelegramUpdateService telegramUpdateService;

    public TelegramWebhookController(
            TelegramWebhookSecretValidator secretValidator,
            TelegramUpdateService telegramUpdateService) {
        this.secretValidator = secretValidator;
        this.telegramUpdateService = telegramUpdateService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody(required = false) TelegramUpdateDto update,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken) {
        if (!secretValidator.isValid(secretToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (update == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        telegramUpdateService.process(update);
        return ResponseEntity.ok().build();
    }
}

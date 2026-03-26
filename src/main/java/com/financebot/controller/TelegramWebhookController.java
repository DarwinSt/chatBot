package com.financebot.controller;

import com.financebot.dto.telegram.TelegramUpdateDto;
import com.financebot.integration.telegram.TelegramMessageSender;
import com.financebot.service.telegram.TelegramUpdateService;
import com.financebot.service.telegram.TelegramWebhookSecretValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * Webhook de Telegram. Solo recibe el update y delega; sin lógica de negocio financiera.
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramWebhookSecretValidator secretValidator;
    private final TelegramUpdateService telegramUpdateService;
    private final TelegramMessageSender messageSender;
    private final TaskExecutor taskExecutor;

    public TelegramWebhookController(
            TelegramWebhookSecretValidator secretValidator,
            TelegramUpdateService telegramUpdateService,
            TelegramMessageSender messageSender,
            TaskExecutor taskExecutor) {
        this.secretValidator = secretValidator;
        this.telegramUpdateService = telegramUpdateService;
        this.messageSender = messageSender;
        this.taskExecutor = taskExecutor;
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
        if (update.callback_query() != null
                && update.callback_query().id() != null
                && !update.callback_query().id().isBlank()) {
            // Responde el callback en background para evitar cualquier latencia en el webhook.
            String callbackId = update.callback_query().id();
            log.info("Telegram callback_query recibido. id={}", callbackId);
            if (update.callback_query().data() != null) {
                log.info("Telegram callback_query data={}", update.callback_query().data());
            }
            CompletableFuture.runAsync(() -> messageSender.answerCallback(callbackId));
        }
        taskExecutor.execute(() -> {
            try {
                telegramUpdateService.process(update);
            } catch (Exception ex) {
                log.error("Error procesando update de Telegram en background: {}", ex.getMessage(), ex);
            }
        });
        return ResponseEntity.ok().build();
    }
}

package com.financebot.service.telegram;

import com.financebot.dto.telegram.TelegramUpdateDto;
import com.financebot.entity.TelegramChatSession;
import com.financebot.enums.TelegramConversationState;
import com.financebot.integration.telegram.TelegramMessageSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el flujo Telegram → sesión → comandos/conversación → envío de respuesta.
 */
@Service
public class TelegramUpdateService {

    private final TelegramSessionService sessionService;
    private final TelegramCommandRouter commandRouter;
    private final TelegramConversationService conversationService;
    private final TelegramMessageSender messageSender;

    public TelegramUpdateService(
            TelegramSessionService sessionService,
            TelegramCommandRouter commandRouter,
            TelegramConversationService conversationService,
            TelegramMessageSender messageSender) {
        this.sessionService = sessionService;
        this.commandRouter = commandRouter;
        this.conversationService = conversationService;
        this.messageSender = messageSender;
    }

    @Transactional
    public void process(TelegramUpdateDto update) {
        if (update == null || update.message() == null) {
            return;
        }
        String text = update.message().text();
        if (text == null || text.isBlank()) {
            return;
        }
        String chatId = String.valueOf(update.message().chat().id());

        TelegramChatSession session = sessionService.getOrCreate(chatId);
        String trimmed = text.trim();

        if (trimmed.startsWith("/")) {
            boolean handled = commandRouter.dispatch(chatId, session, trimmed);
            if (!handled) {
                messageSender.sendText(chatId, "Comando vacío o inválido.");
            }
            return;
        }

        TelegramChatSession latest = sessionService.getOrCreate(chatId);
        if (latest.getCurrentState() == TelegramConversationState.CONVERSATION) {
            conversationService.handleUserInput(chatId, latest, trimmed);
        } else {
            messageSender.sendText(chatId, "Escribe /ayuda para ver comandos. Usa / para iniciar.");
        }
    }
}

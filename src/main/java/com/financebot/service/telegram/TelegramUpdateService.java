package com.financebot.service.telegram;

import com.financebot.dto.telegram.TelegramUpdateDto;
import com.financebot.entity.TelegramChatSession;
import com.financebot.enums.TelegramConversationState;
import com.financebot.integration.telegram.TelegramMessageSender;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquesta el flujo Telegram → sesión → comandos/conversación → envío de respuesta.
 */
@Service
public class TelegramUpdateService {

    private final TelegramSessionService sessionService;
    private final TelegramCommandRouter commandRouter;
    private final TelegramConversationService conversationService;
    private final TelegramMessageSender messageSender;
    private final Map<Long, Long> processedUpdates = new ConcurrentHashMap<>();

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

    public void process(TelegramUpdateDto update) {
        if (update == null) {
            return;
        }
        if (isDuplicateUpdate(update.update_id())) {
            return;
        }
        if (update.callback_query() != null) {
            if (update.callback_query().message() == null
                    || update.callback_query().message().chat() == null) {
                return;
            }
            String chatId = String.valueOf(update.callback_query().message().chat().id());
            TelegramChatSession session = sessionService.getOrCreate(chatId);
            String data = update.callback_query().data();
            if (data == null || data.isBlank()) {
                commandRouter.sendMainMenu(chatId);
                return;
            }
            if (commandRouter.dispatchCallbackAction(chatId, session, data)) {
                return;
            }
            conversationService.handleUserInput(chatId, session, data);
            return;
        }
        if (update.message() == null) {
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
        boolean hasFlow = sessionService.hasActiveFlow(latest);
        boolean hasPendingState = latest.getPendingCommand() != null || latest.getContextData() != null;
        if (latest.getCurrentState() == TelegramConversationState.CONVERSATION || hasFlow || hasPendingState) {
            conversationService.handleUserInput(chatId, latest, trimmed);
        } else {
            commandRouter.sendMainMenu(chatId);
        }
    }

    private boolean isDuplicateUpdate(Long updateId) {
        if (updateId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long ttlMs = 5 * 60 * 1000L;
        Iterator<Map.Entry<Long, Long>> it = processedUpdates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (now - e.getValue() > ttlMs) {
                it.remove();
            }
        }
        Long previous = processedUpdates.putIfAbsent(updateId, now);
        return previous != null;
    }
}

package com.financebot.service.telegram;

import com.financebot.entity.TelegramChatSession;
import com.financebot.enums.TelegramConversationState;
import com.financebot.integration.telegram.context.TelegramContextPayload;
import com.financebot.repository.TelegramChatSessionRepository;
import com.financebot.util.TelegramContextMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramSessionService {

    private final TelegramChatSessionRepository repository;
    private final TelegramContextMapper contextMapper;

    public TelegramSessionService(
            TelegramChatSessionRepository repository,
            TelegramContextMapper contextMapper) {
        this.repository = repository;
        this.contextMapper = contextMapper;
    }

    @Transactional
    public TelegramChatSession getOrCreate(String chatId) {
        Long chatIdValue = parseChatId(chatId);
        return repository.findByChatId(chatIdValue).orElseGet(() -> {
            TelegramChatSession session = new TelegramChatSession();
            session.setChatId(chatIdValue);
            session.setCurrentState(TelegramConversationState.IDLE);
            session.setActive(true);
            return repository.save(session);
        });
    }

    private Long parseChatId(String chatId) {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("chatId inválido para persistencia: " + chatId, e);
        }
    }

    public TelegramContextPayload readPayload(TelegramChatSession session) {
        return contextMapper.fromJson(session.getContextData());
    }

    @Transactional
    public void save(TelegramChatSession session, TelegramConversationState state, String pendingCommand, TelegramContextPayload payload) {
        session.setCurrentState(state);
        session.setPendingCommand(pendingCommand);
        session.setContextData(contextMapper.toJson(payload));
        repository.save(session);
    }

    @Transactional
    public void resetToIdle(TelegramChatSession session) {
        session.setCurrentState(TelegramConversationState.IDLE);
        session.setPendingCommand(null);
        session.setContextData(null);
        repository.save(session);
    }
}

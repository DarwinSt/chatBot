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
            session.setCurrentState(TelegramConversationState.INACTIVO);
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

    public boolean hasActiveFlow(TelegramChatSession session) {
        TelegramContextPayload payload = readPayload(session);
        return payload.flow != null && !payload.flow.isBlank()
                && payload.step != null && !payload.step.isBlank();
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
        session.setCurrentState(TelegramConversationState.INACTIVO);
        session.setPendingCommand(null);
        session.setContextData(null);
        repository.save(session);
    }
}

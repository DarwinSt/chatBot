package com.financebot.repository;

import com.financebot.entity.TelegramChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramChatSessionRepository extends JpaRepository<TelegramChatSession, Long> {
    Optional<TelegramChatSession> findByChatId(String chatId);
}

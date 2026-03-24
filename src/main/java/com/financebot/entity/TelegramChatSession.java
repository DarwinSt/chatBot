package com.financebot.entity;

import com.financebot.enums.TelegramConversationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "telegram_chat_sessions")
public class TelegramChatSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "chat_id", nullable = false, unique = true, length = 100)
    private String chatId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 40)
    private TelegramConversationState currentState;

    @Column(name = "pending_command", length = 100)
    private String pendingCommand;

    @Column(name = "context_data", columnDefinition = "TEXT")
    private String contextData;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public TelegramConversationState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(TelegramConversationState currentState) {
        this.currentState = currentState;
    }

    public String getPendingCommand() {
        return pendingCommand;
    }

    public void setPendingCommand(String pendingCommand) {
        this.pendingCommand = pendingCommand;
    }

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

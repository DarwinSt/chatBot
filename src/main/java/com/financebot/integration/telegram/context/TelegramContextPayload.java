package com.financebot.integration.telegram.context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datos temporales del flujo conversacional (serializado como JSON en {@code TelegramChatSession.contextData}).
 */
public class TelegramContextPayload {

    public String flow;
    public String step;
    public Map<String, String> fields = new LinkedHashMap<>();
}

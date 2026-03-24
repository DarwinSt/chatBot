package com.financebot.util;

import com.financebot.integration.telegram.context.TelegramContextPayload;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TelegramContextMapper {

    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    public TelegramContextPayload fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new TelegramContextPayload();
        }
        try {
            Map<String, Object> root = jsonParser.parseMap(json);
            TelegramContextPayload payload = new TelegramContextPayload();
            Object flow = root.get("flow");
            Object step = root.get("step");
            payload.flow = flow instanceof String ? (String) flow : null;
            payload.step = step instanceof String ? (String) step : null;
            Object fields = root.get("fields");
            if (fields instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        payload.fields.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            }
            return payload;
        } catch (Exception e) {
            return new TelegramContextPayload();
        }
    }

    public String toJson(TelegramContextPayload payload) {
        if (payload == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"flow\":").append(toJsonString(payload.flow)).append(",");
        sb.append("\"step\":").append(toJsonString(payload.step)).append(",");
        sb.append("\"fields\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.fields.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(toJsonString(entry.getKey()))
                    .append(":")
                    .append(toJsonString(entry.getValue()));
        }
        sb.append("}}");
        return sb.toString();
    }

    private String toJsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

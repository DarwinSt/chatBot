package com.financebot.service.telegram;

import com.financebot.entity.TelegramChatSession;
import com.financebot.enums.TelegramConversationState;
import com.financebot.integration.telegram.TelegramMessageSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

/**
 * Enruta comandos de texto; no contiene reglas financieras (solo orquestación Telegram).
 */
@Service
public class TelegramCommandRouter {

    private static final Set<String> FLOW_STARTERS = Set.of(
            "/registrar_gasto",
            "/registrar_ingreso",
            "/registrar_deuda",
            "/registrar_consumo_tarjeta",
            "/pagar_tarjeta",
            "/abonar_deuda",
            "/transferir"
    );

    private final TelegramSessionService sessionService;
    private final TelegramMessageSender messageSender;
    private final TelegramConversationService conversationService;
    private final TelegramQuickReplyService quickReplyService;

    public TelegramCommandRouter(
            TelegramSessionService sessionService,
            TelegramMessageSender messageSender,
            TelegramConversationService conversationService,
            TelegramQuickReplyService quickReplyService) {
        this.sessionService = sessionService;
        this.messageSender = messageSender;
        this.conversationService = conversationService;
        this.quickReplyService = quickReplyService;
    }

    /**
     * @return true si el mensaje fue manejado como comando (no debe seguir a entrada libre de conversación).
     */
    @Transactional
    public boolean dispatch(String chatId, TelegramChatSession session, String rawText) {
        String normalized = normalizeCommandToken(rawText);
        if (normalized.isEmpty()) {
            return false;
        }

        if ("/ayuda".equals(normalized)) {
            messageSender.sendText(chatId, helpText());
            return true;
        }

        if ("/cancelar".equals(normalized)) {
            if (session.getCurrentState() == TelegramConversationState.CONVERSATION) {
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Conversación cancelada. Estado: IDLE.");
            } else {
                messageSender.sendText(chatId, "No había una conversación activa.");
            }
            return true;
        }

        if ("/start".equals(normalized)) {
            messageSender.sendText(chatId, welcomeText());
            return true;
        }

        boolean inConversation = session.getCurrentState() == TelegramConversationState.CONVERSATION;
        if (inConversation) {
            sessionService.resetToIdle(session);
            if (FLOW_STARTERS.contains(normalized)) {
                messageSender.sendText(chatId, "Se canceló la conversación anterior para iniciar: " + normalized);
            }
        }

        TelegramChatSession fresh = sessionService.getOrCreate(chatId);

        boolean handled = switch (normalized) {
            case "/registrar_gasto" -> {
                conversationService.beginExpenseFlow(chatId, fresh, normalized);
                yield true;
            }
            case "/registrar_ingreso" -> {
                conversationService.beginIncomeFlow(chatId, fresh, normalized);
                yield true;
            }
            case "/registrar_deuda" -> {
                conversationService.beginDebtRegisterFlow(chatId, fresh, normalized);
                yield true;
            }
            case "/registrar_consumo_tarjeta" -> {
                conversationService.beginCardConsumptionFlow(chatId, fresh, normalized);
                yield true;
            }
            case "/pagar_tarjeta" -> {
                conversationService.beginCardPaymentFlow(chatId, fresh, normalized);
                yield true;
            }
            case "/abonar_deuda" -> {
                conversationService.beginDebtPaymentFlow(chatId, fresh, normalized);
                yield true;
            }
            case "/transferir" -> {
                conversationService.beginTransferFlow(chatId, fresh, normalized);
                yield true;
            }
            case "/ver_cuentas" -> {
                messageSender.sendText(chatId, quickReplyService.verCuentas());
                yield true;
            }
            case "/ver_deudas" -> {
                messageSender.sendText(chatId, quickReplyService.verDeudas());
                yield true;
            }
            case "/ver_tarjetas" -> {
                messageSender.sendText(chatId, quickReplyService.verTarjetas());
                yield true;
            }
            case "/resumen_mes" -> {
                messageSender.sendText(chatId, quickReplyService.resumenMes());
                yield true;
            }
            case "/balance" -> {
                messageSender.sendText(chatId, quickReplyService.balance());
                yield true;
            }
            default -> false;
        };

        if (!handled) {
            messageSender.sendText(chatId, "Comando no reconocido. Usa /ayuda.");
        }
        return true;
    }

    private static String normalizeCommandToken(String rawText) {
        String trimmed = rawText.trim();
        int space = trimmed.indexOf(' ');
        String first = space == -1 ? trimmed : trimmed.substring(0, space);
        first = first.toLowerCase(Locale.ROOT);
        if (first.contains("@")) {
            first = first.substring(0, first.indexOf('@'));
        }
        return first;
    }

    private static String welcomeText() {
        return """
                ¡Hola! Soy tu asistente financiero personal.
                Usa /ayuda para ver los comandos disponibles.
                """.trim();
    }

    private static String helpText() {
        return """
                Comandos:
                /start — bienvenida
                /ayuda — esta ayuda
                /cancelar — cancela el flujo activo

                Registros (guiados):
                /registrar_ingreso
                /registrar_gasto
                /registrar_deuda
                /registrar_consumo_tarjeta
                /pagar_tarjeta
                /abonar_deuda
                /transferir

                Consultas:
                /ver_cuentas
                /ver_deudas
                /ver_tarjetas
                /resumen_mes
                /balance
                """.trim();
    }
}

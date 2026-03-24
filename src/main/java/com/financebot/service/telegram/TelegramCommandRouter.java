package com.financebot.service.telegram;

import com.financebot.dto.request.AccountCreateRequest;
import com.financebot.dto.request.CreditCardCreateRequest;
import com.financebot.dto.response.AccountResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.enums.AccountType;
import com.financebot.entity.TelegramChatSession;
import com.financebot.enums.TelegramConversationState;
import com.financebot.integration.telegram.TelegramMessageSender;
import com.financebot.service.AccountService;
import com.financebot.service.CreditCardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
    private final AccountService accountService;
    private final CreditCardService creditCardService;

    public TelegramCommandRouter(
            TelegramSessionService sessionService,
            TelegramMessageSender messageSender,
            TelegramConversationService conversationService,
            TelegramQuickReplyService quickReplyService,
            AccountService accountService,
            CreditCardService creditCardService) {
        this.sessionService = sessionService;
        this.messageSender = messageSender;
        this.conversationService = conversationService;
        this.quickReplyService = quickReplyService;
        this.accountService = accountService;
        this.creditCardService = creditCardService;
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

        if ("/ayuda".equals(normalized) || "/menu".equals(normalized)) {
            sendMainMenu(chatId);
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
            sendMainMenu(chatId);
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
            case "/registrar_gasto", "/nuevo_gasto" -> {
                conversationService.beginExpenseFlow(chatId, fresh, normalized);
                yield true;
            }
            case "➕ registrar gasto" -> {
                conversationService.beginExpenseFlow(chatId, fresh, "/registrar_gasto");
                yield true;
            }
            case "/cuentas" -> {
                messageSender.sendText(chatId, quickReplyService.verCuentas());
                yield true;
            }
            case "/cuenta_crear" -> {
                if (rawText.contains("|")) {
                    handleAccountCreate(chatId, rawText);
                } else {
                    conversationService.beginAccountCreateFlow(chatId, fresh, "/cuenta_crear");
                }
                yield true;
            }
            case "/cuenta_editar" -> {
                if (rawText.contains("|")) {
                    handleAccountUpdate(chatId, rawText);
                } else {
                    conversationService.beginAccountEditFlow(chatId, fresh, "/cuenta_editar");
                }
                yield true;
            }
            case "/cuenta_eliminar" -> {
                if (rawText.contains("|")) {
                    handleAccountDelete(chatId, rawText);
                } else {
                    conversationService.beginAccountDeleteFlow(chatId, fresh, "/cuenta_eliminar");
                }
                yield true;
            }
            case "/tarjetas" -> {
                messageSender.sendText(chatId, quickReplyService.verTarjetas());
                yield true;
            }
            case "/tarjeta_crear" -> {
                if (rawText.contains("|")) {
                    handleCardCreate(chatId, rawText);
                } else {
                    conversationService.beginCardCreateFlow(chatId, fresh, "/tarjeta_crear");
                }
                yield true;
            }
            case "/tarjeta_editar" -> {
                if (rawText.contains("|")) {
                    handleCardUpdate(chatId, rawText);
                } else {
                    conversationService.beginCardEditFlow(chatId, fresh, "/tarjeta_editar");
                }
                yield true;
            }
            case "/tarjeta_eliminar" -> {
                if (rawText.contains("|")) {
                    handleCardDelete(chatId, rawText);
                } else {
                    conversationService.beginCardDeleteFlow(chatId, fresh, "/tarjeta_eliminar");
                }
                yield true;
            }
            case "/registrar_ingreso", "/nuevo_ingreso" -> {
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
            case "📊 resumen", "📅 hoy", "🗓️ esta semana", "📆 este mes" -> {
                messageSender.sendText(chatId, quickReplyService.resumenMes());
                yield true;
            }
            case "📈 por categorías" -> {
                messageSender.sendText(chatId, "Pronto: reporte por categorías detallado.");
                yield true;
            }
            case "📂 categorías", "👀 ver categorías", "➕ agregar categoría", "✏️ editar categoría", "🗑️ eliminar categoría" -> {
                messageSender.sendText(chatId, "Módulo de categorías en construcción. Usa API REST por ahora.");
                yield true;
            }
            case "🎯 presupuesto", "⚙️ ajustes" -> {
                messageSender.sendText(chatId, "Módulo en construcción. Usa /menu para continuar.");
                yield true;
            }
            case "🏠 inicio" -> {
                messageSender.sendText(chatId, welcomeText());
                sendMainMenu(chatId);
                yield true;
            }
            case "⬅️ volver" -> {
                sendMainMenu(chatId);
                yield true;
            }
            case "❌ cancelar" -> {
                sessionService.resetToIdle(fresh);
                messageSender.sendText(chatId, "Conversación cancelada.");
                sendMainMenu(chatId);
                yield true;
            }
            case "/balance" -> {
                messageSender.sendText(chatId, quickReplyService.balance());
                yield true;
            }
            default -> false;
        };

        if (!handled) {
            messageSender.sendText(chatId, "Comando no reconocido. Usa /menu.");
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
                Te ayudo a registrar movimientos y consultar tu resumen.
                """.trim();
    }

    private static String menuText() {
        return """
                MENÚ PRINCIPAL
                1) Registros
                - /registrar_gasto
                - /registrar_ingreso
                - /registrar_deuda
                - /registrar_consumo_tarjeta
                - /pagar_tarjeta
                - /abonar_deuda
                - /transferir

                2) Cuentas (CRUD)
                - /cuentas
                - /cuenta_crear  (wizard paso a paso)
                - /cuenta_editar  (wizard paso a paso)
                - /cuenta_eliminar  (wizard con confirmación)

                3) Tarjetas (CRUD)
                - /tarjetas
                - /tarjeta_crear  (wizard paso a paso)
                - /tarjeta_editar  (wizard paso a paso)
                - /tarjeta_eliminar  (wizard con confirmación)

                4) Consultas
                - /ver_cuentas
                - /ver_deudas
                - /ver_tarjetas
                - /resumen_mes
                - /balance

                Atajos:
                - /menu
                - /ayuda
                - /cancelar
                """.trim();
    }

    void sendMainMenu(String chatId) {
        messageSender.sendText(chatId, menuText());
    }

    private void handleAccountCreate(String chatId, String rawText) {
        String[] parts = parsePipeArgs(rawText, 4);
        if (parts == null) {
            messageSender.sendText(chatId, "Uso: /cuenta_crear nombre|tipo|saldo_inicial|notas");
            return;
        }
        try {
            AccountType type = AccountType.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            BigDecimal initial = new BigDecimal(parts[2].trim());
            AccountResponse created = accountService.create(new AccountCreateRequest(
                    parts[0].trim(), type, initial, emptyToNull(parts[3]), true));
            messageSender.sendText(chatId, "Cuenta creada: " + created.name() + " (id " + created.id() + ")");
        } catch (Exception e) {
            messageSender.sendText(chatId, "No se pudo crear la cuenta. Verifica tipo (CHECKING/SAVINGS/CASH/DIGITAL_WALLET) y saldo.");
        }
    }

    private void handleAccountUpdate(String chatId, String rawText) {
        String[] parts = parsePipeArgs(rawText, 5);
        if (parts == null) {
            messageSender.sendText(chatId, "Uso: /cuenta_editar id|nombre|tipo|activa(1/0)|notas");
            return;
        }
        try {
            Long id = Long.parseLong(parts[0].trim());
            AccountType type = AccountType.valueOf(parts[2].trim().toUpperCase(Locale.ROOT));
            boolean active = "1".equals(parts[3].trim()) || "true".equalsIgnoreCase(parts[3].trim());
            AccountResponse updated = accountService.updateBasic(id, parts[1].trim(), type, emptyToNull(parts[4]), active);
            messageSender.sendText(chatId, "Cuenta actualizada: " + updated.name());
        } catch (Exception e) {
            messageSender.sendText(chatId, "No se pudo editar la cuenta. Verifica formato y valores.");
        }
    }

    private void handleAccountDelete(String chatId, String rawText) {
        String[] parts = parsePipeArgs(rawText, 1);
        if (parts == null) {
            messageSender.sendText(chatId, "Uso: /cuenta_eliminar id");
            return;
        }
        try {
            accountService.deactivate(Long.parseLong(parts[0].trim()));
            messageSender.sendText(chatId, "Cuenta desactivada correctamente.");
        } catch (Exception e) {
            messageSender.sendText(chatId, "No se pudo eliminar/desactivar la cuenta.");
        }
    }

    private void handleCardCreate(String chatId, String rawText) {
        String[] parts = parsePipeArgs(rawText, 6);
        if (parts == null) {
            messageSender.sendText(chatId, "Uso: /tarjeta_crear nombre|cupo_total|usado|corte|vencimiento|notas");
            return;
        }
        try {
            CreditCardResponse created = creditCardService.create(new CreditCardCreateRequest(
                    parts[0].trim(),
                    new BigDecimal(parts[1].trim()),
                    new BigDecimal(parts[2].trim()),
                    parseShortOrNull(parts[3]),
                    parseShortOrNull(parts[4]),
                    emptyToNull(parts[5])
            ));
            messageSender.sendText(chatId, "Tarjeta creada: " + created.name() + " (id " + created.id() + ")");
        } catch (Exception e) {
            messageSender.sendText(chatId, "No se pudo crear la tarjeta. Verifica formato numérico.");
        }
    }

    private void handleCardUpdate(String chatId, String rawText) {
        String[] parts = parsePipeArgs(rawText, 7);
        if (parts == null) {
            messageSender.sendText(chatId, "Uso: /tarjeta_editar id|nombre|cupo_total|corte|vencimiento|activa(1/0)|notas");
            return;
        }
        try {
            Long id = Long.parseLong(parts[0].trim());
            boolean active = "1".equals(parts[5].trim()) || "true".equalsIgnoreCase(parts[5].trim());
            CreditCardResponse updated = creditCardService.updateBasic(
                    id,
                    parts[1].trim(),
                    new BigDecimal(parts[2].trim()),
                    parseShortOrNull(parts[3]),
                    parseShortOrNull(parts[4]),
                    emptyToNull(parts[6]),
                    active
            );
            messageSender.sendText(chatId, "Tarjeta actualizada: " + updated.name());
        } catch (Exception e) {
            messageSender.sendText(chatId, "No se pudo editar la tarjeta. Verifica formato y valores.");
        }
    }

    private void handleCardDelete(String chatId, String rawText) {
        String[] parts = parsePipeArgs(rawText, 1);
        if (parts == null) {
            messageSender.sendText(chatId, "Uso: /tarjeta_eliminar id");
            return;
        }
        try {
            creditCardService.deactivate(Long.parseLong(parts[0].trim()));
            messageSender.sendText(chatId, "Tarjeta desactivada correctamente.");
        } catch (Exception e) {
            messageSender.sendText(chatId, "No se pudo eliminar/desactivar la tarjeta.");
        }
    }

    private static String[] parsePipeArgs(String rawText, int expectedParts) {
        int firstSpace = rawText.indexOf(' ');
        if (firstSpace < 0 || firstSpace == rawText.length() - 1) {
            return null;
        }
        String[] parts = rawText.substring(firstSpace + 1).split("\\|", -1);
        if (parts.length != expectedParts) {
            return null;
        }
        return parts;
    }

    private static String emptyToNull(String s) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() || "-".equals(t) ? null : t;
    }

    private static Short parseShortOrNull(String value) {
        String t = value == null ? "" : value.trim();
        if (t.isEmpty() || "-".equals(t)) {
            return null;
        }
        return Short.parseShort(t);
    }
}

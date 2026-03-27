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
import java.util.Map;
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
            "/transferir",
            "/cuenta_crear",
            "/cuenta_editar",
            "/cuenta_eliminar",
            "/tarjeta_crear",
            "/tarjeta_editar",
            "/tarjeta_eliminar",
            "/categoria_crear",
            "/categoria_editar",
            "/categoria_eliminar"
    );
    private static final Set<String> MENU_CALLBACK_ACTIONS = Set.of(
            "menu:main",
            "menu:movements",
            "menu:reports",
            "menu:accounts",
            "menu:cards",
            "menu:categories",
            "menu:cancel",
            "action:expense",
            "action:income",
            "action:debt_register",
            "action:card_consumption",
            "action:card_payment",
            "action:debt_payment",
            "action:transfer",
            "action:summary",
            "action:balance",
            "action:view_accounts",
            "action:view_cards",
            "action:view_debts",
            "action:account_create",
            "action:account_edit",
            "action:account_delete",
            "action:card_create",
            "action:card_edit",
            "action:card_delete",
            "action:category_create",
            "action:category_edit",
            "action:category_delete"
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
                messageSender.sendText(chatId, "Conversación cancelada. Estado: INACTIVO.");
            } else {
                messageSender.sendText(chatId, "No había una conversación activa.");
            }
            return true;
        }

        if ("/start".equals(normalized)) {
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
            case "/categorias" -> {
                sendCategoriesMenu(chatId);
                yield true;
            }
            case "/categoria_crear" -> {
                conversationService.beginCategoryCreateFlow(chatId, fresh, "/categoria_crear");
                yield true;
            }
            case "/categoria_editar" -> {
                conversationService.beginCategoryEditFlow(chatId, fresh, "/categoria_editar");
                yield true;
            }
            case "/categoria_eliminar" -> {
                conversationService.beginCategoryDeleteFlow(chatId, fresh, "/categoria_eliminar");
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

    @Transactional
    public boolean dispatchCallbackAction(String chatId, TelegramChatSession session, String callbackData) {
        String normalized = callbackData == null ? "" : callbackData.trim().toLowerCase(Locale.ROOT);
        if (!MENU_CALLBACK_ACTIONS.contains(normalized)) {
            return false;
        }
        if (session.getCurrentState() == TelegramConversationState.CONVERSATION
                && !"menu:cancel".equals(normalized)) {
            sessionService.resetToIdle(session);
        }
        return switch (normalized) {
            case "menu:main" -> {
                sendMainMenu(chatId);
                yield true;
            }
            case "menu:movements" -> {
                sendMovementsMenu(chatId);
                yield true;
            }
            case "menu:reports" -> {
                sendReportsMenu(chatId);
                yield true;
            }
            case "menu:accounts" -> {
                sendAccountsMenu(chatId);
                yield true;
            }
            case "menu:cards" -> {
                sendCardsMenu(chatId);
                yield true;
            }
            case "menu:categories" -> {
                sendCategoriesMenu(chatId);
                yield true;
            }
            case "menu:cancel" -> {
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Conversación cancelada.");
                sendMainMenu(chatId);
                yield true;
            }
            case "action:expense" -> dispatch(chatId, session, "/registrar_gasto");
            case "action:income" -> dispatch(chatId, session, "/registrar_ingreso");
            case "action:debt_register" -> dispatch(chatId, session, "/registrar_deuda");
            case "action:card_consumption" -> dispatch(chatId, session, "/registrar_consumo_tarjeta");
            case "action:card_payment" -> dispatch(chatId, session, "/pagar_tarjeta");
            case "action:debt_payment" -> dispatch(chatId, session, "/abonar_deuda");
            case "action:transfer" -> dispatch(chatId, session, "/transferir");
            case "action:summary" -> dispatch(chatId, session, "/resumen_mes");
            case "action:balance" -> dispatch(chatId, session, "/balance");
            case "action:view_accounts" -> dispatch(chatId, session, "/ver_cuentas");
            case "action:view_cards" -> dispatch(chatId, session, "/ver_tarjetas");
            case "action:view_debts" -> dispatch(chatId, session, "/ver_deudas");
            case "action:account_create" -> dispatch(chatId, session, "/cuenta_crear");
            case "action:account_edit" -> dispatch(chatId, session, "/cuenta_editar");
            case "action:account_delete" -> dispatch(chatId, session, "/cuenta_eliminar");
            case "action:card_create" -> dispatch(chatId, session, "/tarjeta_crear");
            case "action:card_edit" -> dispatch(chatId, session, "/tarjeta_editar");
            case "action:card_delete" -> dispatch(chatId, session, "/tarjeta_eliminar");
            case "action:category_create" -> dispatch(chatId, session, "/categoria_crear");
            case "action:category_edit" -> dispatch(chatId, session, "/categoria_editar");
            case "action:category_delete" -> dispatch(chatId, session, "/categoria_eliminar");
            default -> false;
        };
    }

    private static String normalizeCommandToken(String rawText) {
        String trimmed = rawText.trim();
        if (trimmed.startsWith("/")) {
            int space = trimmed.indexOf(' ');
            String first = space == -1 ? trimmed : trimmed.substring(0, space);
            first = first.toLowerCase(Locale.ROOT);
            if (first.contains("@")) {
                first = first.substring(0, first.indexOf('@'));
            }
            return first;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String menuText() {
        return """
                Menú principal
                Selecciona una sección:
                """.trim();
    }

    private static String welcomeText() {
        return """
                ¡Hola! Soy tu asistente financiero personal.
                Te ayudo a registrar movimientos y consultar tu resumen.
                """.trim();
    }

    void sendMainMenu(String chatId) {
        hideLegacyReplyKeyboard(chatId);
        messageSender.sendText(chatId, welcomeText() + "\n\n" + menuText(), buildMainMenuInlineKeyboard());
    }

    private void sendAccountsMenu(String chatId) {
        hideLegacyReplyKeyboard(chatId);
        messageSender.sendText(chatId, "Cuentas: elige una opción.", buildAccountsMenuInlineKeyboard());
    }

    private void sendCardsMenu(String chatId) {
        hideLegacyReplyKeyboard(chatId);
        messageSender.sendText(chatId, "Tarjetas: elige una opción.", buildCardsMenuInlineKeyboard());
    }

    private void sendCategoriesMenu(String chatId) {
        hideLegacyReplyKeyboard(chatId);
        messageSender.sendText(chatId, "Categorías: elige una opción.", buildCategoriesMenuInlineKeyboard());
    }

    private void sendMovementsMenu(String chatId) {
        hideLegacyReplyKeyboard(chatId);
        messageSender.sendText(chatId, "Movimientos: elige una opción.", buildMovementsMenuInlineKeyboard());
    }

    private void sendReportsMenu(String chatId) {
        hideLegacyReplyKeyboard(chatId);
        messageSender.sendText(chatId, "Resumen y consultas: elige una opción.", buildReportsMenuInlineKeyboard());
    }

    private void hideLegacyReplyKeyboard(String chatId) {
        // Caracter invisible para forzar remove_keyboard sin ensuciar el chat.
        messageSender.sendText(chatId, "\u200B", Map.of("remove_keyboard", true));
    }

    private static Map<String, Object> buildMainMenuInlineKeyboard() {
        return Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of("text", "➕ Movimientos", "callback_data", "menu:movements"),
                                Map.of("text", "📊 Resumen", "callback_data", "menu:reports")),
                        List.of(Map.of("text", "💼 Cuentas", "callback_data", "menu:accounts"),
                                Map.of("text", "💳 Tarjetas", "callback_data", "menu:cards")),
                        List.of(Map.of("text", "📂 Categorías", "callback_data", "menu:categories")),
                        List.of(Map.of("text", "❌ Cancelar", "callback_data", "menu:cancel"))
                )
        );
    }

    private static Map<String, Object> buildAccountsMenuInlineKeyboard() {
        return Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of("text", "➕ Crear cuenta", "callback_data", "action:account_create"),
                                Map.of("text", "✏️ Editar cuenta", "callback_data", "action:account_edit")),
                        List.of(Map.of("text", "🗑️ Eliminar cuenta", "callback_data", "action:account_delete"),
                                Map.of("text", "👀 Ver cuentas", "callback_data", "action:view_accounts")),
                        List.of(Map.of("text", "⬅️ Volver", "callback_data", "menu:main"),
                                Map.of("text", "❌ Cancelar", "callback_data", "menu:cancel"))
                )
        );
    }

    private static Map<String, Object> buildCardsMenuInlineKeyboard() {
        return Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of("text", "➕ Crear tarjeta", "callback_data", "action:card_create"),
                                Map.of("text", "✏️ Editar tarjeta", "callback_data", "action:card_edit")),
                        List.of(Map.of("text", "🗑️ Eliminar tarjeta", "callback_data", "action:card_delete"),
                                Map.of("text", "👀 Ver tarjetas", "callback_data", "action:view_cards")),
                        List.of(Map.of("text", "⬅️ Volver", "callback_data", "menu:main"),
                                Map.of("text", "❌ Cancelar", "callback_data", "menu:cancel"))
                )
        );
    }

    private static Map<String, Object> buildCategoriesMenuInlineKeyboard() {
        return Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of("text", "➕ Agregar categoría", "callback_data", "action:category_create")),
                        List.of(Map.of("text", "✏️ Editar categoría", "callback_data", "action:category_edit"),
                                Map.of("text", "🗑️ Eliminar categoría", "callback_data", "action:category_delete")),
                        List.of(Map.of("text", "⬅️ Volver", "callback_data", "menu:main"),
                                Map.of("text", "❌ Cancelar", "callback_data", "menu:cancel"))
                )
        );
    }

    private static Map<String, Object> buildMovementsMenuInlineKeyboard() {
        return Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of("text", "➕ Registrar gasto", "callback_data", "action:expense"),
                                Map.of("text", "💰 Registrar ingreso", "callback_data", "action:income")),
                        List.of(Map.of("text", "🧾 Registrar deuda", "callback_data", "action:debt_register"),
                                Map.of("text", "💳 Consumo tarjeta", "callback_data", "action:card_consumption")),
                        List.of(Map.of("text", "🏦 Pagar tarjeta", "callback_data", "action:card_payment"),
                                Map.of("text", "📉 Abonar deuda", "callback_data", "action:debt_payment")),
                        List.of(Map.of("text", "🔁 Transferir", "callback_data", "action:transfer")),
                        List.of(Map.of("text", "⬅️ Volver", "callback_data", "menu:main"),
                                Map.of("text", "❌ Cancelar", "callback_data", "menu:cancel"))
                )
        );
    }

    private static Map<String, Object> buildReportsMenuInlineKeyboard() {
        return Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of("text", "📆 Resumen mes", "callback_data", "action:summary"),
                                Map.of("text", "⚖️ Balance", "callback_data", "action:balance")),
                        List.of(Map.of("text", "👀 Ver cuentas", "callback_data", "action:view_accounts"),
                                Map.of("text", "👀 Ver tarjetas", "callback_data", "action:view_cards")),
                        List.of(Map.of("text", "👀 Ver deudas", "callback_data", "action:view_debts")),
                        List.of(Map.of("text", "⬅️ Volver", "callback_data", "menu:main"),
                                Map.of("text", "❌ Cancelar", "callback_data", "menu:cancel"))
                )
        );
    }

    private void handleAccountCreate(String chatId, String rawText) {
        String[] parts = parsePipeArgs(rawText, 4);
        if (parts == null) {
            messageSender.sendText(chatId, "Uso: /cuenta_crear nombre|tipo|saldo_inicial|notas");
            return;
        }
        try {
            AccountType type = AccountType.fromString(parts[1].trim());
            BigDecimal initial = new BigDecimal(parts[2].trim());
            AccountResponse created = accountService.create(new AccountCreateRequest(
                    parts[0].trim(), type, initial, emptyToNull(parts[3]), true));
            messageSender.sendText(chatId, "Cuenta creada: " + created.name() + " (id " + created.id() + ")");
        } catch (Exception e) {
            messageSender.sendText(chatId, "No se pudo crear la cuenta. Verifica tipo (CORRIENTE/AHORROS/EFECTIVO/BILLETERA_DIGITAL) y saldo.");
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
            AccountType type = AccountType.fromString(parts[2].trim());
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

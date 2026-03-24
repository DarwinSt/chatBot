package com.financebot.service.telegram;

import com.financebot.dto.request.CreditCardPaymentRequest;
import com.financebot.dto.request.DebtCreateRequest;
import com.financebot.dto.request.DebtPaymentRequest;
import com.financebot.dto.request.ExpenseCreateRequest;
import com.financebot.dto.request.IncomeCreateRequest;
import com.financebot.dto.request.TransferCreateRequest;
import com.financebot.dto.response.AccountResponse;
import com.financebot.dto.response.CategoryRefResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.entity.TelegramChatSession;
import com.financebot.enums.CategoryType;
import com.financebot.enums.TelegramConversationState;
import com.financebot.exception.BusinessRuleException;
import com.financebot.exception.ResourceNotFoundException;
import com.financebot.integration.telegram.context.TelegramContextPayload;
import com.financebot.integration.telegram.TelegramMessageSender;
import com.financebot.service.AccountService;
import com.financebot.service.CategoryService;
import com.financebot.service.CreditCardService;
import com.financebot.service.DebtService;
import com.financebot.service.ExpenseService;
import com.financebot.service.IncomeService;
import com.financebot.service.TransferService;
import com.financebot.util.TelegramParsingUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CARD_CONSUMPTION;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CARD_PAYMENT;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_DEBT_PAYMENT;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_DEBT_REGISTER;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_EXPENSE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_INCOME;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_TRANSFER;
import static com.financebot.service.telegram.TelegramDialogConstants.MODE_ACCOUNT;
import static com.financebot.service.telegram.TelegramDialogConstants.MODE_CARD;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_ACCOUNT;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_AMOUNT;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CARD;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CATEGORY;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CREDITOR;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DATE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DEBT;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DEBT_CATEGORY;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DESCRIPTION;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DESTINATION;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DUE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_NAME;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_ORIGIN;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_PAYMENT_MODE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_PENDING;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_SOURCE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_TARGET;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_TOTAL;

@Service
public class TelegramConversationService {

    private final TelegramSessionService sessionService;
    private final TelegramMessageSender messageSender;
    private final CategoryService categoryService;
    private final AccountService accountService;
    private final CreditCardService creditCardService;
    private final DebtService debtService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;

    public TelegramConversationService(
            TelegramSessionService sessionService,
            TelegramMessageSender messageSender,
            CategoryService categoryService,
            AccountService accountService,
            CreditCardService creditCardService,
            DebtService debtService,
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService) {
        this.sessionService = sessionService;
        this.messageSender = messageSender;
        this.categoryService = categoryService;
        this.accountService = accountService;
        this.creditCardService = creditCardService;
        this.debtService = debtService;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
        this.transferService = transferService;
    }

    @Transactional
    public void handleUserInput(String chatId, TelegramChatSession session, String rawInput) {
        TelegramContextPayload ctx = sessionService.readPayload(session);
        if (ctx.flow == null || ctx.step == null) {
            sessionService.resetToIdle(session);
            messageSender.sendText(chatId, "No hay un flujo activo. Escribe /ayuda.");
            return;
        }
        String input = rawInput.trim();
        try {
            switch (ctx.flow) {
                case FLOW_EXPENSE -> handleExpense(chatId, session, ctx, input);
                case FLOW_INCOME -> handleIncome(chatId, session, ctx, input);
                case FLOW_DEBT_REGISTER -> handleDebtRegister(chatId, session, ctx, input);
                case FLOW_CARD_CONSUMPTION -> handleCardConsumption(chatId, session, ctx, input);
                case FLOW_CARD_PAYMENT -> handleCardPayment(chatId, session, ctx, input);
                case FLOW_DEBT_PAYMENT -> handleDebtPayment(chatId, session, ctx, input);
                case FLOW_TRANSFER -> handleTransfer(chatId, session, ctx, input);
                default -> {
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "Flujo desconocido. Escribe /cancelar.");
                }
            }
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            messageSender.sendText(chatId, "No se pudo completar: " + ex.getMessage());
        } catch (Exception ex) {
            messageSender.sendText(chatId, "Error al procesar el paso. Revisa el formato o usa /cancelar.");
        }
    }

    @Transactional
    public void beginExpenseFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        if (categoryService.listActiveByType(CategoryType.EXPENSE).isEmpty()) {
            messageSender.sendText(chatId, "No hay categorías de gasto activas. Crea categorías antes.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_EXPENSE;
        ctx.step = STEP_AMOUNT;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Monto del gasto (ej: 25.50):");
    }

    @Transactional
    public void beginIncomeFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        if (categoryService.listActiveByType(CategoryType.INCOME).isEmpty()) {
            messageSender.sendText(chatId, "No hay categorías de ingreso activas.");
            return;
        }
        if (accountService.listActive().isEmpty()) {
            messageSender.sendText(chatId, "No hay cuentas activas.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_INCOME;
        ctx.step = STEP_AMOUNT;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Monto del ingreso (ej: 1200.00):");
    }

    @Transactional
    public void beginDebtRegisterFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_DEBT_REGISTER;
        ctx.step = STEP_NAME;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Nombre de la deuda:");
    }

    @Transactional
    public void beginCardConsumptionFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        if (categoryService.listActiveByType(CategoryType.EXPENSE).isEmpty()) {
            messageSender.sendText(chatId, "No hay categorías de gasto activas.");
            return;
        }
        if (creditCardService.listActive().isEmpty()) {
            messageSender.sendText(chatId, "No hay tarjetas activas.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CARD_CONSUMPTION;
        ctx.step = STEP_AMOUNT;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Monto del consumo con tarjeta:");
    }

    @Transactional
    public void beginCardPaymentFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        if (creditCardService.listActive().isEmpty()) {
            messageSender.sendText(chatId, "No hay tarjetas activas.");
            return;
        }
        if (accountService.listActive().isEmpty()) {
            messageSender.sendText(chatId, "No hay cuentas activas.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CARD_PAYMENT;
        ctx.step = STEP_CARD;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Elige la tarjeta por número:\n" + formatCards(creditCardService.listActive()));
    }

    @Transactional
    public void beginDebtPaymentFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        if (debtService.listActiveDebts().isEmpty()) {
            messageSender.sendText(chatId, "No hay deudas activas.");
            return;
        }
        if (accountService.listActive().isEmpty()) {
            messageSender.sendText(chatId, "No hay cuentas activas.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_DEBT_PAYMENT;
        ctx.step = STEP_DEBT;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Elige la deuda por número:\n" + formatDebts(debtService.listActiveDebts()));
    }

    @Transactional
    public void beginTransferFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        if (accountService.listActive().size() < 2) {
            messageSender.sendText(chatId, "Se necesitan al menos dos cuentas activas para transferir.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_TRANSFER;
        ctx.step = STEP_SOURCE;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Cuenta origen (número):\n" + formatAccounts(accountService.listActive()));
    }

    private void handleExpense(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_AMOUNT -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido. Ejemplo: 12.50");
                    return;
                }
                ctx.fields.put("amount", amt.get().toPlainString());
                ctx.step = STEP_CATEGORY;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Categoría (número):\n" + formatCategories(categoryService.listActiveByType(CategoryType.EXPENSE)));
            }
            case STEP_CATEGORY -> {
                List<CategoryRefResponse> cats = categoryService.listActiveByType(CategoryType.EXPENSE);
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cats.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("categoryId", String.valueOf(cats.get(idx.get() - 1).id()));
                ctx.step = STEP_DATE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha del gasto (yyyy-MM-dd):");
            }
            case STEP_DATE -> {
                var d = TelegramParsingUtils.parseIsoDate(input);
                if (d.isEmpty()) {
                    messageSender.sendText(chatId, "Fecha inválida. Formato yyyy-MM-dd.");
                    return;
                }
                ctx.fields.put("date", d.get().toString());
                ctx.step = STEP_PAYMENT_MODE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "¿Pago desde cuenta o tarjeta? Escribe 1=cuenta, 2=tarjeta.");
            }
            case STEP_PAYMENT_MODE -> {
                String mode = parsePaymentMode(input);
                if (mode == null) {
                    messageSender.sendText(chatId, "Responde 1 o 2.");
                    return;
                }
                ctx.fields.put("mode", mode);
                ctx.step = STEP_TARGET;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                if (MODE_ACCOUNT.equals(mode)) {
                    messageSender.sendText(chatId, "Cuenta (número):\n" + formatAccounts(accountService.listActive()));
                } else {
                    messageSender.sendText(chatId, "Tarjeta (número):\n" + formatCards(creditCardService.listActive()));
                }
            }
            case STEP_TARGET -> {
                String mode = ctx.fields.get("mode");
                Long targetId = resolveTargetId(mode, input);
                if (targetId == null) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                if (MODE_ACCOUNT.equals(mode)) {
                    ctx.fields.put("accountId", String.valueOf(targetId));
                } else {
                    ctx.fields.put("cardId", String.valueOf(targetId));
                }
                ctx.step = STEP_DESCRIPTION;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Descripción opcional (o '-' para omitir):");
            }
            case STEP_DESCRIPTION -> {
                String notes = "-".equals(input) ? null : input;
                BigDecimal amount = new BigDecimal(ctx.fields.get("amount"));
                LocalDate date = LocalDate.parse(ctx.fields.get("date"));
                long categoryId = Long.parseLong(ctx.fields.get("categoryId"));
                Long accountId = ctx.fields.containsKey("accountId") ? Long.parseLong(ctx.fields.get("accountId")) : null;
                Long cardId = ctx.fields.containsKey("cardId") ? Long.parseLong(ctx.fields.get("cardId")) : null;
                expenseService.create(new ExpenseCreateRequest(
                        amount,
                        date,
                        "Telegram",
                        notes,
                        categoryId,
                        accountId,
                        cardId));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Gasto registrado correctamente.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleIncome(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_AMOUNT -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido.");
                    return;
                }
                ctx.fields.put("amount", amt.get().toPlainString());
                ctx.step = STEP_CATEGORY;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Categoría (número):\n" + formatCategories(categoryService.listActiveByType(CategoryType.INCOME)));
            }
            case STEP_CATEGORY -> {
                List<CategoryRefResponse> cats = categoryService.listActiveByType(CategoryType.INCOME);
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cats.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("categoryId", String.valueOf(cats.get(idx.get() - 1).id()));
                ctx.step = STEP_DATE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha del ingreso (yyyy-MM-dd):");
            }
            case STEP_DATE -> {
                var d = TelegramParsingUtils.parseIsoDate(input);
                if (d.isEmpty()) {
                    messageSender.sendText(chatId, "Fecha inválida.");
                    return;
                }
                ctx.fields.put("date", d.get().toString());
                ctx.step = STEP_ACCOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Cuenta destino (número):\n" + formatAccounts(accountService.listActive()));
            }
            case STEP_ACCOUNT -> {
                List<AccountResponse> accs = accountService.listActive();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > accs.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("accountId", String.valueOf(accs.get(idx.get() - 1).id()));
                ctx.step = STEP_ORIGIN;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Origen del ingreso (texto libre):");
            }
            case STEP_ORIGIN -> {
                ctx.fields.put("origin", input);
                ctx.step = STEP_DESCRIPTION;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Descripción opcional (o '-'):");
            }
            case STEP_DESCRIPTION -> {
                String desc = "-".equals(input) ? null : input;
                incomeService.create(new IncomeCreateRequest(
                        new BigDecimal(ctx.fields.get("amount")),
                        LocalDate.parse(ctx.fields.get("date")),
                        ctx.fields.get("origin"),
                        desc,
                        Long.parseLong(ctx.fields.get("categoryId")),
                        Long.parseLong(ctx.fields.get("accountId"))));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Ingreso registrado correctamente.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleDebtRegister(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_NAME -> {
                ctx.fields.put("name", input);
                ctx.step = STEP_TOTAL;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Monto total de la deuda:");
            }
            case STEP_TOTAL -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido.");
                    return;
                }
                ctx.fields.put("total", amt.get().toPlainString());
                ctx.step = STEP_PENDING;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Saldo pendiente (o '-' para igual al total):");
            }
            case STEP_PENDING -> {
                BigDecimal total = new BigDecimal(ctx.fields.get("total"));
                BigDecimal pending = "-".equals(input) ? total : TelegramParsingUtils.parseAmount(input).orElse(null);
                if (pending == null) {
                    messageSender.sendText(chatId, "Monto pendiente inválido.");
                    return;
                }
                ctx.fields.put("pending", pending.toPlainString());
                ctx.step = STEP_DUE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha de vencimiento (yyyy-MM-dd) o '-' para omitir:");
            }
            case STEP_DUE -> {
                LocalDate due = "-".equals(input) ? null : TelegramParsingUtils.parseIsoDate(input).orElse(null);
                if (!"-".equals(input) && due == null) {
                    messageSender.sendText(chatId, "Fecha inválida.");
                    return;
                }
                if (due != null) {
                    ctx.fields.put("due", due.toString());
                }
                ctx.step = STEP_CREDITOR;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Acreedor (texto) o '-' para omitir:");
            }
            case STEP_CREDITOR -> {
                if (!"-".equals(input)) {
                    ctx.fields.put("creditor", input);
                }
                ctx.step = STEP_DEBT_CATEGORY;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Categoría de deuda (número) o '-' para omitir:\n"
                        + formatCategories(categoryService.listActiveByType(CategoryType.DEBT)));
            }
            case STEP_DEBT_CATEGORY -> {
                Long categoryId = null;
                if (!"-".equals(input)) {
                    List<CategoryRefResponse> cats = categoryService.listActiveByType(CategoryType.DEBT);
                    var idx = TelegramParsingUtils.parsePositiveInt(input);
                    if (idx.isEmpty() || idx.get() > cats.size()) {
                        messageSender.sendText(chatId, "Número inválido.");
                        return;
                    }
                    categoryId = cats.get(idx.get() - 1).id();
                }
                BigDecimal total = new BigDecimal(ctx.fields.get("total"));
                BigDecimal pending = new BigDecimal(ctx.fields.get("pending"));
                LocalDate due = ctx.fields.containsKey("due") ? LocalDate.parse(ctx.fields.get("due")) : null;
                String creditorField = ctx.fields.get("creditor");
                debtService.create(new DebtCreateRequest(
                        ctx.fields.get("name"),
                        total,
                        pending,
                        LocalDate.now(),
                        due,
                        creditorField,
                        null,
                        null,
                        categoryId));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Deuda registrada correctamente.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCardConsumption(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_AMOUNT -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido.");
                    return;
                }
                ctx.fields.put("amount", amt.get().toPlainString());
                ctx.step = STEP_CATEGORY;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Categoría (número):\n" + formatCategories(categoryService.listActiveByType(CategoryType.EXPENSE)));
            }
            case STEP_CATEGORY -> {
                List<CategoryRefResponse> cats = categoryService.listActiveByType(CategoryType.EXPENSE);
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cats.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("categoryId", String.valueOf(cats.get(idx.get() - 1).id()));
                ctx.step = STEP_DATE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha (yyyy-MM-dd):");
            }
            case STEP_DATE -> {
                var d = TelegramParsingUtils.parseIsoDate(input);
                if (d.isEmpty()) {
                    messageSender.sendText(chatId, "Fecha inválida.");
                    return;
                }
                ctx.fields.put("date", d.get().toString());
                ctx.step = STEP_CARD;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Tarjeta (número):\n" + formatCards(creditCardService.listActive()));
            }
            case STEP_CARD -> {
                List<CreditCardResponse> cards = creditCardService.listActive();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cards.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("cardId", String.valueOf(cards.get(idx.get() - 1).id()));
                ctx.step = STEP_DESCRIPTION;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Descripción opcional (o '-'):");
            }
            case STEP_DESCRIPTION -> {
                String notes = "-".equals(input) ? null : input;
                expenseService.create(new ExpenseCreateRequest(
                        new BigDecimal(ctx.fields.get("amount")),
                        LocalDate.parse(ctx.fields.get("date")),
                        "Telegram",
                        notes,
                        Long.parseLong(ctx.fields.get("categoryId")),
                        null,
                        Long.parseLong(ctx.fields.get("cardId"))));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Consumo con tarjeta registrado.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCardPayment(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_CARD -> {
                List<CreditCardResponse> cards = creditCardService.listActive();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cards.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("cardId", String.valueOf(cards.get(idx.get() - 1).id()));
                ctx.step = STEP_AMOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Monto a pagar:");
            }
            case STEP_AMOUNT -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido.");
                    return;
                }
                ctx.fields.put("amount", amt.get().toPlainString());
                ctx.step = STEP_DATE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha del pago (yyyy-MM-dd):");
            }
            case STEP_DATE -> {
                var d = TelegramParsingUtils.parseIsoDate(input);
                if (d.isEmpty()) {
                    messageSender.sendText(chatId, "Fecha inválida.");
                    return;
                }
                ctx.fields.put("date", d.get().toString());
                ctx.step = STEP_ACCOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Cuenta origen (número):\n" + formatAccounts(accountService.listActive()));
            }
            case STEP_ACCOUNT -> {
                List<AccountResponse> accs = accountService.listActive();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > accs.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                long accountId = accs.get(idx.get() - 1).id();
                creditCardService.registerPayment(
                        Long.parseLong(ctx.fields.get("cardId")),
                        new CreditCardPaymentRequest(
                                new BigDecimal(ctx.fields.get("amount")),
                                LocalDate.parse(ctx.fields.get("date")),
                                null,
                                accountId));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Pago a tarjeta registrado.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleDebtPayment(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_DEBT -> {
                List<DebtResponse> debts = debtService.listActiveDebts();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > debts.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("debtId", String.valueOf(debts.get(idx.get() - 1).id()));
                ctx.step = STEP_AMOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Monto del abono:");
            }
            case STEP_AMOUNT -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido.");
                    return;
                }
                ctx.fields.put("amount", amt.get().toPlainString());
                ctx.step = STEP_DATE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha del abono (yyyy-MM-dd):");
            }
            case STEP_DATE -> {
                var d = TelegramParsingUtils.parseIsoDate(input);
                if (d.isEmpty()) {
                    messageSender.sendText(chatId, "Fecha inválida.");
                    return;
                }
                ctx.fields.put("date", d.get().toString());
                ctx.step = STEP_ACCOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Cuenta origen (número):\n" + formatAccounts(accountService.listActive()));
            }
            case STEP_ACCOUNT -> {
                List<AccountResponse> accs = accountService.listActive();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > accs.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                long accountId = accs.get(idx.get() - 1).id();
                debtService.registerPayment(
                        Long.parseLong(ctx.fields.get("debtId")),
                        new DebtPaymentRequest(
                                new BigDecimal(ctx.fields.get("amount")),
                                LocalDate.parse(ctx.fields.get("date")),
                                null,
                                accountId));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Abono a deuda registrado.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleTransfer(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_SOURCE -> {
                List<AccountResponse> accs = accountService.listActive();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > accs.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("sourceId", String.valueOf(accs.get(idx.get() - 1).id()));
                ctx.step = STEP_DESTINATION;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Cuenta destino (número):\n" + formatAccounts(accountService.listActive()));
            }
            case STEP_DESTINATION -> {
                List<AccountResponse> accs = accountService.listActive();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > accs.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                long destId = accs.get(idx.get() - 1).id();
                if (String.valueOf(destId).equals(ctx.fields.get("sourceId"))) {
                    messageSender.sendText(chatId, "La cuenta destino debe ser distinta del origen.");
                    return;
                }
                ctx.fields.put("destId", String.valueOf(destId));
                ctx.step = STEP_AMOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Monto a transferir:");
            }
            case STEP_AMOUNT -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido.");
                    return;
                }
                ctx.fields.put("amount", amt.get().toPlainString());
                ctx.step = STEP_DATE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha (yyyy-MM-dd):");
            }
            case STEP_DATE -> {
                var d = TelegramParsingUtils.parseIsoDate(input);
                if (d.isEmpty()) {
                    messageSender.sendText(chatId, "Fecha inválida.");
                    return;
                }
                ctx.fields.put("date", d.get().toString());
                ctx.step = STEP_DESCRIPTION;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Descripción opcional (o '-'):");
            }
            case STEP_DESCRIPTION -> {
                String desc = "-".equals(input) ? null : input;
                transferService.create(new TransferCreateRequest(
                        new BigDecimal(ctx.fields.get("amount")),
                        LocalDate.parse(ctx.fields.get("date")),
                        desc,
                        Long.parseLong(ctx.fields.get("sourceId")),
                        Long.parseLong(ctx.fields.get("destId"))));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Transferencia registrada.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void resetUnknown(String chatId, TelegramChatSession session) {
        sessionService.resetToIdle(session);
        messageSender.sendText(chatId, "Flujo reiniciado por estado desconocido. Usa /cancelar si persiste.");
    }

    private String parsePaymentMode(String input) {
        String t = input.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(t) || t.contains("cuenta")) {
            return MODE_ACCOUNT;
        }
        if ("2".equals(t) || t.contains("tarjeta")) {
            return MODE_CARD;
        }
        return null;
    }

    private Long resolveTargetId(String mode, String input) {
        if (MODE_ACCOUNT.equals(mode)) {
            List<AccountResponse> list = accountService.listActive();
            var idx = TelegramParsingUtils.parsePositiveInt(input);
            if (idx.isEmpty() || idx.get() > list.size()) {
                return null;
            }
            return list.get(idx.get() - 1).id();
        }
        List<CreditCardResponse> cards = creditCardService.listActive();
        var idx = TelegramParsingUtils.parsePositiveInt(input);
        if (idx.isEmpty() || idx.get() > cards.size()) {
            return null;
        }
        return cards.get(idx.get() - 1).id();
    }

    private String formatCategories(List<CategoryRefResponse> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(i + 1).append(") ").append(list.get(i).name()).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatAccounts(List<AccountResponse> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(i + 1).append(") ").append(list.get(i).name()).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatCards(List<CreditCardResponse> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(i + 1).append(") ").append(list.get(i).name()).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatDebts(List<DebtResponse> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(i + 1).append(") ").append(list.get(i).name())
                    .append(" (pend. ").append(list.get(i).pendingAmount()).append(")\n");
        }
        return sb.toString().trim();
    }
}

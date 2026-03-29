package com.financebot.service.telegram;

import com.financebot.dto.request.AccountCreateRequest;
import com.financebot.dto.request.CreditCardCreateRequest;
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
import com.financebot.enums.AccountType;
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
import java.util.Map;

import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_ACCOUNT_CREATE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_ACCOUNT_DELETE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_ACCOUNT_EDIT;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CARD_CREATE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CARD_DELETE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CARD_EDIT;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CATEGORY_CREATE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CATEGORY_DELETE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CATEGORY_EDIT;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_CARD_PAYMENT;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_DEBT_PAYMENT;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_DEBT_REGISTER;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_EXPENSE;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_INCOME;
import static com.financebot.service.telegram.TelegramDialogConstants.FLOW_TRANSFER;
import static com.financebot.service.telegram.TelegramDialogConstants.MODE_ACCOUNT;
import static com.financebot.service.telegram.TelegramDialogConstants.MODE_CARD;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_ACCOUNT;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_ACCOUNT_TYPE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_ACTIVE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_AMOUNT;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CARD;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CATEGORY;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CATEGORY_TYPE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CREDITOR;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CUTOFF;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DATE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DEBT;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DEBT_CATEGORY;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_CONFIRM;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DESCRIPTION;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DESTINATION;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_DUE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_NAME;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_INITIAL_BALANCE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_LIMIT;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_NOTES;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_ORIGIN;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_PAYMENT_DUE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_PAYMENT_MODE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_PENDING;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_SOURCE;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_TARGET;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_TOTAL;
import static com.financebot.service.telegram.TelegramDialogConstants.STEP_USED;

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
                case FLOW_ACCOUNT_CREATE -> handleAccountCreate(chatId, session, ctx, input);
                case FLOW_CARD_CREATE -> handleCardCreateWizard(chatId, session, ctx, input);
                case FLOW_ACCOUNT_EDIT -> handleAccountEdit(chatId, session, ctx, input);
                case FLOW_ACCOUNT_DELETE -> handleAccountDelete(chatId, session, ctx, input);
                case FLOW_CARD_EDIT -> handleCardEdit(chatId, session, ctx, input);
                case FLOW_CARD_DELETE -> handleCardDelete(chatId, session, ctx, input);
                case FLOW_CATEGORY_CREATE -> handleCategoryCreate(chatId, session, ctx, input);
                case FLOW_CATEGORY_EDIT -> handleCategoryEdit(chatId, session, ctx, input);
                case FLOW_CATEGORY_DELETE -> handleCategoryDelete(chatId, session, ctx, input);
                case FLOW_EXPENSE -> handleExpense(chatId, session, ctx, input);
                case FLOW_INCOME -> handleIncome(chatId, session, ctx, input);
                case FLOW_DEBT_REGISTER -> handleDebtRegister(chatId, session, ctx, input);
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
        List<CategoryRefResponse> expenseCategories = categoryService.listActiveByType(CategoryType.GASTO);
        if (expenseCategories.isEmpty()) {
            messageSender.sendText(chatId, "No hay categorías de gasto activas. Crea categorías antes.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_EXPENSE;
        ctx.fields.put("categoryId", String.valueOf(resolveDefaultExpenseCategoryId(expenseCategories)));
        ctx.step = STEP_PAYMENT_MODE;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Paso 1/4 - Origen del gasto: escribe 1=cuenta, 2=tarjeta.");
    }

    @Transactional
    public void beginIncomeFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        if (categoryService.listActiveByType(CategoryType.INGRESO).isEmpty()) {
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

    @Transactional
    public void beginAccountCreateFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_ACCOUNT_CREATE;
        ctx.step = STEP_NAME;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, """
                Crear cuenta - Paso 1/4
                Escribe el nombre de la cuenta:
                """.trim());
    }

    @Transactional
    public void beginCardCreateFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CARD_CREATE;
        ctx.step = STEP_NAME;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, """
                Crear tarjeta - Paso 1/6
                Escribe el nombre de la tarjeta:
                """.trim());
    }

    @Transactional
    public void beginAccountEditFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        List<AccountResponse> accounts = accountService.listAll();
        if (accounts.isEmpty()) {
            messageSender.sendText(chatId, "No hay cuentas para editar.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_ACCOUNT_EDIT;
        ctx.step = STEP_ACCOUNT;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Editar cuenta - Paso 1/5\nElige la cuenta por número:\n" + formatAccounts(accounts));
    }

    @Transactional
    public void beginAccountDeleteFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        List<AccountResponse> accounts = accountService.listAll();
        if (accounts.isEmpty()) {
            messageSender.sendText(chatId, "No hay cuentas para eliminar.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_ACCOUNT_DELETE;
        ctx.step = STEP_ACCOUNT;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Eliminar cuenta - Paso 1/2\nElige la cuenta por número:\n" + formatAccounts(accounts));
    }

    @Transactional
    public void beginCardEditFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        List<CreditCardResponse> cards = creditCardService.listAll();
        if (cards.isEmpty()) {
            messageSender.sendText(chatId, "No hay tarjetas para editar.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CARD_EDIT;
        ctx.step = STEP_CARD;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Editar tarjeta - Paso 1/7\nElige la tarjeta por número:\n" + formatCards(cards));
    }

    @Transactional
    public void beginCardDeleteFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        List<CreditCardResponse> cards = creditCardService.listAll();
        if (cards.isEmpty()) {
            messageSender.sendText(chatId, "No hay tarjetas para eliminar.");
            return;
        }
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CARD_DELETE;
        ctx.step = STEP_CARD;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, "Eliminar tarjeta - Paso 1/2\nElige la tarjeta por número:\n" + formatCards(cards));
    }

    @Transactional
    public void beginCategoryCreateFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CATEGORY_CREATE;
        ctx.step = STEP_CATEGORY_TYPE;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, """
                Crear categoría - Paso 1/2
                Tipo:
                1) INGRESO
                2) GASTO
                3) DEUDA
                """.trim());
    }

    @Transactional
    public void beginCategoryEditFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CATEGORY_EDIT;
        ctx.step = STEP_CATEGORY_TYPE;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, """
                Editar categoría - Paso 1/3
                Tipo:
                1) INGRESO
                2) GASTO
                3) DEUDA
                """.trim());
    }

    @Transactional
    public void beginCategoryDeleteFlow(String chatId, TelegramChatSession session, String pendingCommand) {
        TelegramContextPayload ctx = new TelegramContextPayload();
        ctx.flow = FLOW_CATEGORY_DELETE;
        ctx.step = STEP_CATEGORY_TYPE;
        sessionService.save(session, TelegramConversationState.CONVERSATION, pendingCommand, ctx);
        messageSender.sendText(chatId, """
                Eliminar categoría - Paso 1/3
                Tipo:
                1) INGRESO
                2) GASTO
                3) DEUDA
                """.trim());
    }

    private void handleAccountCreate(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_NAME -> {
                if (input.isBlank()) {
                    messageSender.sendText(chatId, "El nombre no puede estar vacío.");
                    return;
                }
                ctx.fields.put("name", input.trim());
                ctx.step = STEP_ACCOUNT_TYPE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, """
                        Crear cuenta - Paso 2/4
                        Tipo de cuenta:
                        1) CORRIENTE
                        2) AHORROS
                        3) EFECTIVO
                        4) BILLETERA_DIGITAL
                        """.trim());
            }
            case STEP_ACCOUNT_TYPE -> {
                AccountType type = parseAccountType(input);
                if (type == null) {
                    messageSender.sendText(chatId, "Tipo inválido. Responde 1-4 o nombre del tipo.");
                    return;
                }
                ctx.fields.put("type", type.name());
                ctx.step = STEP_INITIAL_BALANCE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear cuenta - Paso 3/4\nSaldo inicial (ej: 1500.00):");
            }
            case STEP_INITIAL_BALANCE -> {
                var amount = TelegramParsingUtils.parseAmount(input);
                if (amount.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido.");
                    return;
                }
                ctx.fields.put("initialBalance", amount.get().toPlainString());
                ctx.step = STEP_NOTES;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear cuenta - Paso 4/4\nNotas (o '-' para omitir):");
            }
            case STEP_NOTES -> {
                String notes = "-".equals(input) ? null : input.trim();
                accountService.create(new AccountCreateRequest(
                        ctx.fields.get("name"),
                        AccountType.fromString(ctx.fields.get("type")),
                        new BigDecimal(ctx.fields.get("initialBalance")),
                        notes,
                        true
                ));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Cuenta creada correctamente.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCardCreateWizard(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_NAME -> {
                if (input.isBlank()) {
                    messageSender.sendText(chatId, "El nombre no puede estar vacío.");
                    return;
                }
                ctx.fields.put("name", input.trim());
                ctx.step = STEP_LIMIT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear tarjeta - Paso 2/6\nCupo total (ej: 5000):");
            }
            case STEP_LIMIT -> {
                var totalLimit = TelegramParsingUtils.parseAmount(input);
                if (totalLimit.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido para cupo total.");
                    return;
                }
                ctx.fields.put("totalLimit", totalLimit.get().toPlainString());
                ctx.step = STEP_USED;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear tarjeta - Paso 3/6\nMonto usado actual (ej: 0):");
            }
            case STEP_USED -> {
                var used = TelegramParsingUtils.parseAmount(input);
                if (used.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido para usado.");
                    return;
                }
                ctx.fields.put("usedAmount", used.get().toPlainString());
                ctx.step = STEP_CUTOFF;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear tarjeta - Paso 4/6\nDía de corte (1-31) o '-' para omitir:");
            }
            case STEP_CUTOFF -> {
                Short cutoff = parseDayOrNull(input);
                if (!"-".equals(input.trim()) && cutoff == null) {
                    messageSender.sendText(chatId, "Día de corte inválido. Usa 1-31 o '-'.");
                    return;
                }
                if (cutoff != null) {
                    ctx.fields.put("cutoff", cutoff.toString());
                }
                ctx.step = STEP_PAYMENT_DUE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear tarjeta - Paso 5/6\nDía de vencimiento (1-31) o '-' para omitir:");
            }
            case STEP_PAYMENT_DUE -> {
                Short due = parseDayOrNull(input);
                if (!"-".equals(input.trim()) && due == null) {
                    messageSender.sendText(chatId, "Día de vencimiento inválido. Usa 1-31 o '-'.");
                    return;
                }
                if (due != null) {
                    ctx.fields.put("due", due.toString());
                }
                ctx.step = STEP_NOTES;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear tarjeta - Paso 6/6\nNotas (o '-' para omitir):");
            }
            case STEP_NOTES -> {
                String notes = "-".equals(input.trim()) ? null : input.trim();
                creditCardService.create(new CreditCardCreateRequest(
                        ctx.fields.get("name"),
                        new BigDecimal(ctx.fields.get("totalLimit")),
                        new BigDecimal(ctx.fields.get("usedAmount")),
                        ctx.fields.containsKey("cutoff") ? Short.parseShort(ctx.fields.get("cutoff")) : null,
                        ctx.fields.containsKey("due") ? Short.parseShort(ctx.fields.get("due")) : null,
                        notes
                ));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Tarjeta creada correctamente.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleAccountEdit(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_ACCOUNT -> {
                List<AccountResponse> accounts = accountService.listAll();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > accounts.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                AccountResponse selected = accounts.get(idx.get() - 1);
                ctx.fields.put("accountId", String.valueOf(selected.id()));
                ctx.fields.put("name", selected.name());
                ctx.fields.put("type", selected.type().name());
                ctx.fields.put("notes", selected.notes() == null ? "" : selected.notes());
                ctx.fields.put("active", String.valueOf(selected.active()));
                ctx.step = STEP_NAME;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 2/5 - Nuevo nombre (o '-' para mantener):");
            }
            case STEP_NAME -> {
                if (!"-".equals(input.trim())) {
                    ctx.fields.put("name", input.trim());
                }
                ctx.step = STEP_ACCOUNT_TYPE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, """
                        Paso 3/5 - Tipo (1-4) o '-' para mantener:
                        1) CORRIENTE
                        2) AHORROS
                        3) EFECTIVO
                        4) BILLETERA_DIGITAL
                        """.trim());
            }
            case STEP_ACCOUNT_TYPE -> {
                if (!"-".equals(input.trim())) {
                    AccountType type = parseAccountType(input);
                    if (type == null) {
                        messageSender.sendText(chatId, "Tipo inválido. Usa 1-4 o '-'.");
                        return;
                    }
                    ctx.fields.put("type", type.name());
                }
                ctx.step = STEP_ACTIVE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 4/5 - Activa? (SI/NO o '-' para mantener):");
            }
            case STEP_ACTIVE -> {
                String t = input.trim().toUpperCase(Locale.ROOT);
                if (!"-".equals(t)) {
                    if ("SI".equals(t) || "S".equals(t) || "1".equals(t) || "TRUE".equals(t)) {
                        ctx.fields.put("active", "true");
                    } else if ("NO".equals(t) || "N".equals(t) || "0".equals(t) || "FALSE".equals(t)) {
                        ctx.fields.put("active", "false");
                    } else {
                        messageSender.sendText(chatId, "Valor inválido. Responde SI/NO o '-'.");
                        return;
                    }
                }
                ctx.step = STEP_NOTES;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 5/5 - Notas (o '-' para mantener):");
            }
            case STEP_NOTES -> {
                if (!"-".equals(input.trim())) {
                    ctx.fields.put("notes", input.trim());
                }
                accountService.updateBasic(
                        Long.parseLong(ctx.fields.get("accountId")),
                        ctx.fields.get("name"),
                        AccountType.fromString(ctx.fields.get("type")),
                        ctx.fields.get("notes").isBlank() ? null : ctx.fields.get("notes"),
                        Boolean.parseBoolean(ctx.fields.get("active"))
                );
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Cuenta actualizada correctamente.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleAccountDelete(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_ACCOUNT -> {
                List<AccountResponse> accounts = accountService.listAll();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > accounts.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                AccountResponse selected = accounts.get(idx.get() - 1);
                ctx.fields.put("accountId", String.valueOf(selected.id()));
                ctx.step = STEP_CONFIRM;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Confirmar eliminación lógica de cuenta '" + selected.name() + "'? (SI/NO)");
            }
            case STEP_CONFIRM -> {
                String t = input.trim().toUpperCase(Locale.ROOT);
                if ("SI".equals(t) || "S".equals(t)) {
                    accountService.deactivate(Long.parseLong(ctx.fields.get("accountId")));
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "Cuenta desactivada correctamente.");
                } else if ("NO".equals(t) || "N".equals(t)) {
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "Operación cancelada.");
                } else {
                    messageSender.sendText(chatId, "Responde SI o NO.");
                }
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCardEdit(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_CARD -> {
                List<CreditCardResponse> cards = creditCardService.listAll();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cards.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                CreditCardResponse selected = cards.get(idx.get() - 1);
                ctx.fields.put("cardId", String.valueOf(selected.id()));
                ctx.fields.put("name", selected.name());
                ctx.fields.put("totalLimit", selected.totalLimit().toPlainString());
                ctx.fields.put("cutoff", selected.statementCutoffDay() == null ? "" : selected.statementCutoffDay().toString());
                ctx.fields.put("due", selected.paymentDueDay() == null ? "" : selected.paymentDueDay().toString());
                ctx.fields.put("active", String.valueOf(selected.active()));
                ctx.fields.put("notes", selected.notes() == null ? "" : selected.notes());
                ctx.step = STEP_NAME;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 2/7 - Nuevo nombre (o '-' para mantener):");
            }
            case STEP_NAME -> {
                if (!"-".equals(input.trim())) {
                    ctx.fields.put("name", input.trim());
                }
                ctx.step = STEP_LIMIT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 3/7 - Nuevo cupo total (o '-' para mantener):");
            }
            case STEP_LIMIT -> {
                if (!"-".equals(input.trim())) {
                    var amount = TelegramParsingUtils.parseAmount(input);
                    if (amount.isEmpty()) {
                        messageSender.sendText(chatId, "Cupo inválido.");
                        return;
                    }
                    ctx.fields.put("totalLimit", amount.get().toPlainString());
                }
                ctx.step = STEP_CUTOFF;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 4/7 - Día de corte (1-31, '-' mantener, '0' limpiar):");
            }
            case STEP_CUTOFF -> {
                String t = input.trim();
                if (!"-".equals(t)) {
                    if ("0".equals(t)) {
                        ctx.fields.put("cutoff", "");
                    } else {
                        Short day = parseDayOrNull(t);
                        if (day == null) {
                            messageSender.sendText(chatId, "Día de corte inválido.");
                            return;
                        }
                        ctx.fields.put("cutoff", day.toString());
                    }
                }
                ctx.step = STEP_PAYMENT_DUE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 5/7 - Día de vencimiento (1-31, '-' mantener, '0' limpiar):");
            }
            case STEP_PAYMENT_DUE -> {
                String t = input.trim();
                if (!"-".equals(t)) {
                    if ("0".equals(t)) {
                        ctx.fields.put("due", "");
                    } else {
                        Short day = parseDayOrNull(t);
                        if (day == null) {
                            messageSender.sendText(chatId, "Día de vencimiento inválido.");
                            return;
                        }
                        ctx.fields.put("due", day.toString());
                    }
                }
                ctx.step = STEP_ACTIVE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 6/7 - Activa? (SI/NO o '-' para mantener):");
            }
            case STEP_ACTIVE -> {
                String t = input.trim().toUpperCase(Locale.ROOT);
                if (!"-".equals(t)) {
                    if ("SI".equals(t) || "S".equals(t) || "1".equals(t) || "TRUE".equals(t)) {
                        ctx.fields.put("active", "true");
                    } else if ("NO".equals(t) || "N".equals(t) || "0".equals(t) || "FALSE".equals(t)) {
                        ctx.fields.put("active", "false");
                    } else {
                        messageSender.sendText(chatId, "Valor inválido. Responde SI/NO o '-'.");
                        return;
                    }
                }
                ctx.step = STEP_NOTES;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 7/7 - Notas (o '-' para mantener, '0' limpiar):");
            }
            case STEP_NOTES -> {
                String t = input.trim();
                if (!"-".equals(t)) {
                    ctx.fields.put("notes", "0".equals(t) ? "" : t);
                }
                creditCardService.updateBasic(
                        Long.parseLong(ctx.fields.get("cardId")),
                        ctx.fields.get("name"),
                        new BigDecimal(ctx.fields.get("totalLimit")),
                        ctx.fields.get("cutoff").isBlank() ? null : Short.parseShort(ctx.fields.get("cutoff")),
                        ctx.fields.get("due").isBlank() ? null : Short.parseShort(ctx.fields.get("due")),
                        ctx.fields.get("notes").isBlank() ? null : ctx.fields.get("notes"),
                        Boolean.parseBoolean(ctx.fields.get("active"))
                );
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Tarjeta actualizada correctamente.");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCardDelete(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_CARD -> {
                List<CreditCardResponse> cards = creditCardService.listAll();
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cards.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                CreditCardResponse selected = cards.get(idx.get() - 1);
                ctx.fields.put("cardId", String.valueOf(selected.id()));
                ctx.step = STEP_CONFIRM;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Confirmar eliminación lógica de tarjeta '" + selected.name() + "'? (SI/NO)");
            }
            case STEP_CONFIRM -> {
                String t = input.trim().toUpperCase(Locale.ROOT);
                if ("SI".equals(t) || "S".equals(t)) {
                    creditCardService.deactivate(Long.parseLong(ctx.fields.get("cardId")));
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "Tarjeta desactivada correctamente.");
                } else if ("NO".equals(t) || "N".equals(t)) {
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "Operación cancelada.");
                } else {
                    messageSender.sendText(chatId, "Responde SI o NO.");
                }
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCategoryCreate(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_CATEGORY_TYPE -> {
                CategoryType type = parseCategoryType(input);
                if (type == null) {
                    messageSender.sendText(chatId, "Tipo inválido. Usa 1-3.");
                    return;
                }
                ctx.fields.put("type", type.name());
                ctx.step = STEP_NAME;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Crear categoría - Paso 2/2\nNombre:");
            }
            case STEP_NAME -> {
                var created = categoryService.create(input, CategoryType.fromString(ctx.fields.get("type")));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Categoría creada: " + created.name() + " (" + created.type() + ")");
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCategoryEdit(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_CATEGORY_TYPE -> {
                CategoryType type = parseCategoryType(input);
                if (type == null) {
                    messageSender.sendText(chatId, "Tipo inválido. Usa 1-3.");
                    return;
                }
                List<CategoryRefResponse> categories = categoryService.listActiveByType(type);
                if (categories.isEmpty()) {
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "No hay categorías activas de tipo " + type.name() + ".");
                    return;
                }
                ctx.fields.put("type", type.name());
                ctx.step = STEP_CATEGORY;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Editar categoría - Paso 2/3\nElige por número:\n" + formatCategories(categories));
            }
            case STEP_CATEGORY -> {
                List<CategoryRefResponse> categories = categoryService.listActiveByType(CategoryType.fromString(ctx.fields.get("type")));
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > categories.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                CategoryRefResponse selected = categories.get(idx.get() - 1);
                ctx.fields.put("categoryId", String.valueOf(selected.id()));
                ctx.step = STEP_NAME;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Editar categoría - Paso 3/3\nNuevo nombre:");
            }
            case STEP_NAME -> {
                var updated = categoryService.update(
                        Long.parseLong(ctx.fields.get("categoryId")),
                        input,
                        CategoryType.fromString(ctx.fields.get("type")),
                        true
                );
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Categoría actualizada: " + updated.name());
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleCategoryDelete(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
            case STEP_CATEGORY_TYPE -> {
                CategoryType type = parseCategoryType(input);
                if (type == null) {
                    messageSender.sendText(chatId, "Tipo inválido. Usa 1-3.");
                    return;
                }
                List<CategoryRefResponse> categories = categoryService.listActiveByType(type);
                if (categories.isEmpty()) {
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "No hay categorías activas de tipo " + type.name() + ".");
                    return;
                }
                ctx.fields.put("type", type.name());
                ctx.step = STEP_CATEGORY;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Eliminar categoría - Paso 2/3\nElige por número:\n" + formatCategories(categories));
            }
            case STEP_CATEGORY -> {
                List<CategoryRefResponse> categories = categoryService.listActiveByType(CategoryType.fromString(ctx.fields.get("type")));
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > categories.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                CategoryRefResponse selected = categories.get(idx.get() - 1);
                ctx.fields.put("categoryId", String.valueOf(selected.id()));
                ctx.fields.put("categoryName", selected.name());
                ctx.step = STEP_CONFIRM;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Confirmar eliminación lógica de '" + selected.name() + "'? (SI/NO)");
            }
            case STEP_CONFIRM -> {
                String t = input.trim().toUpperCase(Locale.ROOT);
                if ("SI".equals(t) || "S".equals(t)) {
                    categoryService.deactivate(Long.parseLong(ctx.fields.get("categoryId")));
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "Categoría desactivada correctamente.");
                } else if ("NO".equals(t) || "N".equals(t)) {
                    sessionService.resetToIdle(session);
                    messageSender.sendText(chatId, "Operación cancelada.");
                } else {
                    messageSender.sendText(chatId, "Responde SI o NO.");
                }
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleExpense(String chatId, TelegramChatSession session, TelegramContextPayload ctx, String input) {
        switch (ctx.step) {
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
                    messageSender.sendText(chatId, "Paso 2/4 - Elige la cuenta origen (número):\n" + formatAccounts(accountService.listActive()));
                } else {
                    messageSender.sendText(chatId, "Paso 2/4 - Elige la tarjeta origen (número):\n" + formatCards(creditCardService.listActive()));
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
                ctx.step = STEP_AMOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 3/4 - Cantidad del gasto (ej: 25.50):");
            }
            case STEP_AMOUNT -> {
                var amt = TelegramParsingUtils.parseAmount(input);
                if (amt.isEmpty()) {
                    messageSender.sendText(chatId, "Monto inválido. Ejemplo: 12.50");
                    return;
                }
                ctx.fields.put("amount", amt.get().toPlainString());
                ctx.step = STEP_DESCRIPTION;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Paso 4/4 - Descripción (o '-' para omitir):");
            }
            case STEP_DESCRIPTION -> {
                if (input.startsWith("cb:exp_confirm:")) {
                    handleExpenseConfirmationCallback(chatId, session, ctx, input);
                    return;
                }
                String notes = "-".equals(input) ? null : input;
                ctx.fields.put("notes", notes == null ? "" : notes);
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(
                        chatId,
                        buildExpenseConfirmationText(ctx),
                        buildExpenseConfirmKeyboard()
                );
            }
            default -> resetUnknown(chatId, session);
        }
    }

    private void handleExpenseConfirmationCallback(
            String chatId,
            TelegramChatSession session,
            TelegramContextPayload ctx,
            String input
    ) {
        String action = input.substring("cb:exp_confirm:".length());
        switch (action) {
            case "ok" -> {
                BigDecimal amount = new BigDecimal(ctx.fields.get("amount"));
                LocalDate date = LocalDate.now();
                long categoryId = Long.parseLong(ctx.fields.get("categoryId"));
                Long accountId = ctx.fields.containsKey("accountId") ? Long.parseLong(ctx.fields.get("accountId")) : null;
                Long cardId = ctx.fields.containsKey("cardId") ? Long.parseLong(ctx.fields.get("cardId")) : null;
                String notes = ctx.fields.get("notes");
                if (notes != null && notes.isBlank()) {
                    notes = null;
                }
                String origin = MODE_ACCOUNT.equals(ctx.fields.get("mode")) ? "Cuenta" : "Tarjeta";
                expenseService.create(new ExpenseCreateRequest(
                        amount,
                        date,
                        origin,
                        notes,
                        categoryId,
                        accountId,
                        cardId
                ));
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Gasto registrado correctamente.");
            }
            case "src" -> {
                ctx.step = STEP_PAYMENT_MODE;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Origen del gasto: escribe 1=cuenta, 2=tarjeta.");
            }
            case "amt" -> {
                ctx.step = STEP_AMOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Ingresa el nuevo monto:");
            }
            case "cancel" -> {
                sessionService.resetToIdle(session);
                messageSender.sendText(chatId, "Registro cancelado.");
            }
            default -> messageSender.sendText(chatId, "Acción no reconocida.");
        }
    }

    private String buildExpenseConfirmationText(TelegramContextPayload ctx) {
        String mode = MODE_ACCOUNT.equals(ctx.fields.get("mode")) ? "Cuenta" : "Tarjeta";
        String target = ctx.fields.get("accountId");
        if (target == null) {
            target = ctx.fields.get("cardId");
        }
        String notes = ctx.fields.get("notes");
        if (notes == null || notes.isBlank()) {
            notes = "(sin descripción)";
        }
        return """
                Paso 4/4 - Confirmar gasto
                Monto: %s
                Origen: %s #%s
                Fecha: %s
                Notas: %s
                """.formatted(
                ctx.fields.get("amount"),
                mode,
                target,
                LocalDate.now(),
                notes
        ).trim();
    }

    private Map<String, Object> buildExpenseConfirmKeyboard() {
        return Map.of(
                "inline_keyboard", List.of(
                        List.of(
                                Map.of("text", "✅ Confirmar", "callback_data", "cb:exp_confirm:ok"),
                                Map.of("text", "🔁 Cambiar origen", "callback_data", "cb:exp_confirm:src")
                        ),
                        List.of(
                                Map.of("text", "🔁 Cambiar monto", "callback_data", "cb:exp_confirm:amt"),
                                Map.of("text", "❌ Cancelar", "callback_data", "cb:exp_confirm:cancel")
                        )
                )
        );
    }

    private Long resolveDefaultExpenseCategoryId(List<CategoryRefResponse> categories) {
        for (CategoryRefResponse c : categories) {
            String name = c.name() == null ? "" : c.name().trim().toLowerCase(Locale.ROOT);
            if ("otros".equals(name) || "general".equals(name)) {
                return c.id();
            }
        }
        return categories.get(0).id();
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
                messageSender.sendText(chatId, "Categoría (número):\n" + formatCategories(categoryService.listActiveByType(CategoryType.INGRESO)));
            }
            case STEP_CATEGORY -> {
                List<CategoryRefResponse> cats = categoryService.listActiveByType(CategoryType.INGRESO);
                var idx = TelegramParsingUtils.parsePositiveInt(input);
                if (idx.isEmpty() || idx.get() > cats.size()) {
                    messageSender.sendText(chatId, "Número inválido.");
                    return;
                }
                ctx.fields.put("categoryId", String.valueOf(cats.get(idx.get() - 1).id()));
                ctx.fields.put("date", LocalDate.now().toString());
                ctx.step = STEP_ACCOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha del ingreso: hoy.\nCuenta destino (número):\n" + formatAccounts(accountService.listActive()));
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
                        + formatCategories(categoryService.listActiveByType(CategoryType.DEUDA)));
            }
            case STEP_DEBT_CATEGORY -> {
                Long categoryId = null;
                if (!"-".equals(input)) {
                    List<CategoryRefResponse> cats = categoryService.listActiveByType(CategoryType.DEUDA);
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
                ctx.fields.put("date", LocalDate.now().toString());
                ctx.step = STEP_ACCOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha del pago: hoy.\nCuenta origen (número):\n" + formatAccounts(accountService.listActive()));
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
                ctx.fields.put("date", LocalDate.now().toString());
                ctx.step = STEP_ACCOUNT;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha del abono: hoy.\nCuenta origen (número):\n" + formatAccounts(accountService.listActive()));
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
                ctx.fields.put("date", LocalDate.now().toString());
                ctx.step = STEP_DESCRIPTION;
                sessionService.save(session, TelegramConversationState.CONVERSATION, session.getPendingCommand(), ctx);
                messageSender.sendText(chatId, "Fecha de transferencia: hoy.\nDescripción opcional (o '-'):");
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

    private AccountType parseAccountType(String input) {
        String t = input.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "1", "CORRIENTE", "CHECKING" -> AccountType.CORRIENTE;
            case "2", "AHORROS", "SAVINGS" -> AccountType.AHORROS;
            case "3", "EFECTIVO", "CASH" -> AccountType.EFECTIVO;
            case "4", "BILLETERA_DIGITAL", "DIGITAL_WALLET", "DIGITAL-WALLET", "WALLET" -> AccountType.BILLETERA_DIGITAL;
            default -> AccountType.fromString(t);
        };
    }

    private CategoryType parseCategoryType(String input) {
        String t = input.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "1", "INGRESO", "INCOME", "INGRESOS" -> CategoryType.INGRESO;
            case "2", "GASTO", "EXPENSE", "GASTOS" -> CategoryType.GASTO;
            case "3", "DEUDA", "DEBT", "DEUDAS", "DEBIT", "DEBITO" -> CategoryType.DEUDA;
            default -> CategoryType.fromString(t);
        };
    }

    private Short parseDayOrNull(String input) {
        String t = input.trim();
        if ("-".equals(t)) {
            return null;
        }
        try {
            short day = Short.parseShort(t);
            return (day >= 1 && day <= 31) ? day : null;
        } catch (NumberFormatException e) {
            return null;
        }
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

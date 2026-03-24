package com.financebot.service.telegram;

public final class TelegramDialogConstants {

    public static final String FLOW_EXPENSE = "EXPENSE";
    public static final String FLOW_INCOME = "INCOME";
    public static final String FLOW_DEBT_REGISTER = "DEBT_REGISTER";
    public static final String FLOW_CARD_CONSUMPTION = "CARD_CONSUMPTION";
    public static final String FLOW_CARD_PAYMENT = "CARD_PAYMENT";
    public static final String FLOW_DEBT_PAYMENT = "DEBT_PAYMENT";
    public static final String FLOW_TRANSFER = "TRANSFER";

    public static final String STEP_AMOUNT = "AMOUNT";
    public static final String STEP_CATEGORY = "CATEGORY";
    public static final String STEP_DATE = "DATE";
    public static final String STEP_PAYMENT_MODE = "PAYMENT_MODE";
    public static final String STEP_TARGET = "TARGET";
    public static final String STEP_DESCRIPTION = "DESCRIPTION";

    public static final String STEP_ACCOUNT = "ACCOUNT";
    public static final String STEP_ORIGIN = "ORIGIN";

    public static final String STEP_NAME = "NAME";
    public static final String STEP_TOTAL = "TOTAL";
    public static final String STEP_PENDING = "PENDING";
    public static final String STEP_DUE = "DUE";
    public static final String STEP_CREDITOR = "CREDITOR";
    public static final String STEP_DEBT_CATEGORY = "DEBT_CATEGORY";

    public static final String STEP_CARD = "CARD";
    public static final String STEP_DEBT = "DEBT";

    public static final String STEP_SOURCE = "SOURCE";
    public static final String STEP_DESTINATION = "DESTINATION";

    public static final String MODE_ACCOUNT = "A";
    public static final String MODE_CARD = "T";

    private TelegramDialogConstants() {
    }
}

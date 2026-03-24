package com.financebot.exception;

public class InsufficientBalanceException extends BusinessRuleException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}

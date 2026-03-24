package com.financebot.exception;

public class InsufficientCreditException extends BusinessRuleException {

    public InsufficientCreditException(String message) {
        super(message);
    }
}

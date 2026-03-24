package com.financebot.util;

import com.financebot.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUtilsTest {

    @Test
    void assertPositiveRejectsZero() {
        assertThrows(BusinessRuleException.class, () -> MoneyUtils.assertPositive(BigDecimal.ZERO));
    }

    @Test
    void atLeastZeroClampsNegative() {
        assertEquals(BigDecimal.ZERO.setScale(2), MoneyUtils.atLeastZero(new BigDecimal("-1.00")));
    }
}

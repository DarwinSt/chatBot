package com.financebot.repository;

import com.financebot.entity.Expense;
import com.financebot.enums.ExpenseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateAsc(LocalDate startInclusive, LocalDate endInclusive);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.expenseDate BETWEEN :start AND :end")
    BigDecimal sumAmountBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.expenseDate BETWEEN :start AND :end AND e.expenseType = :type
            """)
    BigDecimal sumAmountBetweenAndExpenseType(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("type") ExpenseType type);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.expenseDate BETWEEN :start AND :end AND e.paymentAccount IS NOT NULL
            """)
    BigDecimal sumAmountBetweenAndPaymentAccountPresent(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.expenseDate BETWEEN :start AND :end AND e.creditCard IS NOT NULL
            """)
    BigDecimal sumAmountBetweenAndCreditCardPresent(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("""
            SELECT c.id, c.name, COALESCE(SUM(e.amount), 0)
            FROM Expense e JOIN e.category c
            WHERE e.expenseDate BETWEEN :start AND :end
            GROUP BY c.id, c.name
            ORDER BY c.name
            """)
    List<Object[]> sumAmountGroupedByCategory(@Param("start") LocalDate start, @Param("end") LocalDate end);
}

package com.financebot.repository;

import com.financebot.entity.DebtPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, Long> {

    List<DebtPayment> findByPaymentDateBetweenOrderByPaymentDateAsc(LocalDate startInclusive, LocalDate endInclusive);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM DebtPayment p WHERE p.paymentDate BETWEEN :start AND :end")
    BigDecimal sumAmountBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}

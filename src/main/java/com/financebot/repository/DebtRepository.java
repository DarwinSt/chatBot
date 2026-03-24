package com.financebot.repository;

import com.financebot.entity.Debt;
import com.financebot.enums.DebtStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findAllByOrderByIdDesc();

    List<Debt> findAllByStatusOrderByDueDateAsc(DebtStatus status);

    List<Debt> findAllByStatusInOrderByDueDateAsc(List<DebtStatus> statuses);

    List<Debt> findAllByPendingAmountGreaterThanAndStatusInOrderByDueDateAsc(
            BigDecimal minPending, Collection<DebtStatus> statuses);

    @Query("""
            SELECT d FROM Debt d
            WHERE d.pendingAmount > 0
              AND d.status IN :statuses
              AND d.dueDate IS NOT NULL
              AND d.dueDate >= :fromInclusive
              AND d.dueDate <= :toInclusive
            ORDER BY d.dueDate
            """)
    List<Debt> findUpcomingByDueDateBetween(
            @Param("statuses") Collection<DebtStatus> statuses,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive);
}

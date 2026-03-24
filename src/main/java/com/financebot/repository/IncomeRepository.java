package com.financebot.repository;

import com.financebot.entity.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByIncomeDateBetweenOrderByIncomeDateAsc(LocalDate startInclusive, LocalDate endInclusive);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.incomeDate BETWEEN :start AND :end")
    BigDecimal sumAmountBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            SELECT c.id, c.name, COALESCE(SUM(i.amount), 0)
            FROM Income i JOIN i.category c
            WHERE i.incomeDate BETWEEN :start AND :end
            GROUP BY c.id, c.name
            ORDER BY c.name
            """)
    List<Object[]> sumAmountGroupedByCategory(@Param("start") LocalDate start, @Param("end") LocalDate end);
}

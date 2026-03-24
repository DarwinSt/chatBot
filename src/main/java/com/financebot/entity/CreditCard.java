package com.financebot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Entity
@Table(name = "credit_cards")
public class CreditCard extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @Column(name = "total_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalLimit;

    @NotNull
    @Column(name = "used_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal usedAmount;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** PostgreSQL SMALLINT (int2) — usar Short en JPA. */
    @Column(name = "statement_cutoff_day")
    private Short statementCutoffDay;

    /** PostgreSQL SMALLINT (int2) — usar Short en JPA. */
    @Column(name = "payment_due_day")
    private Short paymentDueDay;

    @Column(name = "notes", length = 500)
    private String notes;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getTotalLimit() {
        return totalLimit;
    }

    public void setTotalLimit(BigDecimal totalLimit) {
        this.totalLimit = totalLimit;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public void setUsedAmount(BigDecimal usedAmount) {
        this.usedAmount = usedAmount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Short getStatementCutoffDay() {
        return statementCutoffDay;
    }

    public void setStatementCutoffDay(Short statementCutoffDay) {
        this.statementCutoffDay = statementCutoffDay;
    }

    public Short getPaymentDueDay() {
        return paymentDueDay;
    }

    public void setPaymentDueDay(Short paymentDueDay) {
        this.paymentDueDay = paymentDueDay;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

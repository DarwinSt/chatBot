package com.financebot.repository;

import com.financebot.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByTransferDateBetweenOrderByTransferDateAsc(LocalDate startInclusive, LocalDate endInclusive);
}

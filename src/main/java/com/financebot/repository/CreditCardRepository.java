package com.financebot.repository;

import com.financebot.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    List<CreditCard> findAllByOrderByNameAsc();

    List<CreditCard> findAllByActiveTrueOrderByNameAsc();
}

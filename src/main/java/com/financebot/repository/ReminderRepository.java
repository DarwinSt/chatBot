package com.financebot.repository;

import com.financebot.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByActiveTrueAndReminderDateBetweenOrderByReminderDateAsc(
            LocalDate startInclusive, LocalDate endInclusive);
}

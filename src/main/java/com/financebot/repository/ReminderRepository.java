package com.financebot.repository;

import com.financebot.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByActiveTrueAndReminderDateBetweenOrderByReminderDateAsc(
            LocalDateTime startInclusive, LocalDateTime endInclusive);
}

package com.example.iotlab.repository;

import com.example.iotlab.model.EmailNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailNotificationLogRepository
        extends JpaRepository<EmailNotificationLog, Long> {

    // Check if a warning email was already sent for this borrow record
    boolean existsByBorrowIdAndEmailType(Long borrowId, String emailType);

    // All logs for audit/display (newest first)
    List<EmailNotificationLog> findAllByOrderByEmailSentDateDesc();
}

package com.example.iotlab.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iotlab.model.EmailNotificationLog;
import com.example.iotlab.repository.EmailNotificationLogRepository;
import com.example.iotlab.service.OverdueEmailService;

@RestController
@RequestMapping("/api/email")
@CrossOrigin(origins = "*")
public class EmailController {

    @Autowired
    private OverdueEmailService overdueEmailService;
    @Autowired
    private EmailNotificationLogRepository emailLogRepository;

    /**
     * GET /api/email/logs Returns all sent email notifications (newest first).
     * Used by the admin dashboard email log panel.
     */
    @GetMapping("/logs")
    public List<EmailNotificationLog> getEmailLogs() {
        return emailLogRepository.findAllByOrderByEmailSentDateDesc();
    }

    /**
     * POST /api/email/trigger Manually triggers the overdue email check
     * immediately. Useful for testing without waiting for the 8 AM cron.
     */
    @PostMapping("/trigger")
    public ResponseEntity<String> triggerNow() {
        overdueEmailService.sendOverdueWarningEmails();
        return ResponseEntity.ok("Overdue email check completed.");
    }
}

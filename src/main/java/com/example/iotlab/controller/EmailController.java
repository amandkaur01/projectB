package com.example.iotlab.controller;

import java.util.ArrayList;
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

    @Autowired(required = false)
    private OverdueEmailService overdueEmailService;

    @Autowired(required = false)
    private EmailNotificationLogRepository emailLogRepository;

    /**
     * GET /api/email/logs Returns all sent email notifications.
     */
    @GetMapping("/logs")
    public List<EmailNotificationLog> getEmailLogs() {
        if (emailLogRepository == null) {
            return new ArrayList<>();
        }
        try {
            return emailLogRepository.findAllByOrderByEmailSentDateDesc();
        } catch (Exception e) {
            System.err.println("[EmailController] Could not fetch logs: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * POST /api/email/trigger Manually triggers the overdue email check.
     */
    @PostMapping("/trigger")
    public ResponseEntity<String> triggerNow() {
        if (overdueEmailService == null) {
            return ResponseEntity.ok("Email service not configured. Emails run automatically at 8 AM.");
        }
        try {
            overdueEmailService.triggerManual(); // force-sends to all current overdue
            return ResponseEntity.ok("✅ Overdue email check completed. All overdue students have been notified.");
        } catch (Exception e) {
            System.err.println("[EmailController] Trigger failed: " + e.getMessage());
            return ResponseEntity.ok("Email check ran but encountered an issue: " + e.getMessage());
        }
    }
}

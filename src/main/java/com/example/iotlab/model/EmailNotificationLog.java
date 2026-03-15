package com.example.iotlab.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Tracks every overdue fine warning attempt — both successful and failed.
 * status values: "SENT" — email delivered successfully "FAILED" — email address
 * exists but sending failed (SMTP error) "NO_EMAIL" — student has no registered
 * email in the system
 */
@Entity
public class EmailNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long borrowId;
    private String studentEmail;   // null if NO_EMAIL
    private String equipmentName;
    private LocalDate emailSentDate;
    private String emailType;      // "OVERDUE_WARNING"
    private String status;         // "SENT" | "FAILED" | "NO_EMAIL"
    private String failureReason;  // populated on FAILED/NO_EMAIL

    public EmailNotificationLog() {
    }

    public EmailNotificationLog(Long borrowId, String studentEmail,
            String equipmentName, LocalDate emailSentDate,
            String emailType, String status,
            String failureReason) {
        this.borrowId = borrowId;
        this.studentEmail = studentEmail;
        this.equipmentName = equipmentName;
        this.emailSentDate = emailSentDate;
        this.emailType = emailType;
        this.status = status;
        this.failureReason = failureReason;
    }

    public Long getId() {
        return id;
    }

    public Long getBorrowId() {
        return borrowId;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public LocalDate getEmailSentDate() {
        return emailSentDate;
    }

    public String getEmailType() {
        return emailType;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setBorrowId(Long b) {
        this.borrowId = b;
    }

    public void setStudentEmail(String e) {
        this.studentEmail = e;
    }

    public void setEquipmentName(String n) {
        this.equipmentName = n;
    }

    public void setEmailSentDate(LocalDate d) {
        this.emailSentDate = d;
    }

    public void setEmailType(String t) {
        this.emailType = t;
    }

    public void setStatus(String s) {
        this.status = s;
    }

    public void setFailureReason(String r) {
        this.failureReason = r;
    }
}

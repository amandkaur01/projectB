package com.example.iotlab.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.iotlab.model.Borrow;
import com.example.iotlab.model.EmailNotificationLog;
import com.example.iotlab.model.User;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EmailNotificationLogRepository;
import com.example.iotlab.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
@EnableScheduling
public class OverdueEmailService {

    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private BorrowRepository borrowRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailNotificationLogRepository emailLogRepository;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${lab.mail.sender-name:Smart Lab Equipment System}")
    private String senderName;

    private static final DateTimeFormatter DATE_FMT
            = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── Scheduled: runs daily at 8 AM ─────────────────────────────────────
    // Skips borrows that already received a warning email
    @Scheduled(cron = "0 0 8 * * *")
    public void sendOverdueWarningEmails() {
        processOverdue(false); // false = respect existing logs (don't resend)
    }

    // ── Manual trigger: called by Run Now button ───────────────────────────
    // Force-sends to ALL currently overdue borrows regardless of previous emails
    public void triggerManual() {
        processOverdue(true); // true = force send even if already notified
    }

    // ── Core logic ────────────────────────────────────────────────────────
    private void processOverdue(boolean forceResend) {
        List<Borrow> allBorrows = borrowRepository.findAll();
        LocalDate today = LocalDate.now();
        int emailsSent = 0;

        for (Borrow borrow : allBorrows) {

            // Skip fully returned
            if ("RETURNED".equals(borrow.getStatus())) {
                continue;
            }
            if (borrow.getQuantity() > 0
                    && borrow.getReturnedQuantity() >= borrow.getQuantity()) {
                continue;
            }

            // Only process overdue borrows
            LocalDate dueDate = borrow.getDueDate();
            if (dueDate == null || !dueDate.isBefore(today)) {
                continue;
            }

            // If not force-resend, skip if already notified
            if (!forceResend && emailLogRepository.existsByBorrowIdAndEmailType(
                    borrow.getId(), "OVERDUE_WARNING")) {
                continue;
            }

            // Find student email
            String studentEmail = resolveStudentEmail(borrow.getStudentName());

            String status;
            String failureReason = null;

            if (studentEmail == null) {
                status = "NO_EMAIL";
                failureReason = "Student '" + borrow.getStudentName() + "' has no registered email";
            } else {
                String sendResult = sendFineWarningEmail(
                        studentEmail,
                        borrow.getStudentName(),
                        borrow.getEquipmentName(),
                        borrow.getQuantity() - borrow.getReturnedQuantity(),
                        dueDate,
                        borrow.getId()
                );
                status = (sendResult == null) ? "SENT" : "FAILED";
                failureReason = sendResult;
            }

            // Log the attempt
            emailLogRepository.save(new EmailNotificationLog(
                    borrow.getId(),
                    studentEmail != null ? studentEmail : "N/A — not registered",
                    borrow.getEquipmentName(),
                    today,
                    "OVERDUE_WARNING",
                    status,
                    failureReason
            ));

            emailsSent++;
        }

        System.out.println("[SmartLab] Overdue emails processed: " + emailsSent
                + (forceResend ? " (manual trigger)" : " (scheduled)"));
    }

    // ── Send email — returns null on success, error string on failure ──────
    public String sendFineWarningEmail(String toEmail, String studentName,
            String equipmentName, int qtyOutstanding,
            LocalDate dueDate, Long borrowId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setTo(toEmail);
            helper.setSubject("⚠️ Overdue Equipment Notice — Fine Warning | Smart Lab");
            helper.setText(buildEmailHtml(studentName, equipmentName,
                    qtyOutstanding, dueDate, borrowId), true);
            mailSender.send(message);
            return null; // success
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : "Unknown SMTP error";
            System.err.println("[SmartLab] Email failed to " + toEmail + ": " + reason);
            return reason;
        }
    }

    private String resolveStudentEmail(String studentName) {
        if (studentName == null) {
            return null;
        }
        return userRepository.findByNameIgnoreCase(studentName.trim())
                .map(User::getEmail).orElse(null);
    }

    // ── HTML Email Template ───────────────────────────────────────────────
    private String buildEmailHtml(String studentName, String equipmentName,
            int qty, LocalDate dueDate, Long borrowId) {
        String dueDateStr = dueDate.format(DATE_FMT);
        String deadlineStr = dueDate.plusDays(2).format(DATE_FMT);
        String todayStr = LocalDate.now().format(DATE_FMT);
        long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Arial,sans-serif;background:#f4fffe;margin:0;padding:0}"
                + ".wrap{max-width:600px;margin:30px auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(13,148,136,0.15)}"
                + ".header{background:linear-gradient(135deg,#134e4a,#0d9488);padding:36px 40px;text-align:center}"
                + ".header h1{color:#fff;margin:0;font-size:22px;font-weight:800}"
                + ".header p{color:rgba(255,255,255,0.75);margin:6px 0 0;font-size:13px}"
                + ".body{padding:36px 40px}"
                + ".greeting{font-size:16px;color:#0d3330;font-weight:600;margin-bottom:12px}"
                + ".info-box{background:#fff5f5;border:1.5px solid #fecaca;border-radius:12px;padding:20px 24px;margin:20px 0}"
                + ".info-box h2{color:#b91c1c;font-size:16px;margin:0 0 14px}"
                + ".info-row{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #fee2e2;font-size:14px}"
                + ".info-row:last-child{border-bottom:none}"
                + ".info-label{color:#6b7280;font-weight:500}"
                + ".info-value{color:#111827;font-weight:700}"
                + ".fine-box{background:linear-gradient(135deg,#134e4a,#0f766e);border-radius:12px;padding:24px;margin:24px 0;text-align:center;color:#fff}"
                + ".fine-box .amount{font-size:48px;font-weight:900;margin:8px 0}"
                + ".fine-box .label{font-size:13px;opacity:0.8;text-transform:uppercase;letter-spacing:0.5px}"
                + ".deadline-box{background:#fef3c7;border:1.5px solid #fde68a;border-radius:12px;padding:16px 24px;margin:20px 0;text-align:center}"
                + ".deadline-box .title{color:#92400e;font-size:13px;font-weight:600;text-transform:uppercase;margin-bottom:4px}"
                + ".deadline-box .date{color:#78350f;font-size:22px;font-weight:800}"
                + ".borrow-id{background:#ccfbf1;color:#134e4a;padding:2px 8px;border-radius:6px;font-weight:700;font-size:12px}"
                + ".footer{background:#f4fffe;padding:20px 40px;text-align:center;border-top:1px solid #ccfbf1}"
                + ".footer p{color:#6b7280;font-size:12px;margin:4px 0}"
                + "</style></head><body>"
                + "<div class='wrap'>"
                + "<div class='header'><h1>🔬 Smart Lab Equipment System</h1>"
                + "<p>Overdue Equipment — Fine Warning Notice</p></div>"
                + "<div class='body'>"
                + "<p class='greeting'>Dear " + studentName + ",</p>"
                + "<p style='color:#374151;font-size:14px;line-height:1.7;'>The lab equipment borrowed under your account is <strong>overdue</strong>. Please return it immediately to avoid a financial penalty.</p>"
                + "<div class='info-box'><h2>⚠️ Overdue Borrow Details</h2>"
                + "<div class='info-row'><span class='info-label'>Borrow ID</span><span class='info-value'><span class='borrow-id'>#" + borrowId + "</span></span></div>"
                + "<div class='info-row'><span class='info-label'>Equipment</span><span class='info-value'>" + equipmentName + "</span></div>"
                + "<div class='info-row'><span class='info-label'>Quantity Outstanding</span><span class='info-value'>" + qty + " unit(s)</span></div>"
                + "<div class='info-row'><span class='info-label'>Due Date</span><span class='info-value' style='color:#b91c1c'>" + dueDateStr + "</span></div>"
                + "<div class='info-row'><span class='info-label'>Days Overdue</span><span class='info-value' style='color:#b91c1c'>" + daysOverdue + " day(s)</span></div>"
                + "<div class='info-row'><span class='info-label'>Notice Sent On</span><span class='info-value'>" + todayStr + "</span></div>"
                + "</div>"
                + "<div class='fine-box'><div class='label'>Fine if not returned within 2 days</div>"
                + "<div class='amount'>₹500</div><div style='font-size:13px;opacity:0.85;'>per overdue borrow record</div></div>"
                + "<div class='deadline-box'><div class='title'>⏰ Return Deadline to Avoid Fine</div>"
                + "<div class='date'>" + deadlineStr + "</div></div>"
                + "<p style='font-size:14px;font-weight:700;color:#0d3330;margin-bottom:14px;'>How to return equipment:</p>"
                + "<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:12px'>"
                + "<tr><td width='32' style='text-align:center;vertical-align:middle;padding-bottom:10px'><div style='width:28px;height:28px;background:#0d9488;color:#fff;border-radius:50%;font-size:13px;font-weight:700;text-align:center;line-height:28px'>1</div></td>"
                + "<td style='font-size:14px;color:#374151;padding-left:12px;padding-bottom:10px;vertical-align:middle'>Log in to the Smart Lab System</td></tr>"
                + "<tr><td width='32' style='text-align:center;vertical-align:middle;padding-bottom:10px'><div style='width:28px;height:28px;background:#0d9488;color:#fff;border-radius:50%;font-size:13px;font-weight:700;text-align:center;line-height:28px'>2</div></td>"
                + "<td style='font-size:14px;color:#374151;padding-left:12px;padding-bottom:10px;vertical-align:middle'>Go to <strong>Return Equipment</strong> from your Student Dashboard</td></tr>"
                + "<tr><td width='32' style='text-align:center;vertical-align:middle;padding-bottom:10px'><div style='width:28px;height:28px;background:#0d9488;color:#fff;border-radius:50%;font-size:13px;font-weight:700;text-align:center;line-height:28px'>3</div></td>"
                + "<td style='font-size:14px;color:#374151;padding-left:12px;padding-bottom:10px;vertical-align:middle'>Enter Borrow ID <span class='borrow-id'>#" + borrowId + "</span> and the quantity returning</td></tr>"
                + "<tr><td width='32' style='text-align:center;vertical-align:middle'><div style='width:28px;height:28px;background:#0d9488;color:#fff;border-radius:50%;font-size:13px;font-weight:700;text-align:center;line-height:28px'>4</div></td>"
                + "<td style='font-size:14px;color:#374151;padding-left:12px;vertical-align:middle'>Submit — your record will be updated immediately</td></tr>"
                + "</table>"
                + "<p style='font-size:13px;color:#6b7280;margin-top:24px;line-height:1.6;'>If you believe this is an error or have already returned the equipment, please contact the lab administrator.</p>"
                + "</div>"
                + "<div class='footer'><p>This is an automated message from Smart Lab Equipment System.</p>"
                + "<p>Please do not reply to this email.</p></div>"
                + "</div></body></html>";
    }
}

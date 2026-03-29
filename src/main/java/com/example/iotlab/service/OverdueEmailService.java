package com.example.iotlab.service;

import com.sendgrid.SendGrid;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.Method;

import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Content;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.iotlab.model.Borrow;
import com.example.iotlab.model.EmailNotificationLog;
import com.example.iotlab.model.User;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EmailNotificationLogRepository;
import com.example.iotlab.repository.UserRepository;

@Service
@EnableScheduling
public class OverdueEmailService {

    @Autowired
    private BorrowRepository borrowRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailNotificationLogRepository emailLogRepository;

    @Value("${sendgrid.api.key}")
    private String apiKey;

    private static final DateTimeFormatter DATE_FMT
            = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // 🔹 Scheduled
    @Scheduled(cron = "0 0 8 * * *")
    public void sendOverdueWarningEmails() {
        processOverdue(false);
    }

    // 🔹 Manual trigger
    public void triggerManual() {
        processOverdue(true);
    }

    private void processOverdue(boolean forceResend) {

        List<Borrow> allBorrows = borrowRepository.findAll();
        LocalDate today = LocalDate.now();
        int emailsSent = 0;

        for (Borrow borrow : allBorrows) {

            if ("RETURNED".equals(borrow.getStatus())) {
                continue;
            }

            if (borrow.getQuantity() > 0
                    && borrow.getReturnedQuantity() >= borrow.getQuantity()) {
                continue;
            }

            LocalDate dueDate = borrow.getDueDate();
            if (dueDate == null || !dueDate.isBefore(today)) {
                continue;
            }

            if (!forceResend && emailLogRepository.existsByBorrowIdAndEmailType(
                    borrow.getId(), "OVERDUE_WARNING")) {
                continue;
            }

            String studentEmail = resolveStudentEmail(borrow.getStudentName());

            String status;
            String failureReason = null;

            if (studentEmail == null) {
                status = "NO_EMAIL";
                failureReason = "No email found";
            } else {
                String result = sendEmail(
                        studentEmail,
                        "⚠️ Overdue Equipment Notice",
                        buildEmailHtml(
                                borrow.getStudentName(),
                                borrow.getEquipmentName(),
                                borrow.getQuantity() - borrow.getReturnedQuantity(),
                                dueDate,
                                borrow.getId()
                        )
                );

                status = (result == null) ? "SENT" : "FAILED";
                failureReason = result;
            }

            emailLogRepository.save(new EmailNotificationLog(
                    borrow.getId(),
                    studentEmail != null ? studentEmail : "N/A",
                    borrow.getEquipmentName(),
                    today,
                    "OVERDUE_WARNING",
                    status,
                    failureReason
            ));

            emailsSent++;
        }

        System.out.println("Emails processed: " + emailsSent);
    }

    // 🚀 SENDGRID EMAIL METHOD
    public String sendEmail(String to, String subject, String htmlContent) {
        try {
            Email from = new Email("amankkaur064@gmail.com"); // VERIFIED EMAIL
            Email toEmail = new Email(to);

            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, toEmail, content);

            SendGrid sg = new SendGrid(apiKey);
            Request request = new Request();

            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            System.out.println("SendGrid Status: " + response.getStatusCode());

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private String resolveStudentEmail(String studentName) {
        if (studentName == null) {
            return null;
        }

        return userRepository.findByNameIgnoreCase(studentName.trim())
                .map(User::getEmail).orElse(null);
    }

    // 🔥 KEEP YOUR EXISTING HTML METHOD SAME
    private String buildEmailHtml(String studentName, String equipmentName,
            int qty, LocalDate dueDate, Long borrowId) {

        String dueDateStr = dueDate.format(DATE_FMT);

        return "<h2>Overdue Equipment Notice</h2>"
                + "<p>Dear " + studentName + ",</p>"
                + "<p>You have overdue equipment:</p>"
                + "<ul>"
                + "<li><b>Equipment:</b> " + equipmentName + "</li>"
                + "<li><b>Quantity:</b> " + qty + "</li>"
                + "<li><b>Due Date:</b> " + dueDateStr + "</li>"
                + "</ul>"
                + "<p>Please return it immediately to avoid fine.</p>";
    }
}

package com.jing.monitor.service;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Email notification service for enrollment state alerts.
 */
@Service
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    /**
     * Creates mail service with Spring-managed sender.
     *
     * @param mailSender JavaMail sender bean
     */
    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an email alert when a section becomes OPEN.
     *
     * @param recipientEmail recipient mailbox
     * @param section section id
     * @param courseInfo course display text
     */
    public void sendCourseOpenAlert(String recipientEmail, String section, String courseInfo) {
        log.info("[Mail] Preparing to send OPEN alert for section {} to {}", section, recipientEmail);

        try {
            // Encode the courseInfo (e.g. "comp sci 577" -> "comp+sci+577") to build a valid URL
            String encodedCourse = URLEncoder.encode(courseInfo, StandardCharsets.UTF_8);
            String enrollLink = "https://enroll.wisc.edu/search?keywords=" + encodedCourse;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(requireRecipientEmail(recipientEmail));

            // Optimized Subject
            message.setSubject("🟢 OPEN SEAT ALERT: " + courseInfo + " (Section " + section + ")");

            // Optimized Body
            message.setText(
                    "Great news!\n\n" +
                            "A seat has just opened up for your monitored course.\n\n" +
                            "📚 Course: " + courseInfo + "\n" +
                            "🔖 Section: " + section + "\n" +
                            "🟢 Status: OPEN\n\n" +
                            "Click the link below to go directly to Course Search & Enroll:\n" +
                            "👉 " + enrollLink + "\n\n" +
                            "Fingers crossed for your enrollment!\n\n" +
                            "---\n" +
                            "Automated alert from MadEnroll"
            );

            mailSender.send(message);
            log.info("[Mail] OPEN alert email sent successfully for section {} to {}", section, recipientEmail);
        } catch (Exception e) {
            log.error("[Mail] Failed to send OPEN alert email for section {} to {}", section, recipientEmail, e);
            throw new IllegalStateException("Failed to send OPEN alert email.", e);
        }
    }

    /**
     * Sends an email alert when a section becomes WAITLISTED.
     *
     * @param recipientEmail recipient mailbox
     * @param section section id
     * @param courseInfo course display text
     */
    public void sendCourseWaitlistedAlert(String recipientEmail, String section, String courseInfo) {
        log.info("[Mail] Preparing to send WAITLIST alert for section {} to {}", section, recipientEmail);

        try {
            // Encode the courseInfo (e.g. "comp sci 577" -> "comp+sci+577") to build a valid URL
            String encodedCourse = URLEncoder.encode(courseInfo, StandardCharsets.UTF_8);
            String enrollLink = "https://enroll.wisc.edu/search?keywords=" + encodedCourse;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(requireRecipientEmail(recipientEmail));

            // Optimized Subject
            message.setSubject("🟡 WAITLIST ALERT: " + courseInfo + " (Section " + section + ")");

            // Optimized Body
            message.setText(
                    "Hello,\n\n" +
                            "A waitlist spot has just opened up for your monitored course.\n\n" +
                            "📚 Course: " + courseInfo + "\n" +
                            "🔖 Section: " + section + "\n" +
                            "🟡 Status: WAITLISTED\n\n" +
                            "Click the link below to secure your spot via Course Search & Enroll:\n" +
                            "👉 " + enrollLink + "\n\n" +
                            "Fingers crossed for your enrollment!\n\n" +
                            "---\n" +
                            "Automated alert from MadEnroll"
            );

            mailSender.send(message);
            log.info("[Mail] WAITLIST alert email sent successfully for section {} to {}", section, recipientEmail);
        } catch (Exception e) {
            log.error("[Mail] Failed to send WAITLIST alert email for section {} to {}", section, recipientEmail, e);
            throw new IllegalStateException("Failed to send WAITLIST alert email.", e);
        }
    }

    /**
     * Sends a welcome email to a newly registered user.
     *
     * @param recipientEmail recipient mailbox
     */
    public void sendWelcomeEmail(String recipientEmail) {
        log.info("[Mail] Preparing to send welcome email to {}", recipientEmail);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(requireRecipientEmail(recipientEmail));
            message.setSubject("Welcome to MadEnroll");
            message.setText(
                    "Welcome to MadEnroll!\n\n" +
                            "Your account has been created successfully.\n\n" +
                            "You can now:\n" +
                            "- search courses across supported subjects\n" +
                            "- subscribe to specific sections\n" +
                            "- receive automatic OPEN and WAITLIST alerts by email\n\n" +
                            "We are excited to help you track the classes you care about.\n\n" +
                            "---\n" +
                            "Automated message from MadEnroll"
            );

            mailSender.send(message);
            log.info("[Mail] Welcome email sent successfully to {}", recipientEmail);
        } catch (Exception e) {
            log.error("[Mail] Failed to send welcome email to {}", recipientEmail, e);
            throw new IllegalStateException("Failed to send welcome email.", e);
        }
    }

    private String requireRecipientEmail(String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }
        return Objects.requireNonNull(recipientEmail).trim().toLowerCase();
    }
}

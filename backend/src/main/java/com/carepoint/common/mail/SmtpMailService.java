package com.carepoint.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * SMTP provider (e.g. Gmail with an app password). Activated by
 * MAIL_PROVIDER=smtp together with SMTP_HOST/PORT/USER/PASS env vars.
 */
@Service
@ConditionalOnProperty(name = "carepoint.mail.provider", havingValue = "smtp")
public class SmtpMailService implements MailService {

    private final JavaMailSender sender;

    public SmtpMailService(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}

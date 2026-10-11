package com.carepoint.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default mail provider: writes outgoing mail to the application log.
 * Keeps the full OTP flow usable in dev and CI without a mail account.
 */
@Service
@ConditionalOnProperty(name = "carepoint.mail.provider", havingValue = "log", matchIfMissing = true)
public class LogMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LogMailService.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("MAIL(log-provider) to={} subject={}\n{}", to, subject, body);
    }
}

package com.carepoint.clinic.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;

/**
 * Secure first-admin provisioning. Runs only when BOOTSTRAP_ADMIN_EMAIL is
 * set in the server environment: targets an existing clinic by slug, or
 * creates one from BOOTSTRAP_CLINIC_NAME/TIMEZONE, then mails the admin a
 * single-use invitation link. No password ever passes through configuration.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final ClinicProvisioningService provisioning;
    private final ClinicRepository clinics;
    private final String adminEmail;
    private final String adminName;
    private final String clinicSlug;
    private final String clinicName;
    private final String clinicTimezone;

    public AdminBootstrapRunner(ClinicProvisioningService provisioning,
                                ClinicRepository clinics,
                                @Value("${carepoint.bootstrap.admin-email:}") String adminEmail,
                                @Value("${carepoint.bootstrap.admin-name:}") String adminName,
                                @Value("${carepoint.bootstrap.clinic-slug:}") String clinicSlug,
                                @Value("${carepoint.bootstrap.clinic-name:}") String clinicName,
                                @Value("${carepoint.bootstrap.clinic-timezone:}") String clinicTimezone) {
        this.provisioning = provisioning;
        this.clinics = clinics;
        this.adminEmail = adminEmail.strip();
        this.adminName = adminName.strip();
        this.clinicSlug = clinicSlug.strip();
        this.clinicName = clinicName.strip();
        this.clinicTimezone = clinicTimezone.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isEmpty()) {
            return;
        }
        UUID clinicId = resolveClinic();
        var result = provisioning.ensureAdmin(clinicId, adminEmail,
                adminName.isEmpty() ? "Clinic Administrator" : adminName);
        log.info("Clinic admin bootstrap clinic={} result={}", clinicId, result);
    }

    private UUID resolveClinic() {
        if (!clinicSlug.isEmpty()) {
            return clinics.findBySlug(clinicSlug).map(Clinic::getId)
                    .orElseThrow(() -> new IllegalStateException(
                            "BOOTSTRAP_CLINIC_SLUG does not match any clinic: " + clinicSlug));
        }
        if (clinicName.isEmpty() || clinicTimezone.isEmpty()) {
            throw new IllegalStateException(
                    "Set BOOTSTRAP_CLINIC_SLUG, or BOOTSTRAP_CLINIC_NAME and BOOTSTRAP_CLINIC_TIMEZONE");
        }
        return provisioning.findOrCreateClinic(clinicName, clinicTimezone).getId();
    }
}

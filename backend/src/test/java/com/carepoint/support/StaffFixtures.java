package com.carepoint.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.carepoint.clinic.application.ClinicProvisioningService;
import com.carepoint.clinic.domain.Clinic;
import com.carepoint.common.mail.MailService;
import com.carepoint.identity.domain.ClinicMembership;
import com.carepoint.identity.persistence.ClinicMembershipRepository;

/** Clinic provisioning has no public endpoint, so tests seed admins through the operator service. */
public final class StaffFixtures {

    public static final String PASSWORD = "Correct-Horse-9";
    private static final Pattern INVITE_TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private StaffFixtures() {
    }

    public static String createClinicAs(MockMvc mvc, ClinicProvisioningService provisioning,
                                        ClinicMembershipRepository memberships, Cookie access) throws Exception {
        return createClinicAs(mvc, provisioning, memberships, access,
                "Clinic " + UUID.randomUUID().toString().substring(0, 8));
    }

    public static String createClinicAs(MockMvc mvc, ClinicProvisioningService provisioning,
                                        ClinicMembershipRepository memberships, Cookie access,
                                        String name) throws Exception {
        String me = mvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(me.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        Clinic clinic = provisioning.createClinic(name, "UTC", null);
        memberships.save(new ClinicMembership(clinic.getId(), userId, "CLINIC_ADMIN"));
        return clinic.getId().toString();
    }

    public static String lastInviteTokenSentTo(MailService mail, String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), body.capture());
        List<String> all = body.getAllValues();
        Matcher m = INVITE_TOKEN.matcher(all.get(all.size() - 1));
        assertThat(m.find()).as("invitation link present in mail body").isTrue();
        return m.group(1);
    }

    public static void acceptInvite(MockMvc mvc, MailService mail, String email) throws Exception {
        mvc.perform(post("/api/v1/invitations/accept")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s","confirmPassword":"%s"}
                                """.formatted(lastInviteTokenSentTo(mail, email), PASSWORD, PASSWORD)))
                .andExpect(status().isNoContent());
    }
}

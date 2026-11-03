package com.carepoint.clinic.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.clinic.api.dto.AcceptInvitationRequest;
import com.carepoint.clinic.api.dto.InvitationTokenRequest;
import com.carepoint.clinic.application.StaffInvitationService;

/** Public, rate-limited. Tokens travel in POST bodies only — never in URLs or logs. */
@RestController
@RequestMapping("/api/v1/invitations")
public class StaffInvitationController {

    private final StaffInvitationService invitations;

    public StaffInvitationController(StaffInvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/preview")
    public StaffInvitationService.InvitationPreview preview(@Valid @RequestBody InvitationTokenRequest req) {
        return invitations.preview(req.token());
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> accept(@Valid @RequestBody AcceptInvitationRequest req) {
        invitations.accept(req.token(), req.password(), req.confirmPassword());
        return ResponseEntity.noContent().build();
    }
}

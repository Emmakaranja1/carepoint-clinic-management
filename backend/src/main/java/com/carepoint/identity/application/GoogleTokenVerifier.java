package com.carepoint.identity.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;

/**
 * Verifies a Google ID token by calling Google's tokeninfo endpoint and
 * checking the audience matches our OAuth client id. Simple and correct
 * for MVP volume; swap for google-api-client's offline verification if
 * latency or call volume ever justifies it.
 */
@Component
public class GoogleTokenVerifier implements GoogleIdentityVerifier {

    private final RestClient http = RestClient.create();
    private final String clientId;

    public GoogleTokenVerifier(@Value("${carepoint.google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID, "Google sign-in is not configured");
        }
        TokenInfo info;
        try {
            info = http.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken)
                    .retrieve()
                    .body(TokenInfo.class);
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID, "Invalid Google token");
        }
        if (info == null || !clientId.equals(info.aud())) {
            throw new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID, "Invalid Google token");
        }
        return new GoogleIdentity(info.email(), info.name(), "true".equals(info.email_verified()));
    }

    private record TokenInfo(String aud, String email, String name, String email_verified) {
    }
}

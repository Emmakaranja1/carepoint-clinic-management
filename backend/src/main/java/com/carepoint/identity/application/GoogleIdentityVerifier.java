package com.carepoint.identity.application;

public interface GoogleIdentityVerifier {

    GoogleIdentity verify(String idToken);

    record GoogleIdentity(String email, String name, boolean emailVerified) {
    }
}

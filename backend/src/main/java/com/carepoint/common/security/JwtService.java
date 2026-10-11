package com.carepoint.common.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.carepoint.common.config.AuthProperties;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties props;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder, AuthProperties props) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.props = props;
    }

    public String issueAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwtIssuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .claim("email", email)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /**
     * @throws org.springframework.security.oauth2.jwt.JwtException on any
     *         signature, expiry or structure problem — callers map to 401.
     */
    public Jwt verify(String token) {
        return decoder.decode(token);
    }
}

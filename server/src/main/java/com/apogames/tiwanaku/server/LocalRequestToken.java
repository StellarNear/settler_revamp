package com.apogames.tiwanaku.server;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public final class LocalRequestToken {
    private final String value;

    public LocalRequestToken() {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        value = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public String value() {
        return value;
    }

    public boolean matches(String candidate) {
        return candidate != null
                && MessageDigest.isEqual(
                        value.getBytes(StandardCharsets.US_ASCII),
                        candidate.getBytes(StandardCharsets.US_ASCII));
    }
}

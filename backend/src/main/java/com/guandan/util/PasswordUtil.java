package com.guandan.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hash(String rawPassword) {
        String salt = generateSalt();
        return salt + ":" + sha256(salt + rawPassword);
    }

    public static boolean matches(String rawPassword, String storedHash) {
        String[] parts = storedHash.split(":", 2);
        if (parts.length != 2) return false;
        return storedHash.equals(parts[0] + ":" + sha256(parts[0] + rawPassword));
    }

    private static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }
}
// Regression check: password encoding verification point added
// Chore: password util configuration wrap-up

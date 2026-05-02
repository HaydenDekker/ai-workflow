package com.hdekker.ai_workflow.domain.shared;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Pure SHA-256 hash utility for file content.
 * <p>
 * Domain-level — no I/O, no framework dependencies.
 * Used by the scanner service for change detection.
 */
public final class FileHash {

    private FileHash() {
        // Utility class — no instantiation
    }

    /**
     * Computes the SHA-256 hash of the given content string.
     *
     * @param content the string content to hash
     * @return hex-encoded SHA-256 hash
     */
    public static String hash(String content) {
        MessageDigest instance;
        try {
            instance = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVMs
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
        byte[] hashBytes = instance.digest(content.getBytes());

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

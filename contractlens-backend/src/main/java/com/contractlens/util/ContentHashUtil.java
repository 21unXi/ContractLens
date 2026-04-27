package com.contractlens.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ContentHashUtil {

    private ContentHashUtil() {
    }

    public static String sha256HexNormalized(String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return null;
        }
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            return toHexLower(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String toHexLower(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            hex[i++] = toHexChar(v >>> 4);
            hex[i++] = toHexChar(v & 0x0F);
        }
        return new String(hex);
    }

    private static char toHexChar(int halfByte) {
        return (char) (halfByte < 10 ? ('0' + halfByte) : ('a' + (halfByte - 10)));
    }
}


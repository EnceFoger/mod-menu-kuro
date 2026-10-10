package com.kuro.companionctl;

import java.util.Locale;

/** Pure helpers for license keys and tokens (no Android dependencies, unit-tested). */
final class LicenseToken {
    private LicenseToken() { }

    /** Upper-case, keep only A-Z, 0-9 and '-'. Pasted spaces, quotes and line breaks disappear. */
    static String normalizeKey(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toUpperCase(Locale.US).toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-') sb.append(c);
        }
        return sb.toString();
    }

    static boolean looksLikeKey(String key) {
        return key != null && key.length() >= 8 && key.length() <= 64 && key.matches("[A-Z0-9-]+");
    }

    /** Keeps [A-Za-z0-9_-]; null when fewer than 8 characters remain. */
    static String sanitizeDeviceId(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') sb.append(c);
        }
        return sb.length() >= 8 && sb.length() <= 128 ? sb.toString() : null;
    }

    /** JSON request body, or null when an input is invalid. Both values are restricted to safe characters. */
    static String activateBody(String key, String deviceId) {
        if (!looksLikeKey(key) || sanitizeDeviceId(deviceId) == null || !sanitizeDeviceId(deviceId).equals(deviceId)) return null;
        return "{\"action\":\"activate\",\"key\":\"" + key + "\",\"device_id\":\"" + deviceId + "\"}";
    }

    /** Expiry (unix seconds) of a token "KURO1|package|device|expiry\nsignature", or -1 when malformed. */
    static long expiry(String token) {
        if (token == null) return -1;
        int nl = token.indexOf('\n');
        if (nl <= 0) return -1;
        String[] f = token.substring(0, nl).split("\\|", -1);
        if (f.length != 4 || !"KURO1".equals(f[0]) || f[3].isEmpty() || f[3].length() > 12) return -1;
        for (char c : f[3].toCharArray()) if (c < '0' || c > '9') return -1;
        try { return Long.parseLong(f[3]); } catch (NumberFormatException e) { return -1; }
    }

    /** True when the token is well-formed and still valid for at least minRemainingSec more seconds. */
    static boolean isFresh(String token, long nowSec, long minRemainingSec) {
        long exp = expiry(token);
        return exp > 0 && exp - nowSec > minRemainingSec;
    }

    static String message(String code) {
        if (code == null) return "Terjadi kesalahan.";
        switch (code) {
            case "invalid_key":    return "Key tidak valid.";
            case "revoked":        return "Key ini sudah dicabut.";
            case "expired":        return "Key ini sudah kedaluwarsa.";
            case "other_device":   return "Key ini sudah dipakai di perangkat lain.";
            case "invalid_input":  return "Format key tidak valid.";
            case "network":        return "Tidak bisa terhubung ke server. Periksa internet Anda.";
            case "not_configured": return "Lisensi belum dikonfigurasi pada build ini.";
            case "bad_config":     return "Konfigurasi server lisensi salah (cek URL dan anon key).";
            default:               return "Server bermasalah, coba lagi nanti.";
        }
    }
}

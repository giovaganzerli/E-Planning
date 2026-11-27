package it.giovaganzerli.planning;

import java.time.LocalDateTime;
final class EffiplanRules {
    static String scopeKey(String origin, String role) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest((origin + "\n" + role).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String color(String value, String fallback) {
        if (value == null) return fallback;
        String hex = value.trim().replaceFirst("^#", "");
        return hex.matches("[0-9a-fA-F]{6}") ? "#" + hex.toUpperCase(java.util.Locale.ROOT) : fallback;
    }
    static String serviceLabel(String label) {
        String normalized = label.trim().toUpperCase(java.util.Locale.ITALIAN);
        if (normalized.startsWith("V-")) return "Vendita";
        if (normalized.equals("TRUCKTOPEG")) return "TTP";
        if (normalized.equals("PERMA")) return "Permanenza";
        return label;
    }
    static String movementAction(String direction) {
        if ("IN".equals(direction)) return "ENTRY";
        if ("OUT".equals(direction)) return "DEPARTURE";
        throw new IllegalArgumentException("Movimento non riconosciuto");
    }
    /** Horoquartz event range end is exclusive; a Sunday–Saturday UI week must request the following Sunday. */
    static java.time.LocalDate queryEndExclusive(java.time.LocalDate visibleEnd) { return visibleEnd.plusDays(1); }
    static boolean validSession(String token, String role, String user) {
        return token != null && token.length() <= 16000 && token.matches("[^\\x00-\\x20\\x7f]+")
            && user != null && user.length() <= 128 && user.matches("[^\\x00-\\x20\\x7f/]+")
            && role != null && role.length() <= 256 && role.startsWith(user + "/") && role.length() > user.length() + 1
            && role.matches("[^\\x00-\\x20\\x7f]+");
    }
    static LocalDateTime time(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?")) throw new IllegalArgumentException("Data Effiplan non valida");
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }
}

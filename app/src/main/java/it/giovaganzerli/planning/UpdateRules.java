package it.giovaganzerli.planning;

import java.net.URI;
import java.util.Locale;
import java.util.LinkedHashSet;

final class UpdateRules {
    static final long MAX_APK_BYTES = 150L * 1024 * 1024;
    static boolean hasUpdate(int installed, int available) { return available > installed; }
    static boolean showInstalledNotes(boolean accountReady, boolean resumed, boolean showing, int seen, int installed) {
        return accountReady && resumed && !showing && seen < installed;
    }
    static String summarizeNotes(String notes, int maxItems) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        if (notes != null && maxItems > 0) for (String line : notes.split("\\r?\\n")) {
            String point = line.trim();
            if (point.startsWith("#") && !points.isEmpty()) break;
            if (!point.matches("^(?:[-*•] |\\d+[.)] ).+")) continue;
            point = point.replaceFirst("^(?:[-*•] |\\d+[.)] )", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replace("**", "").replace("`", "").trim();
            if (point.length() > 100) {
                int space = point.lastIndexOf(' ', 97);
                point = point.substring(0, space > 50 ? space : 97).trim() + "…";
            }
            if (!point.isEmpty()) points.add(point);
            if (points.size() >= maxItems) break;
        }
        StringBuilder result = new StringBuilder();
        for (String point : points) { if (result.length() > 0) result.append("\n"); result.append("• ").append(point); }
        return result.length() == 0 ? "• Miglioramenti e correzioni." : result.toString();
    }
    static boolean releaseUrl(String value, String repository, String extension) {
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost())
                && uri.getPort() == -1 && uri.getRawUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && uri.getPath().startsWith("/" + repository + "/releases/download/") && uri.getPath().endsWith(extension)
                && !uri.getPath().contains("/../") && !uri.getPath().contains("/./");
        } catch (Exception e) { return false; }
    }
    static boolean downloadHost(String value, String repository) {
        try {
            URI uri = new URI(value); String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getRawUserInfo() != null || uri.getPort() != -1) return false;
            return (host.equalsIgnoreCase("github.com") && uri.getPath().startsWith("/" + repository + "/releases/"))
                || host.equalsIgnoreCase("release-assets.githubusercontent.com") || host.equalsIgnoreCase("objects.githubusercontent.com");
        } catch (Exception e) { return false; }
    }
    static boolean sha256(String value) { return value != null && value.matches("[a-fA-F0-9]{64}"); }
    static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 255)); return out.toString(); }
}

package it.giovaganzerli.planning;

public class UpdateRulesTest {
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        String repo = "owner/repo", good = "https://github.com/owner/repo/releases/download/v1/app.apk";
        check(UpdateRules.releaseUrl(good, repo, ".apk"), "valid release");
        for (String url : new String[]{ good.replace("https:", "http:"), good.replace("github.com", "github.com.evil.test"), good.replace("owner/repo", "other/repo"), good + "?x=1", good + "#x", good.replace("github.com/", "github.com:8443/"), good.replace("/v1/", "/../"), good.replace("/v1/", "/%2e%2e/") }) check(!UpdateRules.releaseUrl(url, repo, ".apk"), "reject " + url);
        check(UpdateRules.downloadHost("https://github.com/owner/repo/releases/latest/download/update.json", repo), "manifest origin");
        check(UpdateRules.downloadHost("https://release-assets.githubusercontent.com/asset?signature=abc", repo), "GitHub asset redirect");
        check(!UpdateRules.downloadHost("https://example.com/app.apk", repo), "reject external host");
        check(!UpdateRules.downloadHost("https://token@github.com/owner/repo/releases/a", repo), "no URL credentials");
        check(UpdateRules.sha256("a".repeat(64)) && !UpdateRules.sha256("a".repeat(63)) && !UpdateRules.sha256("g".repeat(64)), "checksum format");
        check(UpdateRules.hex(new byte[]{0, 15, -1}).equals("000fff"), "checksum hex");
        System.out.println("UpdateRules: all tests passed");
    }
}

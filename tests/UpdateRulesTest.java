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
        check(UpdateRules.hasUpdate(2008, 2009), "new release enables both notification indicators");
        check(!UpdateRules.hasUpdate(2008, 2008) && !UpdateRules.hasUpdate(2008, 2005), "equal and older releases do not show a badge");
        check(!UpdateRules.showInstalledNotes(false, true, false, 0, 2029), "no installed notes during login or onboarding");
        check(UpdateRules.showInstalledNotes(true, true, false, 0, 2029), "installed notes after account setup");
        check(!UpdateRules.showInstalledNotes(true, false, false, 0, 2029), "no installed notes while app is paused");
        check(!UpdateRules.showInstalledNotes(true, true, true, 0, 2029), "no duplicate installed notes dialog");
        check(!UpdateRules.showInstalledNotes(true, true, false, 2029, 2029), "acknowledged installed notes remain hidden");
        String notes = "# Novità\n\n- **Aggiornamenti** · pallini nel menu.\n- Calendario · stato salvato.\n- Timbratura · conferma.\n- Quarta novità.\n## Versioni precedenti\n- Non mostrare questa voce.";
        String concise = UpdateRules.summarizeNotes(notes, 4);
        check(concise.split("\n").length == 4 && !concise.contains("**") && !concise.contains("Non mostrare"), "compact bullets without historical notes");
        check(UpdateRules.summarizeNotes(notes, 3).split("\n").length == 3, "startup limited to three items");
        check(UpdateRules.summarizeNotes("- Ripetizione\n- Ripetizione\n- Altra voce", 4).split("\n").length == 2, "duplicate points removed");
        check(UpdateRules.summarizeNotes("- " + "Descrizione molto lunga ".repeat(20), 4).length() <= 102, "long descriptions bounded");
        check(UpdateRules.summarizeNotes(null, 4).equals("• Miglioramenti e correzioni."), "empty notes fallback");
        check(UpdateRules.summarizeNotes("1. [Calendario](https://example.invalid) con `avvisi`", 4).equals("• Calendario con avvisi"), "numbered and markdown notes normalized");
        System.out.println("UpdateRules: all tests passed");
    }
}

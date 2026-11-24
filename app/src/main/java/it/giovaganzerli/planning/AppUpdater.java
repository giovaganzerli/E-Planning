package it.giovaganzerli.planning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks GitHub releases and hands the user-selected download to their browser. */
final class AppUpdater {
    private static final String REPOSITORY = BuildConfig.UPDATE_REPOSITORY;
    private static final long CHECK_INTERVAL = 5 * 60 * 1000;
    private final Activity activity;
    private final View button;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private volatile HttpURLConnection active;
    private boolean checking;
    private Info latest;
    private final Runnable poll = new Runnable() {
        @Override public void run() { if (!destroyed) { check(false); main.postDelayed(this, CHECK_INTERVAL); } }
    };

    private static final class Info {
        final int code;
        final String version, notes, url, sha, raw;
        final long size;
        Info(String raw) throws Exception {
            JSONObject json = new JSONObject(raw);
            if (json.getInt("schemaVersion") != 1) throw new Exception("Formato aggiornamento non supportato");
            long codeValue = json.getLong("versionCode");
            if (codeValue <= 0 || codeValue > Integer.MAX_VALUE) throw new Exception("Versione non valida");
            code = (int) codeValue; version = json.getString("versionName"); notes = json.getString("notes");
            url = json.getString("apkUrl"); sha = json.getString("sha256"); size = json.getLong("size"); this.raw = raw;
            if (!UpdateRules.releaseUrl(url, REPOSITORY, ".apk") || !UpdateRules.sha256(sha) || size <= 0 || size > UpdateRules.MAX_APK_BYTES || version.length() > 100 || notes.length() > 50000) throw new Exception("Dati aggiornamento non validi");
        }
    }

    AppUpdater(Activity activity, View button) {
        this.activity = activity; this.button = button; prefs = activity.getSharedPreferences("app-updates", Activity.MODE_PRIVATE);
        try { latest = new Info(prefs.getString("latest", "")); } catch (Exception ignored) { }
        updateButton(); button.setOnClickListener(v -> {
            if (latest != null && latest.code > BuildConfig.VERSION_CODE) showAvailable(latest); else check(true);
        });
    }
    void start() { main.post(() -> { if (!destroyed) showInstalledNotes(); }); main.post(poll); }
    void resume() { check(false); }
    void destroy() {
        destroyed = true; main.removeCallbacksAndMessages(null); executor.shutdownNow();
        if (active != null) active.disconnect();
    }
    private void updateButton() {
        boolean available = latest != null && latest.code > BuildConfig.VERSION_CODE;
        button.setAlpha(available ? 1f : .65f); button.setBackgroundResource(available ? R.drawable.bg_update_available : R.drawable.bg_logout);
        button.setContentDescription(available ? "Aggiornamento disponibile: " + latest.version : "Verifica aggiornamenti");
    }
    private void check(boolean manual) {
        if (destroyed) return;
        if (checking) { if (manual) error("Verifica aggiornamenti in corso…"); return; }
        long elapsed = System.currentTimeMillis() - prefs.getLong("last_check", 0);
        if (!manual && elapsed >= 0 && elapsed < CHECK_INTERVAL) return;
        checking = true;
        executor.execute(() -> {
            try {
                String url = "https://github.com/" + REPOSITORY + "/releases/latest/download/update.json";
                Info info = new Info(readText(url, 1024 * 1024));
                main.post(() -> {
                    if (destroyed) return; checking = false; latest = info;
                    prefs.edit().putString("latest", info.raw).putLong("last_check", System.currentTimeMillis()).apply(); updateButton();
                    if (manual) { if (info.code > BuildConfig.VERSION_CODE) showAvailable(info); else new AlertDialog.Builder(activity).setTitle("E-Planning " + BuildConfig.VERSION_NAME).setMessage("L'app è aggiornata.\n\n" + installedNotes()).setPositiveButton("OK", null).show(); }
                });
            } catch (Exception e) { main.post(() -> { if (!destroyed) { checking = false; if (manual) error("Non riesco a verificare gli aggiornamenti. Controlla la connessione e riprova."); } }); }
        });
    }
    private void showAvailable(Info info) {
        new AlertDialog.Builder(activity).setTitle("Aggiornamento " + info.version)
            .setMessage("Versione installata: " + BuildConfig.VERSION_NAME + "\nDimensione: " + ((info.size + 1023) / 1024) + " KB\n\n" + info.notes + "\n\nSi aprirà il browser per scaricare l'APK dalla Release GitHub. Android richiederà poi la tua conferma per installarlo.")
            .setNegativeButton("Non ora", null).setPositiveButton("Apri download nel browser", (d, w) -> openBrowser(info)).show();
    }
    private void openBrowser(Info info) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.url));
            activity.startActivity(intent);
        } catch (Exception e) { error("Non riesco ad aprire il browser. Apri manualmente la pagina Releases su GitHub."); }
    }
    private HttpURLConnection open(String address) throws Exception {
        for (int redirects = 0; redirects < 8; redirects++) {
            if (destroyed) throw new Exception("Annullato");
            if (!UpdateRules.downloadHost(address, REPOSITORY)) throw new Exception("Origine non valida");
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection(); active = connection;
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setRequestProperty("User-Agent", "E-Planning/" + BuildConfig.VERSION_NAME);
            int code;
            try { code = connection.getResponseCode(); } catch (Exception e) { connection.disconnect(); throw e; }
            if (code == 200) return connection;
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String next = connection.getHeaderField("Location"); connection.disconnect(); if (next == null) throw new Exception("Redirect non valido"); address = new URL(new URL(address), next).toString();
            } else { connection.disconnect(); throw new Exception("Risposta GitHub " + code); }
        }
        throw new Exception("Troppi redirect");
    }
    private String readText(String address, int limit) throws Exception {
        HttpURLConnection connection = open(address);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new Exception("File troppo grande"); output.write(buffer, 0, count); }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally { connection.disconnect(); active = null; }
    }
    private String installedNotes() {
        try { Info cached = new Info(prefs.getString("latest", "")); if (cached.code == BuildConfig.VERSION_CODE) return cached.notes; } catch (Exception ignored) { }
        try (InputStream input = activity.getAssets().open("release-notes.txt"); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { return "Miglioramenti e correzioni."; }
    }
    private void showInstalledNotes() {
        int previous = prefs.getInt("seen_version", 0);
        if (previous >= BuildConfig.VERSION_CODE) return;
        String notes = installedNotes();
        new AlertDialog.Builder(activity).setTitle("Novità · E-Planning " + BuildConfig.VERSION_NAME).setMessage(notes).setPositiveButton("Continua", (d, w) -> prefs.edit().putInt("seen_version", BuildConfig.VERSION_CODE).apply()).setCancelable(false).show();
    }
    private void error(String message) { if (!destroyed) new AlertDialog.Builder(activity).setTitle("Aggiornamenti").setMessage(message).setPositiveButton("OK", null).show(); }
}

package it.giovaganzerli.planning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** GitHub-only updater. No application credentials are ever sent to GitHub. */
final class AppUpdater {
    private static final String REPOSITORY = BuildConfig.UPDATE_REPOSITORY;
    private static final long CHECK_INTERVAL = 5 * 60 * 1000;
    private final Activity activity;
    private final View button;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed, cancelled;
    private volatile HttpURLConnection active;
    private boolean checking, downloading;
    private Info latest;
    private AlertDialog progress;
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
            if (downloading) return;
            if (latest != null && latest.code > BuildConfig.VERSION_CODE) showAvailable(latest); else check(true);
        });
    }
    void start() { main.post(() -> { if (!destroyed) showInstalledNotes(); }); main.post(poll); }
    void resume() {
        if (prefs.getBoolean("awaiting_permission", false)) {
            prefs.edit().putBoolean("awaiting_permission", false).apply();
            if (activity.getPackageManager().canRequestPackageInstalls()) {
                try { install(new Info(prefs.getString("pending", ""))); } catch (Exception e) { error("Scarica nuovamente l'aggiornamento."); }
            } else error("Installazione non autorizzata. Potrai riprovare dall'icona aggiornamenti.");
        }
        check(false);
    }
    void destroy() {
        destroyed = true; cancelled = true; main.removeCallbacksAndMessages(null); executor.shutdownNow();
        if (active != null) active.disconnect(); if (progress != null) progress.dismiss();
    }
    private void updateButton() {
        boolean available = latest != null && latest.code > BuildConfig.VERSION_CODE;
        button.setAlpha(available ? 1f : .65f); button.setBackgroundResource(available ? R.drawable.bg_update_available : R.drawable.bg_logout);
        button.setContentDescription(available ? "Aggiornamento disponibile: " + latest.version : "Verifica aggiornamenti");
    }
    private void check(boolean manual) {
        if (destroyed || downloading) return;
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
            .setMessage("Versione installata: " + BuildConfig.VERSION_NAME + "\nDimensione: " + ((info.size + 1023) / 1024) + " KB\n\n" + info.notes + "\n\nAndroid chiederà conferma prima dell'installazione. Sessione e configurazione vengono mantenute.")
            .setNegativeButton("Non ora", null).setPositiveButton("Scarica e aggiorna", (d, w) -> download(info)).show();
    }
    private void download(Info info) {
        if (downloading || destroyed) return; downloading = true; cancelled = false;
        LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL); int padding = (int) (24 * activity.getResources().getDisplayMetrics().density); box.setPadding(padding, padding, padding, padding);
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100); box.addView(bar);
        TextView label = new TextView(activity); label.setText("Download da GitHub…"); box.addView(label);
        progress = new AlertDialog.Builder(activity).setTitle("E-Planning " + info.version).setView(box).setCancelable(false).setNegativeButton("Annulla", (d, w) -> { cancelled = true; if (active != null) active.disconnect(); }).show();
        executor.execute(() -> {
            File folder = new File(activity.getFilesDir(), "updates"), partial = new File(folder, "update.part"), apk = new File(folder, "update.apk");
            try {
                if (!folder.isDirectory() && !folder.mkdirs()) throw new Exception("Spazio non disponibile");
                HttpURLConnection connection = open(info.url); active = connection;
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); long received = 0; int lastPercent = -1;
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (cancelled || destroyed) throw new Exception("Annullato");
                        received += count; if (received > info.size || received > UpdateRules.MAX_APK_BYTES) throw new Exception("Dimensione non valida");
                        output.write(buffer, 0, count); digest.update(buffer, 0, count);
                        final int percent = (int) (received * 100 / info.size);
                        if (percent != lastPercent) { lastPercent = percent; main.post(() -> { if (!destroyed) { bar.setProgress(percent); label.setText("Download " + percent + "%"); } }); }
                    }
                } finally { connection.disconnect(); active = null; }
                if (cancelled || destroyed || received != info.size || !UpdateRules.hex(digest.digest()).equalsIgnoreCase(info.sha)) throw new Exception("Integrità del file non valida");
                verifyPackage(partial, info);
                if (!partial.renameTo(apk)) throw new Exception("Impossibile salvare l'APK");
                prefs.edit().putString("pending", info.raw).apply();
                main.post(() -> { if (!destroyed) { downloading = false; progress.dismiss(); requestInstall(info); } });
            } catch (Exception e) {
                partial.delete(); main.post(() -> { if (!destroyed) { downloading = false; progress.dismiss(); if (!cancelled) error("Aggiornamento non installato. Download o verifica di sicurezza non riusciti; riprova."); } });
            }
        });
    }
    private void requestInstall(Info info) {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("Consenti gli aggiornamenti")
                .setMessage("Abilita ‘Consenti da questa fonte’ per E-PLANNING nelle impostazioni Android. Al ritorno potrai confermare l'installazione.")
                .setNegativeButton("Annulla", null).setPositiveButton("Apri impostazioni", (d, w) -> {
                    try { prefs.edit().putBoolean("awaiting_permission", true).apply(); activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
                    catch (Exception e) { prefs.edit().putBoolean("awaiting_permission", false).apply(); error("Non riesco ad aprire le impostazioni Android."); }
                }).show();
        } else install(info);
    }
    private void install(Info info) {
        if (info.code <= BuildConfig.VERSION_CODE) return;
        File apk = new File(activity.getFilesDir(), "updates/update.apk");
        executor.execute(() -> {
            try {
                verifyFile(apk, info); verifyPackage(apk, info);
                main.post(() -> {
                    if (destroyed) return;
                    try {
                        Uri uri = new Uri.Builder().scheme("content").authority(activity.getPackageName() + ".updates").path("/update.apk").build();
                        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(ClipData.newRawUri("E-Planning", uri)); activity.startActivity(intent);
                    } catch (Exception e) { error("Nessun programma di installazione disponibile sul dispositivo."); }
                });
            } catch (Exception e) { main.post(() -> { if (!destroyed) error("APK non valido: scarica nuovamente l'aggiornamento."); }); }
        });
    }
    private void verifyFile(File file, Info info) throws Exception {
        if (file.length() != info.size) throw new Exception(); MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) { byte[] buffer = new byte[16384]; int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); }
        if (!UpdateRules.hex(digest.digest()).equalsIgnoreCase(info.sha)) throw new Exception();
    }
    @SuppressWarnings("deprecation")
    private void verifyPackage(File file, Info info) throws Exception {
        PackageManager manager = activity.getPackageManager(); int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo installed = manager.getPackageInfo(activity.getPackageName(), flags), candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (candidate == null || !activity.getPackageName().equals(candidate.packageName)) throw new Exception("App diversa");
        long code = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
        if (code != info.code || code <= BuildConfig.VERSION_CODE) throw new Exception("Versione diversa");
        Signature[] ours = Build.VERSION.SDK_INT >= 28 ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        Signature[] theirs = Build.VERSION.SDK_INT >= 28 ? candidate.signingInfo.getApkContentsSigners() : candidate.signatures;
        if (ours == null || theirs == null || ours.length != 1 || theirs.length != 1 || !Arrays.equals(ours[0].toByteArray(), theirs[0].toByteArray())) throw new Exception("Firma diversa");
    }
    private HttpURLConnection open(String address) throws Exception {
        for (int redirects = 0; redirects < 8; redirects++) {
            if (destroyed || (downloading && cancelled)) throw new Exception("Annullato");
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
        try { Info pending = new Info(prefs.getString("pending", "")); if (pending.code == BuildConfig.VERSION_CODE) return pending.notes; } catch (Exception ignored) { }
        try (InputStream input = activity.getAssets().open("release-notes.txt"); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { return "Miglioramenti e correzioni."; }
    }
    private void showInstalledNotes() {
        int previous = prefs.getInt("seen_version", 0);
        if (previous >= BuildConfig.VERSION_CODE) return;
        String notes = installedNotes();
        new AlertDialog.Builder(activity).setTitle("Novità · E-Planning " + BuildConfig.VERSION_NAME).setMessage(notes).setPositiveButton("Continua", (d, w) -> prefs.edit().putInt("seen_version", BuildConfig.VERSION_CODE).remove("awaiting_permission").apply()).setCancelable(false).show();
    }
    private void error(String message) { if (!destroyed) new AlertDialog.Builder(activity).setTitle("Aggiornamenti").setMessage(message).setPositiveButton("OK", null).show(); }
}

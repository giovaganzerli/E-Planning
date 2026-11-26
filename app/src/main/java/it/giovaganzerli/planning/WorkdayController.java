package it.giovaganzerli.planning;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Attendance writes are user-initiated, never retried automatically, and reconciled by GET. */
final class WorkdayController {
    private static final String BASE = BuildConfig.API_BASE_URL + "/lite/planning/api/v2/";
    private final Activity activity;
    private final Button button;
    private final TextView description;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DateTimeFormatter format = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ITALIAN);
    private volatile int generation;
    private volatile boolean destroyed;
    private String token = "", owner = "";
    private boolean visible, resumed, busy;
    private Snapshot snapshot;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running()) return;
            refresh(); handler.postDelayed(this, 30000);
        }
    };
    WorkdayController(Activity activity) {
        this.activity = activity;
        button = activity.findViewById(R.id.punchButton);
        description = activity.findViewById(R.id.punchDescription);
        prefs = activity.getSharedPreferences("workday", Activity.MODE_PRIVATE);
        button.setOnClickListener(v -> punch());
        display("Attendi", "Verifica disponibilità…", false, false);
    }
    void session(String value, String collaborator) {
        if (token.equals(value) && owner.equals(collaborator)) return;
        generation++; token = value; owner = collaborator; snapshot = null; busy = false;
        restart();
    }
    void visible(boolean value) { visible = value; restart(); }
    void resume() { resumed = true; restart(); }
    void pause() { resumed = false; handler.removeCallbacks(poll); }
    void destroy() { destroyed = true; generation++; handler.removeCallbacksAndMessages(null); executor.shutdownNow(); }
    private boolean running() { return !destroyed && resumed && visible && !token.isEmpty() && !owner.isEmpty(); }
    private void restart() {
        handler.removeCallbacks(poll);
        if (running()) handler.post(poll);
    }
    void refresh() {
        if (!running() || busy) return;
        busy = true;
        final int version = generation; final String bearer = token, user = owner;
        executor.execute(() -> {
            try {
                Snapshot loaded = load(bearer, user);
                reconcile(user, loaded);
                activity.runOnUiThread(() -> {
                    if (!valid(version)) return;
                    busy = false; snapshot = loaded; render();
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (!valid(version)) return;
                    busy = false; snapshot = null;
                    display("Timbra", "Stato non disponibile. Premi aggiorna per riprovare.", false, false);
                });
            }
        });
    }
    private boolean valid(int version) { return !destroyed && version == generation; }
    private String key(String user, WorkdayRules.Turn turn) { return user + "|" + turn.key(); }
    private JSONObject stored(String user, WorkdayRules.Turn turn) {
        if (turn == null) return new JSONObject();
        try { return new JSONObject(prefs.getString(key(user, turn), "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }
    private void save(String user, WorkdayRules.Turn turn, JSONObject record) throws Exception {
        if (!prefs.edit().putString(key(user, turn), record.toString()).commit()) throw new Exception("Salvataggio non riuscito");
    }
    private void display(String label, String text, boolean enabled, boolean done) {
        button.setText(label); button.setEnabled(enabled);
        button.setAlpha(enabled || done ? 1f : .45f);
        button.setBackgroundResource(done ? R.drawable.bg_punch_done : R.drawable.bg_punch_button);
        button.setContentDescription(done ? "Timbratura effettuata" : "Registra la presenza di oggi");
        description.setText(text);
        description.setTextColor(done ? Color.rgb(21, 128, 61) : Color.rgb(100, 116, 139));
    }
    private void render() {
        if (busy) return;
        if (snapshot == null) { display("Attendi", "Verifica disponibilità…", false, false); return; }
        WorkdayRules.Turn turn = snapshot.turn;
        JSONObject record = stored(owner, turn);
        WorkdayRules.State state = WorkdayRules.state(turn, snapshot.status, record.optBoolean("confirmed"), record.optBoolean("pending"));
        if (state == WorkdayRules.State.DONE) {
            String at = record.optString("at");
            try { at = WorkdayRules.time(at).format(format); } catch (Exception ignored) { }
            display("✓ Effettuata", (record.optBoolean("serverTime") ? "Timbrata il " : "Confermata il ") + at, false, true);
        } else if (state == WorkdayRules.State.VERIFY) {
            display("Verifica", "Esito da verificare sul portale. Nessun nuovo invio automatico.", false, false);
        } else if (state == WorkdayRules.State.READY) {
            display("Timbra", "In turno · registra " + ("DEPARTURE".equals(snapshot.status) ? "l’uscita" : "l’entrata"), true, false);
        } else {
            display("Timbra", "Disponibile durante il prossimo turno.", false, false);
        }
    }
    private Snapshot load(String bearer, String user) throws Exception {
        JSONObject status = get(BASE + "recordWorkDay/recordWorkDayStatus", bearer);
        if (!status.has("status") || status.optBoolean("success", true) == false) throw new Exception("Stato non leggibile");
        Object planning = new JSONTokener(http(BASE + "planning/collaboratorWeek", bearer, null).body).nextValue();
        if (planning instanceof JSONObject && !((JSONObject) planning).optBoolean("success", true)) throw new Exception("Planning non disponibile");
        List<WorkdayRules.Turn> turns = new ArrayList<>();
        collectTurns(planning, user, turns);
        Snapshot result = new Snapshot();
        result.status = status.optString("status", "");
        result.turn = WorkdayRules.currentTurn(turns, LocalDateTime.now(WorkdayRules.ZONE));
        JSONArray records = status.optJSONArray("recordWorkdayDto");
        if (records != null) for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            for (String action : new String[] { "ENTRY", "DEPARTURE" }) {
                String name = "ENTRY".equals(action) ? "entry" : "departure";
                if (record.isNull(name) || record.optString(name, "").isEmpty()) continue;
                result.records.add(WorkdayRules.recordKey(action, WorkdayRules.time(record.getString(name))));
            }
        }
        return result;
    }
    private void collectTurns(Object node, String user, List<WorkdayRules.Turn> turns) throws Exception {
        if (node instanceof JSONArray) {
            JSONArray items = (JSONArray) node;
            for (int i = 0; i < items.length(); i++) collectTurns(items.get(i), user, turns);
        } else if (node instanceof JSONObject) {
            JSONObject item = (JSONObject) node;
            if (item.has("fromHour") && item.has("toHour")) {
                String id = item.optString("collaboratorUuid", "");
                if (id.isEmpty()) id = item.optString("uuidCollaborator", "");
                JSONObject person = item.optJSONObject("collaborator");
                if (id.isEmpty() && person != null) id = person.optString("uuid", "");
                if (!id.isEmpty() && !id.equals(user)) return;
                LocalDateTime from = WorkdayRules.time(item.getString("fromHour")), to = WorkdayRules.time(item.getString("toHour"));
                if (to.isAfter(from)) turns.add(new WorkdayRules.Turn(from, to));
            } else {
                Iterator<String> keys = item.keys();
                while (keys.hasNext()) collectTurns(item.get(keys.next()), user, turns);
            }
        }
    }
    private void reconcile(String user, Snapshot loaded) throws Exception {
        if (loaded.turn == null) return;
        JSONObject saved = stored(user, loaded.turn);
        if (!saved.optBoolean("pending") && !saved.optBoolean("confirmed")) {
            // Also recognize a presence already registered from the official portal.
            LocalDateTime latest = null;
            String action = "";
            for (String entry : loaded.records) {
                int split = entry.indexOf('|');
                LocalDateTime at = WorkdayRules.time(entry.substring(split + 1));
                if (at.isBefore(loaded.turn.start) || at.isAfter(loaded.turn.end)) continue;
                if (latest == null || at.isAfter(latest)) { latest = at; action = entry.substring(0, split); }
            }
            if (latest != null) save(user, loaded.turn, new JSONObject().put("confirmed", true).put("pending", false).put("at", latest.toString()).put("serverTime", true).put("action", action));
            return;
        }
        if (!saved.optBoolean("pending") && (!saved.optBoolean("confirmed") || saved.optBoolean("serverTime"))) return;
        Set<String> before = new HashSet<>();
        JSONArray baseline = saved.optJSONArray("before");
        if (baseline != null) for (int i = 0; i < baseline.length(); i++) before.add(baseline.getString(i));
        LocalDateTime actual = WorkdayRules.newRecord(saved.optString("action"), before, loaded.records, loaded.turn);
        if (actual != null) {
            saved.put("at", actual.toString()).put("confirmed", true).put("pending", false).put("serverTime", true);
            save(user, loaded.turn, saved);
        }
    }
    private void punch() {
        if (!running() || busy || snapshot == null || snapshot.turn == null || !button.isEnabled()) return;
        final int version = generation;
        final String bearer = token, user = owner;
        final WorkdayRules.Turn selected = snapshot.turn;
        busy = true;
        display("Invio…", "Verifica e registrazione in corso…", false, false);
        executor.execute(() -> {
            try {
                Snapshot fresh = load(bearer, user);
                reconcile(user, fresh);
                JSONObject previous = stored(user, selected);
                if (!valid(version) || fresh.turn == null || !selected.key().equals(fresh.turn.key()) || !WorkdayRules.permitted(fresh.status) || previous.optBoolean("confirmed") || previous.optBoolean("pending")) {
                    finish(version, fresh); return;
                }
                String uid = new JSONObject(new String(Base64.getUrlDecoder().decode(bearer.split("\\.")[1]), StandardCharsets.UTF_8)).optString("uid", "");
                if (uid.isEmpty() || uid.length() > 128) throw new Exception("Utente non riconosciuto");
                String command = UUID.randomUUID().toString();
                JSONObject body = new JSONObject().put("uuid", command).put("actorDto", new JSONObject().put("name", JSONObject.NULL).put("username", uid).put("email", JSONObject.NULL));
                JSONObject record = new JSONObject().put("pending", true).put("confirmed", false).put("command", command).put("action", fresh.status).put("before", new JSONArray(fresh.records));
                save(user, selected, record);
                if (!valid(version)) {
                    prefs.edit().remove(key(user, selected)).commit();
                    return;
                }
                Reply reply = http(BASE + "recordWorkDay/update", bearer, body);
                JSONObject result = new JSONObject(reply.body);
                if (!WorkdayRules.accepted(reply.code, Boolean.TRUE.equals(result.opt("success")))) {
                    if (Boolean.FALSE.equals(result.opt("success"))) prefs.edit().remove(key(user, selected)).commit();
                    throw new Exception("Registrazione non confermata");
                }
                record.put("pending", false).put("confirmed", true).put("at", LocalDateTime.now(WorkdayRules.ZONE).toString()).put("serverTime", false);
                save(user, selected, record);
                try { fresh = load(bearer, user); reconcile(user, fresh); } catch (Exception ignored) { }
                finish(version, fresh);
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (!valid(version)) return;
                    busy = false; render();
                    new AppDialogs.Builder(activity).setTitle("Timbratura non confermata")
                        .setMessage("Non è stato possibile confermare la registrazione. Aggiorna i dati o controlla il portale prima di riprovare: nessuna richiesta viene reinviata automaticamente.")
                        .setPositiveButton("Ho capito", null).show();
                });
            }
        });
    }
    private void finish(int version, Snapshot value) {
        activity.runOnUiThread(() -> { if (valid(version)) { busy = false; snapshot = value; render(); } });
    }
    private JSONObject get(String url, String bearer) throws Exception { return new JSONObject(http(url, bearer, null).body); }
    private Reply http(String url, String bearer, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            String value;
            try (InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (input == null) value = "";
                else {
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096]; int size;
                    while ((size = input.read(buffer)) != -1) {
                        if (output.size() + size > 4 * 1024 * 1024) throw new Exception("Risposta troppo grande");
                        output.write(buffer, 0, size);
                    }
                    value = output.toString("UTF-8");
                }
            }
            if (body == null && (code < 200 || code >= 300)) throw new Exception("Stato non disponibile");
            return new Reply(code, value);
        } finally { connection.disconnect(); }
    }
    private static final class Reply {
        final int code; final String body;
        Reply(int code, String body) { this.code = code; this.body = body; }
    }
    private static final class Snapshot {
        String status;
        WorkdayRules.Turn turn;
        final List<String> records = new ArrayList<>();
    }
}

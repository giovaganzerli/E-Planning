package it.giovaganzerli.planning;

import android.webkit.CookieManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Endpoints observed in the official client. Attendance writes have a separate entry point. */
final class EffiplanApi {
    static final class AuthenticationException extends Exception { }
    final String user;
    private final String origin, token, role;
    private final java.util.Map<String, Integer> models = new java.util.HashMap<>();
    private final java.util.Map<String, JSONObject> displays = new java.util.HashMap<>();
    private final java.util.Map<String, Long> displayTimes = new java.util.HashMap<>();
    private SetupRules.Team verifiedMembership;
    EffiplanApi(String origin, String token, String role, String user) throws Exception {
        URI uri = new URI(origin);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw new Exception("Origine non valida");
        if (!EffiplanRules.validSession(token, role, user)) throw new Exception("Sessione Effiplan incompleta");
        this.origin = origin.replaceAll("/$", ""); this.token = token; this.role = role; this.user = user;
    }
    JSONObject profile() throws Exception {
        JSONObject content = call("/v2/users/settings/menu?roleId=" + encode(role) + "&language=it", null);
        JSONObject person = content.getJSONObject("user");
        if (!user.equals(person.getString("rollNumber"))) throw new AuthenticationException();
        return new JSONObject().put("id", user).put("name", (person.optString("firstName", "") + " " + person.optString("lastName", "")).trim());
    }
    private synchronized int model(String function) throws Exception {
        if (!models.containsKey(function)) {
            JSONArray available = call("/v1/settings/functions/" + function + "/models?roleId=" + encode(role) + "&allModels=true", null).getJSONArray("models");
            if (available.length() == 0) throw new Exception("Planning non abilitato");
            models.put(function, available.getJSONObject(0).getInt("functionModelId"));
        }
        return models.get(function);
    }
    private JSONObject eventsRequest(String function, LocalDate start, LocalDate end, JSONArray keywords) throws Exception {
        if (end.isBefore(start) || end.isAfter(start.plusDays(62))) throw new Exception("Periodo non valido");
        return new JSONObject().put("function", function).put("model", String.valueOf(model(function))).put("keywords", keywords)
            .put("start", start.toString()).put("end", end.toString());
    }
    private synchronized JSONObject postDisplay(String function) throws Exception {
        long now = android.os.SystemClock.elapsedRealtime();
        if (!displays.containsKey(function) || now - displayTimes.get(function) > 5 * 60 * 1000L) {
            JSONObject settings = call("/v1/settings/functions/" + function + "/models/" + model(function) + "?roleId=" + encode(role), null);
            JSONObject post = settings.getJSONObject("display").optJSONObject("post");
            displays.put(function, post == null ? new JSONObject() : post);
            displayTimes.put(function, now);
        }
        return displays.get(function);
    }
    String scopeKey() { return EffiplanRules.scopeKey(origin, role); }
    SetupRules.Team membership() throws Exception {
        try {
            verifiedMembership = SetupData.effiplanMembership(call("/v1/synoptic/" + encode(user) + "/personal?roleId=" + encode(role), null));
        } catch (AuthenticationException expired) { throw expired; }
        catch (Exception unavailable) {
            try {
                verifiedMembership = SetupData.effiplanMembership(call("/v2/employees/" + encode(user) + "?categories=identity&roleId=" + encode(role), null));
            } catch (AuthenticationException expired) { throw expired; }
            catch (Exception denied) {
                JSONArray sections = call("/v2/settings/lists/types/SEH?roleId=" + encode(role), null).getJSONArray("SEH");
                java.util.List<SetupRules.Team> matches = new java.util.ArrayList<>();
                LocalDate start = PlanningPortal.EFFIPLAN.weekStart(LocalDate.now());
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject section = sections.getJSONObject(i);
                    SetupRules.Team candidate = new SetupRules.Team(EffiplanData.populationKey("SEH", section.getString("value")), section.getString("title"));
                    if (sectionRoster(candidate, start).containsKey(user)) matches.add(candidate);
                }
                verifiedMembership = SetupData.uniqueMembership(matches);
            }
        }
        return verifiedMembership;
    }
    private JSONObject ownGroup(SetupRules.Team team) throws Exception {
        if (!team.key.startsWith("SEH:") || team.key.length() <= 4) throw new Exception("Squadra effettiva non valida");
        return new JSONObject().put("type", "SEH").put("value", team.key.substring(4)).put("title", team.title);
    }
    java.util.Map<String, String> membershipRoster(SetupRules.Team team, LocalDate start) throws Exception {
        if (verifiedMembership == null || !verifiedMembership.key.equals(team.key)) {
            boolean authorized = false;
            for (SetupRules.Team available : SetupData.effiplanTeams(populations()))
                if (available.key.equals(team.key)) authorized = true;
            if (!authorized) throw new Exception("Squadra non disponibile per il profilo");
        }
        return sectionRoster(team, start);
    }
    private java.util.Map<String, String> sectionRoster(SetupRules.Team team, LocalDate start) throws Exception {
        JSONObject group = ownGroup(team);
        JSONObject request = eventsRequest("UPC", start, start.plusDays(6), new JSONArray().put("pst"))
            .put("restriType", group.getString("type")).put("restriValue", group.getString("value"))
            .put("loansDisplay", new JSONObject().put("showLent", true).put("showBorrowed", false))
            .put("valueDate", start.toString()).put("limit", 50).put("sortid", "").put("direction", "ASC");
        JSONArray members = new JSONArray();
        int offset = 0;
        while (true) {
            JSONObject page = call("/v3/activities/events?roleId=" + encode(role), request.put("offset", offset));
            JSONArray people = page.getJSONArray("persons");
            for (int i = 0; i < people.length(); i++) {
                JSONObject person = people.getJSONObject(i);
                if (person.optBoolean("hidden")) throw new Exception("Collaboratori riservati");
                members.put(new JSONObject().put("uuid", person.getString("resource")).put("name", person.getString("name")));
            }
            if (offset + people.length() >= page.getInt("totalEmp")) break;
            if (people.length() == 0 || offset > 5000) throw new Exception("Collaboratori incompleti");
            offset += people.length();
        }
        return EffiplanData.members(members);
    }
    JSONArray planning(LocalDate start, LocalDate end) throws Exception {
        JSONObject request = eventsRequest("UFH", start, end, new JSONArray().put("pst")).put("rollNumber", new JSONArray().put(user));
        JSONObject content = call("/v3/activities/events?roleId=" + encode(role), request);
        return EffiplanData.shifts(content, start, end, user, postDisplay("UFH"));
    }
    JSONArray populations() throws Exception {
        JSONArray sections = call("/v2/settings/lists/types/SEH?roleId=" + encode(role), null).getJSONArray("SEH");
        JSONArray favorites = call("/v1/settings/lists/types?roleId=" + encode(role), null).getJSONArray("lists");
        JSONObject context = call("/v1/users/settings/context?roleId=" + encode(role) + "&properties=GENERAL", null);
        return EffiplanData.populations(sections, favorites, context.getJSONArray("content"));
    }
    JSONObject team(LocalDate start, LocalDate end, boolean includeAbsences, String populationKey) throws Exception {
        JSONObject group = verifiedMembership != null && verifiedMembership.key.equals(populationKey)
            ? ownGroup(verifiedMembership) : EffiplanData.population(populations(), populationKey);
        JSONObject display = postDisplay("UPC");
        JSONObject request = eventsRequest("UPC", start, end, new JSONArray().put("pst").put("abs"))
            .put("valueDate", start.toString()).put("sortid", "").put("direction", "ASC")
            .put("loansDisplay", new JSONObject().put("showLent", true).put("showBorrowed", true))
            .put("restriType", group.getString("type")).put("restriValue", group.getString("value")).put("limit", 50);
        JSONArray allPersons = new JSONArray(), allShifts = new JSONArray(), allBlocks = new JSONArray();
        java.util.Set<String> seen = new java.util.HashSet<>();
        int offset = 0;
        while (true) {
            request.put("offset", offset);
            JSONObject page = call("/v3/activities/events?roleId=" + encode(role), request);
            JSONArray people = page.getJSONArray("persons");
            for (int i = 0; i < people.length(); i++) {
                JSONObject person = people.getJSONObject(i);
                if (person.optBoolean("hidden")) throw new Exception("Planning parzialmente riservato: ricerca non disponibile");
                String id = person.getString("resource");
                if (!seen.add(id)) throw new Exception("Paginazione squadra incoerente");
                allPersons.put(new JSONObject().put("uuid", id).put("name", person.getString("name")));
            }
            JSONArray shifts = EffiplanData.shifts(page, start, end, null, display);
            for (int i = 0; i < shifts.length(); i++) allShifts.put(shifts.get(i));
            if (includeAbsences) {
                JSONArray blocks = EffiplanData.absences(page, start, end);
                for (int i = 0; i < blocks.length(); i++) allBlocks.put(blocks.get(i));
            }
            int total = page.getInt("totalEmp");
            if (offset + people.length() >= total) break;
            if (people.length() == 0 || offset + people.length() > 5000) throw new Exception("Squadra incompleta");
            offset += people.length();
        }
        return new JSONObject().put("members", allPersons).put("shifts", allShifts).put("blocks", allBlocks)
            .put("team", EffiplanData.populationKey(group.getString("type"), group.getString("value"))).put("populationTitle", group.getString("title"));
    }
    JSONObject attendance(LocalDate start, LocalDate end) throws Exception {
        JSONObject menu = call("/v2/users/settings/menu?roleId=" + encode(role) + "&language=it", null);
        if (!user.equals(menu.getJSONObject("user").getString("rollNumber"))) throw new AuthenticationException();
        boolean allowed = EffiplanData.canClock(menu);
        JSONObject request = eventsRequest("USD", start, end, new JSONArray().put("mvt")).put("rollNumber", new JSONArray().put(user));
        JSONObject data = call("/v3/activities/events?roleId=" + encode(role), request);
        return new JSONObject().put("allowed", allowed).put("data", data);
    }
    void punch() throws Exception {
        // Called only by the controller after the explicit tap, fresh checks and durable pending record.
        String form = "rollNumber=" + encode(user) + "&decalhor=" + (-OffsetDateTime.now().getOffset().getTotalSeconds() / 60);
        exchange("/v1/self/clocks?roleId=" + encode(role), form, true);
    }
    private JSONObject call(String path, JSONObject body) throws Exception {
        return exchange(path, body == null ? null : body.toString(), false);
    }
    private JSONObject exchange(String path, String body, boolean attendanceWrite) throws Exception {
        if (attendanceWrite) {
            if (!path.equals("/v1/self/clocks?roleId=" + encode(role)) || body == null) throw new Exception("Operazione non consentita");
        } else if (!path.startsWith("/v2/users/settings/menu?") && !path.matches("/v1/settings/functions/(UFH|UPC|USD)/models(/\\d+)?\\?.*") && !path.startsWith("/v3/activities/events?") && !path.startsWith("/v1/users/settings/context?") && !path.startsWith("/v2/settings/lists/types/SEH?") && !path.startsWith("/v1/settings/lists/types?")
            && !path.equals("/v1/synoptic/" + encode(user) + "/personal?roleId=" + encode(role))
            && !path.equals("/v2/employees/" + encode(user) + "?categories=identity&roleId=" + encode(role))) {
            throw new Exception("Operazione non consentita");
        }
        String endpoint = origin + "/webquartz/hq-web/api" + path;
        HttpURLConnection connection = (HttpURLConnection) new java.net.URL(endpoint).openConnection();
        try {
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setRequestProperty("x-hq-token", token); connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-hq-idTransaction", String.valueOf(Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 10000000000L)));
            connection.setRequestProperty("x-hq-decalhor", String.valueOf(-OffsetDateTime.now().getOffset().getTotalSeconds() / 60));
            String cookie = CookieManager.getInstance().getCookie(endpoint);
            if (cookie != null) connection.setRequestProperty("Cookie", cookie);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", attendanceWrite ? "application/x-www-form-urlencoded" : "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status == 401 || (status >= 300 && status < 400)) throw new AuthenticationException();
            if (status == 403) throw new Exception("Il portale non autorizza questa lettura.");
            if (status < 200 || status >= 300) throw new Exception("Lettura Effiplan non riuscita");
            if (connection.getContentType() == null || !connection.getContentType().contains("application/json")) throw new Exception("Formato Effiplan non supportato");
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096]; int length;
                while ((length = input.read(buffer)) != -1) {
                    if (output.size() + length > 4 * 1024 * 1024) throw new Exception("Risposta troppo grande");
                    output.write(buffer, 0, length);
                }
            }
            JSONObject response = new JSONObject(output.toString("UTF-8"));
            if (!"SUCCESS".equals(response.optString("status"))) throw new Exception("Richiesta non autorizzata o non disponibile");
            return attendanceWrite ? response : response.getJSONObject("content");
        } finally { connection.disconnect(); }
    }
    private String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
}

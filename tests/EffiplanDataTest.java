package it.giovaganzerli.planning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Synthetic fixtures only: no operational origins, employee IDs or captured personal data. */
public class EffiplanDataTest {
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    interface Checked { void run() throws Exception; }
    static void rejects(Checked action, String label) throws Exception {
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError(label);
    }
    static JSONObject content(String id, JSONObject events) throws Exception {
        return new JSONObject().put("persons", new JSONArray().put(new JSONObject().put("resource", id).put("name", "Persona test").put("events", events)));
    }
    static JSONObject post(String start, String end) throws Exception {
        return new JSONObject().put("title", "Vendita").put("abrev", "V").put("post", "TEST")
            .put("period", new JSONObject().put("sdt", start).put("edt", end));
    }
    static JSONObject movement(String direction, String time) throws Exception {
        return new JSONObject().put("direction", direction).put("period", new JSONObject().put("realdt", time));
    }
    public static void main(String[] args) throws Exception {
        JSONArray roster = new JSONArray().put(new JSONObject().put("uuid", "opaque-member-a").put("name", "Nome Test"))
            .put(new JSONObject().put("uuid", "opaque-member-b").put("name", "Nome Test"));
        java.util.Map<String, String> members = EffiplanData.members(roster);
        check(members.size() == 2 && members.get("opaque-member-b").startsWith("Nome Test") && !members.get("opaque-member-a").equals(members.get("opaque-member-b")), "Effiplan members need no E-Planning actor/directory fields; disambiguate equal display names");
        check(EffiplanData.members(new JSONArray()).isEmpty(), "valid empty roster");
        rejects(() -> EffiplanData.members(new JSONArray().put(new JSONObject().put("uuid", "test").put("name", ""))), "incomplete roster must not silently discard members");
        rejects(() -> EffiplanData.members(new JSONArray().put(roster.getJSONObject(0)).put(roster.getJSONObject(0))), "duplicate member IDs fail closed");
        LocalDate day = LocalDate.of(2026, 11, 27);
        JSONObject data = content("test-user", new JSONObject().put("pst", new JSONArray().put(post("2026-11-27 09:00", "2026-11-27 13:00"))));
        data.put("datas", new JSONObject().put("post", new JSONObject().put("TEST", new JSONObject().put("colors", new JSONObject().put("back", "#123456")))));
        JSONArray shifts = EffiplanData.shifts(data, day, day, "test-user");
        check(shifts.length() == 1 && shifts.getJSONObject(0).getString("fromHour").equals("2026-11-27T09:00"), "normalize dates");
        JSONObject type = shifts.getJSONObject(0).getJSONObject("hourTypology");
        check(type.getString("color").equals("#123456") && SwapSearch.service(type.getString("translation"), type.getString("abbreviation")) == SwapSearch.Service.SALES, "same service rules and portal color");
        data.getJSONArray("persons").getJSONObject(0).getJSONObject("events").getJSONArray("pst").getJSONObject(0).put("title", "");
        data.getJSONObject("datas").getJSONObject("post").getJSONObject("TEST").put("title", "V-REPARTO");
        check(EffiplanData.shifts(data, day, day, "test-user").getJSONObject(0).getJSONObject("hourTypology").getString("translation").equals("V-REPARTO"), "official post title lives in metadata, not event");
        check(SwapSearch.service(EffiplanRules.serviceLabel("V-REPARTO"), "") == SwapSearch.Service.SALES, "legacy sales label");
        check(SwapSearch.service(EffiplanRules.serviceLabel("PERMA"), "") == SwapSearch.Service.BLOCKED, "legacy permanence excluded");
        check(SwapSearch.service(EffiplanRules.serviceLabel("TRUCKTOPEG"), "") == SwapSearch.Service.SALES, "legacy truck to peg");
        check(SwapSearch.service(EffiplanRules.serviceLabel("NON NOTO"), "") == SwapSearch.Service.UNKNOWN, "do not guess unknown services");
        JSONObject event = data.getJSONArray("persons").getJSONObject(0).getJSONObject("events").getJSONArray("pst").getJSONObject(0);
        event.put("task", "new-qualification").put("need", "new-location").put("activity", "new-activity");
        data.getJSONObject("datas").put("task", new JSONObject().put("new-qualification", new JSONObject().put("colors", new JSONObject().put("back", "12aBef").put("font", "fEdCbA"))));
        JSONObject qualification = EffiplanData.shifts(data, day, day, "test-user", new JSONObject().put("color", "POST_COLOR_QUALIFICATION")).getJSONObject(0).getJSONObject("hourTypology");
        check(qualification.getString("color").equals("#12ABEF") && qualification.getString("fontColor").equals("#FEDCBA"), "read arbitrary API palette and font, normalize bare hex");
        data.getJSONObject("datas").getJSONObject("task").getJSONObject("new-qualification").getJSONObject("colors").put("back", "c0ffee");
        check(EffiplanData.shifts(data, day, day, "test-user", new JSONObject().put("color", "POST_COLOR_QUALIFICATION")).getJSONObject(0).getJSONObject("hourTypology").getString("color").equals("#C0FFEE"), "changed API colors not cached by type name");
        data.getJSONObject("datas").put("need", new JSONObject().put("new-location", new JSONObject().put("colors", new JSONObject().put("back", "#ABCDEF").put("font", "010203"))));
        check(EffiplanData.shifts(data, day, day, null, new JSONObject().put("color", "POST_COLOR_LOCATION")).getJSONObject(0).getJSONObject("hourTypology").getString("color").equals("#ABCDEF"), "model selects location palette");
        data.getJSONObject("datas").put("activity", new JSONObject().put("new-activity", new JSONObject().put("colors", new JSONObject().put("back", "112233"))));
        check(EffiplanData.shifts(data, day, day, null, new JSONObject().put("color", "POST_COLOR_ACTIVITY")).getJSONObject(0).getJSONObject("hourTypology").getString("color").equals("#112233"), "model selects activity palette");
        check(EffiplanRules.color("not a color", "#FFFFFF").equals("#FFFFFF"), "missing/invalid API color uses neutral fallback only");
        JSONArray sections = new JSONArray().put(new JSONObject().put("value", "shop-test").put("title", "Negozio sintetico"))
            .put(new JSONObject().put("value", "section-one").put("title", "Squadra arbitraria"));
        JSONArray favorites = new JSONArray().put(new JSONObject().put("title", "Squadra duplicata").put("datas", new JSONObject().put("type", "SEH").put("value", "section-one")))
            .put(new JSONObject().put("title", "Preferito dinamico").put("datas", new JSONObject().put("type", "SEL").put("value", "favorite-test")));
        JSONArray context = new JSONArray().put(new JSONObject().put("property", "GENERAL.COLLECTIVE.GROUPMENT").put("value", new JSONObject().put("title", "Predefinita del profilo").put("datas", new JSONObject().put("type", "SEH").put("value", "section-one")).toString()));
        JSONArray choices = EffiplanData.populations(sections, favorites, context);
        check(choices.length() == 5, "API sections, deduplicated favorites, default and authorized population");
        check(EffiplanData.population(choices, "DEFAULT").getString("value").equals("section-one"), "default from authenticated profile");
        check(EffiplanData.population(choices, "SEH:shop-test").getString("title").equals("Negozio sintetico"), "store node from API, no embedded store ID");
        check(EffiplanData.population(choices, "ALL").getString("type").isEmpty() && EffiplanData.population(choices, "ALL").getString("value").isEmpty(), "unrestricted authorized population uses no guessed store code");
        JSONArray other = EffiplanData.populations(new JSONArray().put(new JSONObject().put("value", "different-section").put("title", "Altro negozio")), new JSONArray(), new JSONArray());
        rejects(() -> EffiplanData.population(other, "SEH:section-one"), "no previous user/store options leaking into new session");
        rejects(() -> EffiplanData.population(other, "DEFAULT"), "missing default must not silently widen to all employees");
        String scope = EffiplanRules.scopeKey("https://example.invalid", "test-user/role");
        check(scope.matches("[a-f0-9]{64}") && scope.equals(EffiplanRules.scopeKey("https://example.invalid", "test-user/role")), "safe stable local namespace");
        check(!scope.equals(EffiplanRules.scopeKey("https://example.invalid", "second-user/role")) && !scope.equals(EffiplanRules.scopeKey("https://other.invalid", "test-user/role")), "local preferences scoped to role, user and portal origin");
        rejects(() -> EffiplanData.shifts(data, day, day, "other-user"), "identity mismatch");
        check(EffiplanData.shifts(data, day.plusDays(1), day.plusDays(1), null).length() == 0, "range filtering");
        JSONObject bad = content("test-user", new JSONObject().put("pst", new JSONArray().put(post("2026-11-27 13:00", "2026-11-27 09:00"))));
        rejects(() -> EffiplanData.shifts(bad, day, day, null), "invalid interval");
        JSONObject night = content("test-user", new JSONObject().put("pst", new JSONArray().put(post("2026-11-27 22:00", "2026-11-28 06:00"))));
        check(EffiplanData.shifts(night, day, day, null).length() == 1, "overnight shift");
        data.getJSONArray("persons").getJSONObject(0).put("hidden", true);
        rejects(() -> EffiplanData.shifts(data, day, day, null), "hidden planning fails closed");
        JSONObject absence = content("test-user", new JSONObject().put("abs", new JSONArray().put(new JSONObject().put("period", new JSONObject().put("sd", "2026-11-26").put("ed", "2026-11-28")))));
        JSONObject block = EffiplanData.absences(absence, day, day).getJSONObject(0);
        check(block.getString("from").equals("2026-11-27T00:00") && block.getString("to").equals("2026-11-28T00:00"), "clip inclusive absence days");
        JSONObject timed = content("test-user", new JSONObject().put("abs", new JSONArray().put(new JSONObject().put("timePeriod", new JSONObject().put("sdt", "2026-11-27 10:00").put("edt", "2026-11-27 12:00")))));
        check(EffiplanData.absences(timed, day, day).getJSONObject(0).getString("from").endsWith("T10:00"), "timed absence");
        JSONObject menu = new JSONObject().put("menu", new JSONObject().put("entries", new JSONArray().put(new JSONObject().put("code", "clock").put("authorizations", new JSONArray().put(new JSONObject().put("global", "R"))))));
        check(!EffiplanData.canClock(menu), "read permission must not allow punching");
        menu.getJSONObject("menu").getJSONArray("entries").getJSONObject(0).getJSONArray("authorizations").getJSONObject(0).put("global", "CRUD");
        check(EffiplanData.canClock(menu), "official update permission");
        JSONObject events = new JSONObject().put("mvt", new JSONArray()
            .put(movement("OUT", "2026-11-27 12:00")).put(movement("IN", "2026-11-27 09:00")));
        JSONObject status = EffiplanData.movements(content("test-user", events), "test-user", day.atStartOfDay(), day.atTime(14, 0));
        check(status.getString("status").equals("ENTRY") && status.getJSONArray("recordWorkdayDto").length() == 2, "unordered server movements");
        check(EffiplanData.movements(content("test-user", events), "test-user", day.atStartOfDay(), day.atTime(10, 0)).getString("status").equals("DEPARTURE"), "ignore future records");
        check(EffiplanData.movements(content("test-user", new JSONObject().put("mvt", new JSONArray())), "test-user", day.atStartOfDay(), day.atTime(10, 0)).getString("status").equals("ENTRY"), "empty valid movements");
        rejects(() -> EffiplanData.movements(content("other-user", events), "test-user", day.atStartOfDay(), day.atTime(14, 0)), "movement identity mismatch");
        rejects(() -> EffiplanData.movements(content("test-user", new JSONObject()), "test-user", day.atStartOfDay(), day.atTime(14, 0)), "missing movements fail closed");
        JSONObject unknown = content("test-user", new JSONObject().put("mvt", new JSONArray().put(movement("UNKNOWN", "2026-11-27 09:00"))));
        rejects(() -> EffiplanData.movements(unknown, "test-user", day.atStartOfDay(), day.atTime(14, 0)), "unknown direction fails closed");
        System.out.println("EffiplanData: all synthetic fixture tests passed");
    }
}

package it.giovaganzerli.planning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Conversion is shared by personal planning, team planning and attendance; no network or writes. */
final class EffiplanData {
    static java.util.Map<String, String> members(JSONArray people) throws Exception {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.getJSONObject(i);
            String id = person.getString("uuid").trim(), name = person.getString("name").trim();
            if (id.isEmpty() || name.isEmpty() || result.containsKey(id)) throw new Exception("Collaboratori Effiplan incompleti");
            result.put(id, name);
        }
        java.util.Map<String, Integer> names = new java.util.HashMap<>();
        for (String name : result.values()) names.put(name, names.getOrDefault(name, 0) + 1);
        result.replaceAll((id, name) -> names.get(name) > 1 ? name + " · " + id : name);
        return result;
    }
    static JSONArray shifts(JSONObject content, LocalDate start, LocalDate end, String expectedUser) throws Exception {
        return shifts(content, start, end, expectedUser, new JSONObject().put("color", "POST_COLOR_POST"));
    }
    static JSONArray personalPlanning(JSONObject content, LocalDate start, LocalDate end, String expectedUser, JSONObject display) throws Exception {
        JSONArray result = shifts(content, start, end, expectedUser, display);
        JSONArray persons = content.getJSONArray("persons");
        JSONObject datas = content.optJSONObject("datas");
        JSONObject reasons = datas == null ? null : datas.optJSONObject("reason");
        for (int i = 0; i < persons.length(); i++) {
            JSONObject person = persons.getJSONObject(i);
            String id = person.getString("resource");
            if (!expectedUser.equals(id) || person.optBoolean("hidden")) throw new Exception("Identità planning non corrispondente");
            JSONObject events = person.getJSONObject("events");
            for (String eventType : new String[] { "abs", "req" }) {
                JSONArray absences = events.optJSONArray(eventType);
                if (absences == null) continue;
                boolean nonWorking = "req".equals(eventType);
                for (int j = 0; j < absences.length(); j++) {
                    JSONObject absence = absences.getJSONObject(j);
                if (absence.optBoolean("disabled")) throw new Exception("Assenza riservata o non disponibile");
                String code = absence.optString("reason", "Assenza"), title = code;
                JSONObject metadata = reasons == null ? null : reasons.optJSONObject(code);
                if (metadata != null && !metadata.optString("title").trim().isEmpty()) title = metadata.getString("title").trim();
                JSONObject colors = metadata == null ? null : metadata.optJSONObject("colors");
                JSONObject period = absence.optJSONObject("timePeriod");
                if (period != null) {
                    LocalDateTime from = EffiplanRules.time(period.getString("sdt")), to = EffiplanRules.time(period.getString("edt"));
                    if (!to.isAfter(from) || from.toLocalDate().isBefore(start) || from.toLocalDate().isAfter(end)) continue;
                    result.put(absenceShift(id, person, code, title, colors, from, to, false, nonWorking));
                } else {
                    JSONObject days = absence.optJSONObject("period");
                    if (days == null || !days.has("sd") || !days.has("ed")) continue;
                    LocalDate first = LocalDate.parse(days.getString("sd")), last = LocalDate.parse(days.getString("ed"));
                    if (last.isBefore(first)) throw new Exception("Assenza giornaliera non valida");
                    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1))
                        if (!date.isBefore(start) && !date.isAfter(end))
                            result.put(absenceShift(id, person, code, title, colors, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), true, true));
                }
                }
            }
        }
        return result;
    }
    private static JSONObject absenceShift(String id, JSONObject person, String code, String title, JSONObject colors, LocalDateTime from, LocalDateTime to, boolean allDay, boolean nonWorking) throws Exception {
        return new JSONObject().put("fromHour", from.toString()).put("toHour", to.toString()).put("date", from.toLocalDate().toString())
            .put("absence", true).put("allDay", allDay).put("nonWorking", nonWorking).put("collaboratorUuid", id).put("collaborator", new JSONObject().put("uuid", id).put("name", person.optString("name", "")))
            .put("hourTypology", new JSONObject().put("translation", title).put("code", code).put("abbreviation", "")
                .put("color", EffiplanRules.color(colors == null ? "" : colors.optString("back"), "#7E22CE"))
                .put("fontColor", EffiplanRules.color(colors == null ? "" : colors.optString("font"), "#FFFFFF")));
    }
    static JSONArray shifts(JSONObject content, LocalDate start, LocalDate end, String expectedUser, JSONObject display) throws Exception {
        JSONArray persons = content.getJSONArray("persons"), shifts = new JSONArray();
        JSONObject postsData = content.optJSONObject("datas");
        postsData = postsData == null ? null : postsData.optJSONObject("post");
        for (int i = 0; i < persons.length(); i++) {
            JSONObject person = persons.getJSONObject(i);
            String id = person.getString("resource");
            if (expectedUser != null && !expectedUser.equals(id)) throw new Exception("Identità planning non corrispondente");
            if (person.optBoolean("hidden")) throw new Exception("Planning riservato");
            JSONArray posts = person.getJSONObject("events").optJSONArray("pst");
            if (posts == null) continue;
            for (int j = 0; j < posts.length(); j++) {
                JSONObject post = posts.getJSONObject(j), period = post.getJSONObject("period");
                if (post.optBoolean("disabled")) throw new Exception("Turno riservato o non disponibile");
                LocalDateTime from = EffiplanRules.time(period.getString("sdt")), to = EffiplanRules.time(period.getString("edt"));
                if (!to.isAfter(from)) throw new Exception("Intervallo non leggibile");
                if (from.toLocalDate().isBefore(start) || from.toLocalDate().isAfter(end)) continue;
                JSONObject metadata = postsData == null ? null : postsData.optJSONObject(post.optString("post"));
                JSONObject colors = postColors(content.optJSONObject("datas"), post, display.optString("color"));
                String title = post.optString("title", "").trim();
                if (title.isEmpty() && metadata != null) title = metadata.optString("title", "").trim();
                if (title.isEmpty()) throw new Exception("Tipo di turno non leggibile");
                shifts.put(new JSONObject().put("fromHour", from.toString()).put("toHour", to.toString()).put("date", from.toLocalDate().toString())
                    .put("collaboratorUuid", id).put("collaborator", new JSONObject().put("uuid", id).put("name", person.optString("name", "")))
                    .put("hourTypology", new JSONObject().put("translation", title)
                         .put("code", post.optString("post", title))
                         .put("abbreviation", post.optString("abrev", ""))
                         .put("color", EffiplanRules.color(colors.optString("back"), "#FFFFFF"))
                         .put("fontColor", EffiplanRules.color(colors.optString("font"), "#000000"))));
            }
        }
        return shifts;
    }
    private static JSONObject postColors(JSONObject datas, JSONObject post, String mode) {
        // Select the dictionary configured by this user's model, never by observed labels or colors.
        String source;
        switch (mode) {
            case "POST_COLOR_QUALIFICATION": source = "task"; break;
            case "POST_COLOR_LOCATION": case "POST_COLOR_NEED": source = "need"; break;
            case "POST_COLOR_ACTIVITY": source = "activity"; break;
            case "POST_COLOR_POST": source = "post"; break;
            default: return new JSONObject();
        }
        JSONObject dictionary = datas == null ? null : datas.optJSONObject(source);
        JSONObject entry = dictionary == null ? null : dictionary.optJSONObject(post.optString(source));
        JSONObject colors = entry == null ? null : entry.optJSONObject("colors");
        return colors == null ? new JSONObject() : colors;
    }
    static String populationKey(String type, String value) { return type + ":" + value; }
    static JSONArray populations(JSONArray sections, JSONArray favorites, JSONArray context) throws Exception {
        JSONArray choices = new JSONArray();
        JSONObject initial = null;
        for (int i = 0; i < context.length(); i++) {
            JSONObject property = context.getJSONObject(i);
            if ("GENERAL.COLLECTIVE.GROUPMENT".equals(property.optString("property"))) {
                JSONObject saved = new JSONObject(property.getString("value"));
                JSONObject datas = saved.optJSONObject("datas");
                if (datas != null && !datas.optString("type").isEmpty() && !datas.optString("value").isEmpty()) initial = saved;
            }
        }
        if (initial != null) {
            JSONObject datas = initial.getJSONObject("datas");
            choices.put(new JSONObject().put("key", "DEFAULT").put("type", datas.getString("type"))
                .put("value", datas.getString("value")).put("title", "Predefinita · " + initial.optString("title", "La mia squadra")));
        }
        choices.put(new JSONObject().put("key", "ALL").put("type", "").put("value", "").put("title", "Tutta la popolazione autorizzata"));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.getJSONObject(i);
            String value = section.getString("value"), key = populationKey("SEH", value);
            if (!seen.add(key)) continue;
            choices.put(new JSONObject().put("key", key).put("type", "SEH").put("value", value).put("title", section.getString("title")));
        }
        for (int i = 0; i < favorites.length(); i++) {
            JSONObject favorite = favorites.getJSONObject(i), datas = favorite.getJSONObject("datas");
            String type = datas.getString("type"), value = datas.getString("value"), key = populationKey(type, value);
            if (type.isEmpty() || value.isEmpty() || !seen.add(key)) continue;
            choices.put(new JSONObject().put("key", key).put("type", type).put("value", value).put("title", favorite.getString("title")));
        }
        return choices;
    }
    static JSONObject population(JSONArray choices, String key) throws Exception {
        for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.getJSONObject(i);
            if (key.equals(choice.getString("key")) || key.equals(populationKey(choice.getString("type"), choice.getString("value")))) return choice;
        }
        throw new Exception("Popolazione non più disponibile: scegli nuovamente la squadra.");
    }
    static JSONArray absences(JSONObject content, LocalDate start, LocalDate end) throws Exception {
        JSONArray people = content.getJSONArray("persons"), result = new JSONArray();
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.getJSONObject(i);
            JSONArray absences = person.getJSONObject("events").optJSONArray("abs");
            if (absences == null) continue;
            for (int j = 0; j < absences.length(); j++) {
                JSONObject absence = absences.getJSONObject(j);
                JSONObject timed = absence.optJSONObject("timePeriod"), period = absence.optJSONObject("period");
                LocalDateTime from, to;
                if (timed != null || (period != null && period.has("sdt"))) {
                    JSONObject p = timed == null ? period : timed;
                    from = EffiplanRules.time(p.getString("sdt")); to = EffiplanRules.time(p.getString("edt"));
                } else if (period != null) {
                    // Half-day and confidential absences are conservatively blocked for the entire day.
                    from = LocalDate.parse(period.getString("sd")).atStartOfDay();
                    to = LocalDate.parse(period.getString("ed")).plusDays(1).atStartOfDay();
                } else throw new Exception("Assenza non leggibile: ricerca non sicura");
                if (!to.isAfter(from)) throw new Exception("Assenza non valida");
                if (from.isBefore(start.atStartOfDay())) from = start.atStartOfDay();
                if (to.isAfter(end.plusDays(1).atStartOfDay())) to = end.plusDays(1).atStartOfDay();
                if (to.isAfter(from)) result.put(new JSONObject().put("person", person.getString("resource")).put("from", from.toString()).put("to", to.toString()));
            }
        }
        return result;
    }
    static boolean canClock(JSONObject menu) throws Exception {
        JSONArray entries = menu.getJSONObject("menu").getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (!"clock".equals(entry.optString("code"))) continue;
            JSONArray authorizations = entry.optJSONArray("authorizations");
            if (authorizations == null) return false;
            for (int j = 0; j < authorizations.length(); j++)
                if (authorizations.getJSONObject(j).optString("global").contains("U")) return true;
        }
        return false;
    }
    static JSONObject movements(JSONObject content, String user, LocalDateTime since, LocalDateTime now) throws Exception {
        JSONArray people = content.getJSONArray("persons"), records = new JSONArray();
        LocalDateTime latest = null;
        String status = "ENTRY";
        if (people.length() != 1) throw new Exception("Movimenti personali incompleti");
        JSONObject person = people.getJSONObject(0);
        if (!user.equals(person.getString("resource")) || person.optBoolean("hidden")) throw new Exception("Movimenti non disponibili");
        JSONArray movements = person.getJSONObject("events").optJSONArray("mvt");
        if (movements == null) throw new Exception("Movimenti non leggibili");
        for (int j = 0; j < movements.length(); j++) {
            JSONObject movement = movements.getJSONObject(j), period = movement.getJSONObject("period");
            String action = EffiplanRules.movementAction(movement.getString("direction"));
            String value = period.optString("validateddt", "");
            if (value.isEmpty()) value = period.getString("realdt");
            LocalDateTime at = WorkdayRules.time(value.replace(' ', 'T'));
            if (at.isAfter(now)) continue;
            records.put(new JSONObject().put(action.equals("ENTRY") ? "entry" : "departure", at.toString()));
            if (!at.isBefore(since) && (latest == null || at.isAfter(latest))) {
                latest = at; status = action.equals("ENTRY") ? "DEPARTURE" : "ENTRY";
            }
        }
        return new JSONObject().put("status", status).put("recordWorkdayDto", records);
    }
}

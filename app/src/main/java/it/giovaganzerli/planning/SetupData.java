package it.giovaganzerli.planning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SetupData {
    /** Global planning is a day-level, collaborator-authorised read, not a manager roster. */
    static JSONObject eplanningGlobalDay(JSONArray groups, String teamName, java.time.LocalDate day) throws Exception {
        JSONObject members = new JSONObject();
        JSONArray shifts = new JSONArray();
        int matches = 0;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            if (teamName != null && !teamName.trim().equalsIgnoreCase(group.getString("teamName").trim())) continue;
            if (teamName != null && ++matches > 1) throw new Exception("Nome squadra ambiguo nel planning globale");
            JSONArray people = group.getJSONArray("collaboratorsPlanningGlobalDto");
            for (int j = 0; j < people.length(); j++) {
                JSONObject person = people.getJSONObject(j);
                String id = person.getString("collaboratorUuid");
                if (!uuid(id)) throw new Exception("Collaboratore globale non valido");
                members.put(id, (person.optString("collaboratorName") + " " + person.optString("collaboratorFirsLastName")).trim());
                JSONArray periods = person.getJSONArray("periods");
                for (int k = 0; k < periods.length(); k++) {
                    JSONObject period = periods.getJSONObject(k);
                    if (!id.equals(period.getString("collaboratorUuid"))) throw new Exception("Identità turno globale non corrispondente");
                    java.time.LocalDateTime from = java.time.LocalDateTime.parse(period.getString("fromHour"));
                    java.time.LocalDateTime to = java.time.LocalDateTime.parse(period.getString("toHour"));
                    if (!to.isAfter(from)) throw new Exception("Orari globali non validi");
                    if (!from.toLocalDate().equals(day)) continue;
                    shifts.put(new JSONObject(period.toString()).put("date", day.toString())
                        .put("hourTypology", new JSONObject().put("uuid", period.optString("hourTypologyUuid"))
                            .put("translation", period.optString("translation", "Turno")).put("color", period.optString("hourTypologyColor", "#2563EB"))));
                }
            }
        }
        return new JSONObject().put("members", members).put("shifts", shifts);
    }
    static boolean eplanningAuthorizationDenied(String body) {
        try {
            JSONArray errors = new JSONObject(body).optJSONArray("exceptions");
            if (errors != null) for (int i = 0; i < errors.length(); i++)
                if ("PROFILE_AUTHORIZATION_ERROR".equals(errors.getJSONObject(i).optString("translationCode"))) return true;
        } catch (Exception ignored) { }
        return false;
    }
    static SetupRules.Team uniqueMembership(List<SetupRules.Team> matches) throws Exception {
        if (matches.size() != 1) throw new Exception("Appartenenza assente o ambigua: seleziona la squadra manualmente.");
        return matches.get(0);
    }
    static JSONObject activeCondition(JSONObject person) {
        JSONObject condition = person.optJSONObject("activeLaborCondition");
        return condition == null ? person.optJSONObject("latestSuccessfulLaborCondition") : condition;
    }
    static SetupRules.Team eplanningMembership(JSONObject profile, String expectedUser) throws Exception {
        JSONObject person = profile.getJSONObject("collaborator");
        if (!expectedUser.equals(person.getString("uuid"))) throw new Exception("Il profilo non corrisponde all’utente.");
        JSONObject condition = activeCondition(person);
        JSONObject team = condition == null ? null : condition.optJSONObject("team");
        if (team == null || !uuid(team.optString("uuid"))) throw new Exception("Squadra effettiva non disponibile.");
        String title = team.optString("name", team.optString("description", "")).trim();
        if (title.isEmpty()) throw new Exception("Nome squadra non disponibile.");
        return new SetupRules.Team(team.getString("uuid"), title);
    }
    static SetupRules.Team effiplanMembership(JSONObject content) throws Exception {
        JSONObject section = content.optJSONObject("section");
        if (section == null) {
            JSONObject identity = content.optJSONObject("identity");
            JSONObject core = identity == null ? null : identity.optJSONObject("core");
            JSONObject assignment = core == null ? null : core.optJSONObject("affectation");
            JSONObject field = assignment == null ? null : assignment.optJSONObject("section");
            section = field == null ? null : field.optJSONObject("listvalue");
        }
        if (section == null || section.optString("code").isEmpty() || section.optString("title").isEmpty())
            throw new Exception("Il portale non restituisce la squadra effettiva del profilo.");
        return new SetupRules.Team(EffiplanData.populationKey("SEH", section.getString("code")), section.getString("title"));
    }
    static java.util.Map<String, String> eplanningMembers(Object value) throws Exception {
        java.util.Map<String, String> members = new LinkedHashMap<>();
        collectMembers(value, members);
        return members;
    }
    private static void collectMembers(Object value, java.util.Map<String, String> members) throws Exception {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectMembers(array.get(i), members);
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String id = object.optString("uuid", object.optString("collaboratorUuid"));
            if (uuid(id) && (object.has("actor") || object.has("firstLastName") || object.has("directoryUuid"))) {
                String name = object.optString("displayName", object.optString("fullName", object.optString("name"))).trim();
                if (name.isEmpty()) name = (object.optString("firstName", object.optString("firstname")) + " "
                    + object.optString("lastName", object.optString("lastname"))).trim();
                String surname = object.optString("firstLastName");
                if (!surname.isEmpty() && !object.has("displayName") && !object.has("fullName")) name = (name + " " + surname).trim();
                if (!name.isEmpty()) members.put(id, name);
                return;
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) collectMembers(object.get(keys.next()), members);
        }
    }
    static List<SetupRules.Team> effiplanTeams(JSONArray choices) throws Exception {
        Map<String, SetupRules.Team> teams = new LinkedHashMap<>();
        for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.getJSONObject(i);
            String type = choice.getString("type"), value = choice.getString("value");
            if (type.isEmpty() || value.isEmpty() || "EMP".equals(type)) continue;
            String key = EffiplanData.populationKey(type, value);
            String title = choice.getString("title").replaceFirst("^Predefinita · ", "").trim();
            if (!title.isEmpty()) teams.put(key, new SetupRules.Team(key, title));
        }
        return new ArrayList<>(teams.values());
    }
    static boolean uuid(String value) { return value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"); }
    static List<SetupRules.Team> eplanningTeams(Object response) throws Exception {
        if (response instanceof JSONObject) {
            JSONObject object = (JSONObject) response;
            for (String field : new String[] { "teams", "data", "content", "items" }) {
                Object nested = object.opt(field);
                if (nested instanceof JSONArray || nested instanceof JSONObject) return eplanningTeams(nested);
            }
            throw new Exception("Elenco squadre non leggibile");
        }
        if (!(response instanceof JSONArray)) throw new Exception("Elenco squadre non leggibile");
        Map<String, SetupRules.Team> teams = new LinkedHashMap<>();
        JSONArray array = (JSONArray) response;
        for (int i = 0; i < array.length(); i++) {
            JSONObject team = array.getJSONObject(i);
            String id = team.getString("uuid"), title = team.optString("name", "").trim();
            if (title.isEmpty()) title = team.optString("translation", "").trim();
            if (title.isEmpty()) title = team.optString("description", "").trim();
            if (!uuid(id) || title.isEmpty()) throw new Exception("Dati squadra incompleti");
            teams.put(id, new SetupRules.Team(id, title));
        }
        return new ArrayList<>(teams.values());
    }
}

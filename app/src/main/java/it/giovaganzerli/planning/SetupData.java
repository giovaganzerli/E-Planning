package it.giovaganzerli.planning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SetupData {
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

package it.giovaganzerli.planning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Synthetic first-run fixtures: no users, teams, origins or calendars from production. */
public class SetupDataTest {
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    interface Checked { void run() throws Exception; }
    static void rejects(Checked action, String label) throws Exception {
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        JSONArray sections = new JSONArray().put(new JSONObject().put("value", "new-team").put("title", "Squadra test"));
        JSONArray populations = EffiplanData.populations(sections, new JSONArray(), new JSONArray());
        List<SetupRules.Team> teams = SetupData.effiplanTeams(populations);
        check(teams.size() == 1 && teams.get(0).key.equals("SEH:new-team"), "first Effiplan login works without server DEFAULT");
        check(!teams.get(0).key.equals("ALL"), "no silent widening to entire store");
        check(EffiplanData.population(populations, teams.get(0).key).getString("value").equals("new-team"), "persisted own team can be queried without portal context");
        JSONArray context = new JSONArray().put(new JSONObject().put("property", "GENERAL.COLLECTIVE.GROUPMENT")
            .put("value", new JSONObject().put("title", "Squadra test").put("datas", new JSONObject().put("type", "SEH").put("value", "new-team")).toString()));
        populations = EffiplanData.populations(sections, new JSONArray(), context);
        check(SetupData.effiplanTeams(populations).size() == 1, "normalize and deduplicate DEFAULT and explicit team");
        JSONArray contextOnly = EffiplanData.populations(new JSONArray(), new JSONArray(), context);
        String contextKey = SetupData.effiplanTeams(contextOnly).get(0).key;
        check(EffiplanData.population(contextOnly, contextKey).getString("value").equals("new-team"), "context-only option has a stable explicit key");
        JSONArray individual = new JSONArray().put(new JSONObject().put("key", "EMP:test").put("type", "EMP").put("value", "test").put("title", "Persona test"));
        check(SetupData.effiplanTeams(individual).isEmpty(), "a person is not a membership team");
        String id = "00000000-0000-0000-0000-000000000001";
        JSONArray eplanning = new JSONArray().put(new JSONObject().put("uuid", id).put("name", "Squadra nuova"));
        check(SetupData.eplanningTeams(eplanning).get(0).key.equals(id), "E-Planning team IDs and names from API");
        check(SetupData.eplanningTeams(new JSONObject().put("data", new JSONObject().put("content", eplanning))).size() == 1, "wrapped team responses");
        rejects(() -> SetupData.eplanningTeams(new JSONArray().put(new JSONObject().put("uuid", "not-uuid").put("name", "Test"))), "invalid team identifiers rejected");
        SetupRules.Draft draft = new SetupRules.Draft();
        check(!SetupRules.valid(draft), "team choice is required");
        draft.teamKey = teams.get(0).key; draft.teamTitle = teams.get(0).title;
        check(!SetupRules.valid(draft), "verified roster is required");
        java.util.Map<String, String> members = new java.util.LinkedHashMap<>();
        members.put(id, "Collega test");
        draft.membership(new SetupRules.Membership(teams.get(0), members));
        check(SetupRules.valid(draft), "calendar opt-out allows setup without calendar/permissions");
        check(draft.roles.get(id) == 0, "roles start excluded rather than guessed");
        draft.roles.put(id, 3);
        draft.membership(new SetupRules.Membership(teams.get(0), members));
        check(draft.roles.get(id) == 3, "same-team refresh preserves draft roles");
        draft.membership(new SetupRules.Membership(new SetupRules.Team("SEH:transferred", "Nuova squadra"), members));
        check(draft.roles.get(id) == 0, "transfer does not inherit old team roles");
        check(SetupRules.transferred("SEH:old", "SEH:transferred"), "transfer detected from actual membership");
        check(!SetupRules.transferred("SEH:old", "DEFAULT"), "browser default cannot trigger a transfer");
        check(SetupRules.normalizeRoles(31) == 16, "manager role excludes other roles");
        check(SetupData.uniqueMembership(java.util.Collections.singletonList(teams.get(0))).key.equals(teams.get(0).key), "unique authorized roster match identifies membership");
        rejects(() -> SetupData.uniqueMembership(java.util.Collections.emptyList()), "missing match must offer manual selection rather than guess");
        rejects(() -> SetupData.uniqueMembership(java.util.Arrays.asList(teams.get(0), teams.get(0))), "ambiguous membership must not pick first section");
        draft.membership(new SetupRules.Membership(teams.get(0), members, "", true, "SEH:detected"));
        check(draft.manualTeam && draft.detectedTeamKey.equals("SEH:detected"), "manual team retains automatic baseline for transfer detection");
        draft.membership(new SetupRules.Membership(teams.get(0), members));
        check(!draft.manualTeam && draft.detectedTeamKey.equals(teams.get(0).key), "automatic membership clears manual provenance");
        SetupRules.requireMember(members, id);
        rejects(() -> SetupRules.requireMember(members, "missing-user"), "manual team must actually contain authenticated user");
        rejects(() -> SetupRules.requireMember(new java.util.HashMap<>(), id), "empty team cannot validate membership");
        check(!members.containsKey("missing-user"), "membership check never inserts authenticated user into roster");
        SetupRules.Draft rejected = new SetupRules.Draft();
        rejected.membership(new SetupRules.Membership(teams.get(0), members));
        rejected.calendarId = 42; rejected.calendarName = "Calendario test";
        SetupRules.clearTeam(rejected);
        check(rejected.teamKey.isEmpty() && rejected.teamTitle.isEmpty() && rejected.members.isEmpty() && rejected.roles.isEmpty(), "rejection clears displayed team and roles");
        check(!SetupRules.valid(rejected) && rejected.calendarId == 42, "rejected selection blocks continue without losing calendar draft");
        check(SetupData.effiplanMembership(new JSONObject().put("section",
            new JSONObject().put("code", "actual").put("title", "Squadra effettiva"))).key.equals("SEH:actual"), "membership uses employee section");
        JSONObject identity = new JSONObject("{\"identity\":{\"core\":{\"affectation\":{\"section\":{\"listvalue\":{\"code\":\"new\",\"title\":\"Nuova\"}}}}}}");
        check(SetupData.effiplanMembership(identity).key.equals("SEH:new"), "employee identity fallback");
        rejects(() -> SetupData.effiplanMembership(new JSONObject().put("population", "DEFAULT")), "no browser-population fallback");
        JSONObject profile = new JSONObject().put("collaborator", new JSONObject().put("uuid", id)
            .put("activeLaborCondition", new JSONObject().put("team", new JSONObject().put("uuid", id).put("name", "Attuale")))
            .put("latestSuccessfulLaborCondition", new JSONObject().put("team", new JSONObject().put("uuid", id).put("name", "Vecchia"))));
        check(SetupData.eplanningMembership(profile, id).title.equals("Attuale"), "active team wins over historical team");
        rejects(() -> SetupData.eplanningMembership(profile, "another-user"), "profile must match authenticated user");
        JSONObject colleague = new JSONObject().put("uuid", id).put("name", "Nome").put("firstLastName", "Test");
        check(SetupData.eplanningMembers(new JSONObject().put("data", new JSONArray().put(colleague))).get(id).equals("Nome Test"), "team members parsed from wrapped API response");
        check(!SetupRules.completed(0, draft), "incomplete first-run is not skipped");
        check(!SetupRules.completed(1, draft), "upgrade from first onboarding revision requires configuration");
        check(!SetupRules.completed(2, draft), "upgrade from previous test builds requires configuration even if previously saved");
        check(!SetupRules.completed(SetupRules.VERSION, new SetupRules.Draft()), "current marker alone cannot bypass incomplete configuration");
        check(!draft.members.isEmpty() && !draft.teamKey.isEmpty(), "migration preserves prefilled preferences");
        check(SetupRules.completed(SetupRules.VERSION, draft), "completed valid setup can be restored");
        java.util.Map<PlanningPortal, Integer> revisions = new java.util.EnumMap<>(PlanningPortal.class);
        revisions.put(PlanningPortal.EPLANNING, SetupRules.VERSION);
        revisions.put(PlanningPortal.EFFIPLAN, 0);
        check(!SetupRules.requiresSetup(revisions.get(PlanningPortal.EPLANNING), draft, false), "configured E-Planning skips onboarding");
        check(SetupRules.requiresSetup(revisions.get(PlanningPortal.EFFIPLAN), draft, false), "first Effiplan login requires its own configuration");
        revisions.put(PlanningPortal.EFFIPLAN, SetupRules.VERSION);
        for (PlanningPortal portal : new PlanningPortal[] { PlanningPortal.EPLANNING, PlanningPortal.EFFIPLAN, PlanningPortal.EPLANNING, PlanningPortal.EFFIPLAN })
            check(!SetupRules.requiresSetup(revisions.get(portal), draft, false), "switching back to configured portal skips onboarding: " + portal);
        check(SetupRules.requiresSetup(SetupRules.VERSION - 1, draft, false), "migration still requires one confirmation per account and portal");
        check(SetupRules.requiresSetup(SetupRules.VERSION, draft, true), "actual membership transfer still requires confirmation");
        check(SetupRules.requiresSetup(SetupRules.VERSION, new SetupRules.Draft(), false), "incomplete configuration still requires onboarding");
        java.util.Map<String, Integer> accountRevisions = new java.util.HashMap<>();
        for (PlanningPortal portal : PlanningPortal.values()) {
            String firstUser = portal.calendarPreferences(EffiplanRules.scopeKey("https://portal.example.invalid",
                portal == PlanningPortal.EFFIPLAN ? "user-a/SELF" : "user-a"));
            String secondUser = portal.calendarPreferences(EffiplanRules.scopeKey("https://portal.example.invalid",
                portal == PlanningPortal.EFFIPLAN ? "user-b/SELF" : "user-b"));
            check(!firstUser.equals(secondUser), "different users have separate preferences on " + portal);
            accountRevisions.put(firstUser, SetupRules.VERSION);
            check(SetupRules.requiresSetup(accountRevisions.getOrDefault(secondUser, 0), draft, false), "new user must configure even when another user already did: " + portal);
            accountRevisions.put(secondUser, SetupRules.VERSION);
            check(!SetupRules.requiresSetup(accountRevisions.get(firstUser), draft, false), "returning to first configured user skips onboarding: " + portal);
            check(!SetupRules.requiresSetup(accountRevisions.get(secondUser), draft, false), "returning to second configured user skips onboarding: " + portal);
        }
        String sharedScope = EffiplanRules.scopeKey("https://portal.example.invalid", "user-a");
        check(!PlanningPortal.EPLANNING.calendarPreferences(sharedScope).equals(PlanningPortal.EFFIPLAN.calendarPreferences(sharedScope)),
            "even identical account scopes cannot share completion across portals");
        draft.calendarEnabled = true;
        check(!SetupRules.valid(draft), "enabled calendar needs explicit target");
        draft.calendarId = 42;
        check(!SetupRules.valid(draft), "calendar target requires its display name");
        draft.calendarName = "Calendario test";
        check(SetupRules.valid(draft), "calendar and default alerts can be confirmed together");
        check(draft.reminderMinutes[0] == 1440 && draft.reminderMinutes[1] == 60, "preserve one-day/one-hour defaults");
        draft.reminderEnabled[0] = false; draft.reminderEnabled[1] = false;
        check(SetupRules.valid(draft), "user may disable both alerts");
        draft.reminderMinutes[0] = -5;
        check(!SetupRules.valid(draft), "corrupted notification settings cannot complete onboarding");
        draft.reminderMinutes[0] = 1440;
        for (String placeholder : new String[] { "", "DEFAULT", "OWN", "ALL", ":" }) {
            draft.teamKey = placeholder;
            check(!SetupRules.valid(draft), "placeholder is not a persisted membership team");
        }
        System.out.println("SetupData: all onboarding fixture tests passed");
    }
}

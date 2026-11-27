package it.giovaganzerli.planning;

/** First-run settings are local to one portal/account, never a server-side team change. */
final class SetupRules {
    // Require a new explicit confirmation when upgrading from all earlier setup revisions.
    // This is independent of the app version: subsequent releases need not repeat onboarding.
    static final int VERSION = 3;
    static final int[] REMINDER_TIMES = { 0, 5, 10, 15, 30, 60, 120, 240, 720, 1440, 2880, 10080 };
    static final class Team {
        final String key, title;
        Team(String key, String title) { this.key = key; this.title = title; }
    }
    static final class Draft {
        String teamKey = "", teamTitle = "", calendarName = "";
        boolean calendarEnabled;
        boolean manualTeam;
        String detectedTeamKey = "";
        long calendarId = -1;
        final boolean[] reminderEnabled = { true, true };
        final int[] reminderMinutes = { 1440, 60 };
        final java.util.Map<String, String> members = new java.util.LinkedHashMap<>();
        final java.util.Map<String, Integer> roles = new java.util.LinkedHashMap<>();
        void membership(Membership membership) {
            if (!membership.team.key.equals(teamKey)) roles.clear();
            teamKey = membership.team.key; teamTitle = membership.team.title;
            manualTeam = membership.manual; detectedTeamKey = membership.detectedTeamKey;
            members.clear(); members.putAll(membership.members);
            roles.keySet().retainAll(members.keySet());
            for (String id : members.keySet()) roles.put(id, normalizeRoles(roles.getOrDefault(id, 0)));
        }
    }
    static final class Membership {
        final Team team;
        final java.util.Map<String, String> members;
        final String siteKey;
        final boolean manual;
        final String detectedTeamKey;
        Membership(Team team, java.util.Map<String, String> members) { this(team, members, ""); }
        Membership(Team team, java.util.Map<String, String> members, String siteKey) {
            this(team, members, siteKey, false, team.key);
        }
        Membership(Team team, java.util.Map<String, String> members, String siteKey, boolean manual, String detectedTeamKey) {
            this.team = team; this.members = new java.util.LinkedHashMap<>(members); this.siteKey = siteKey;
            this.manual = manual; this.detectedTeamKey = detectedTeamKey;
        }
    }
    static int normalizeRoles(int mask) { return mask < 0 || mask > 31 ? 0 : (mask & 16) != 0 ? 16 : mask; }
    static boolean transferred(String saved, String actual) { return explicitTeam(actual) && !actual.equals(saved); }
    static void requireMember(java.util.Map<String, String> members, String user) {
        if (user == null || user.isEmpty() || !members.containsKey(user))
            throw new IllegalArgumentException("Non risulti tra i membri della squadra selezionata. Seleziona la tua squadra.");
    }
    static void clearTeam(Draft draft) {
        draft.teamKey = ""; draft.teamTitle = ""; draft.manualTeam = false; draft.detectedTeamKey = "";
        draft.members.clear(); draft.roles.clear();
    }
    static boolean explicitTeam(String key) {
        return key != null && !key.trim().isEmpty() && !"DEFAULT".equals(key) && !"ALL".equals(key) && !"OWN".equals(key) && !":".equals(key);
    }
    static boolean valid(Draft draft) {
        if (!explicitTeam(draft.teamKey) || draft.teamTitle == null || draft.teamTitle.trim().isEmpty()) return false;
        if (draft.members.isEmpty()) return false;
        for (String id : draft.members.keySet()) if (!draft.roles.containsKey(id) || draft.roles.get(id) != normalizeRoles(draft.roles.get(id))) return false;
        if (draft.calendarEnabled && (draft.calendarId < 0 || draft.calendarName == null || draft.calendarName.trim().isEmpty())) return false;
        for (int i = 0; i < 2; i++) {
            boolean known = false;
            for (int allowed : REMINDER_TIMES) if (draft.reminderMinutes[i] == allowed) known = true;
            if (!known) return false;
        }
        return true;
    }
    static boolean completed(int version, Draft draft) { return version == VERSION && valid(draft); }
    static boolean requiresSetup(int version, Draft draft, boolean transferred) {
        return transferred || !completed(version, draft);
    }
}

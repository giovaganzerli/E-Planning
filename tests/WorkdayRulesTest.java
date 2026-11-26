package it.giovaganzerli.planning;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class WorkdayRulesTest {
    private static LocalDateTime t(String time) { return LocalDateTime.parse("2026-11-26T" + time); }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        WorkdayRules.Turn morning = new WorkdayRules.Turn(t("09:00"), t("13:00"));
        WorkdayRules.Turn afternoon = new WorkdayRules.Turn(t("14:00"), t("18:00"));
        check(WorkdayRules.currentTurn(Arrays.asList(morning, afternoon), t("08:59")) == null, "before shift");
        check(WorkdayRules.currentTurn(Arrays.asList(morning, afternoon), t("09:00")) != null, "inclusive start");
        check(WorkdayRules.currentTurn(Arrays.asList(morning, afternoon), t("13:00")) == null, "exclusive end");
        check(WorkdayRules.currentTurn(Arrays.asList(morning, afternoon), t("13:30")) == null, "break");
        WorkdayRules.Turn fused = WorkdayRules.currentTurn(Arrays.asList(new WorkdayRules.Turn(t("11:00"), t("13:00")), new WorkdayRules.Turn(t("09:00"), t("11:00"))), t("12:00"));
        check(fused.start.equals(t("09:00")) && fused.end.equals(t("13:00")), "contiguous services form one turn");
        check(!morning.key().equals(afternoon.key()), "next turn has independent duplicate lock");
        check(WorkdayRules.state(null, "ENTRY", false, false) == WorkdayRules.State.OUTSIDE, "no write outside shift");
        check(WorkdayRules.state(morning, "INACTIVE", false, false) == WorkdayRules.State.OUTSIDE, "server inactive");
        check(WorkdayRules.state(morning, "UNKNOWN", false, false) == WorkdayRules.State.OUTSIDE, "unknown status fails closed");
        check(WorkdayRules.state(morning, "ENTRY", false, false) == WorkdayRules.State.READY, "server entry permission");
        check(WorkdayRules.state(morning, "DEPARTURE", false, false) == WorkdayRules.State.READY, "server departure permission");
        check(WorkdayRules.state(morning, "DEPARTURE", true, false) == WorkdayRules.State.DONE, "successful punch never becomes a second click");
        check(WorkdayRules.state(morning, "ENTRY", false, true) == WorkdayRules.State.VERIFY, "uncertain POST never retries automatically");
        check(!WorkdayRules.accepted(200, false) && !WorkdayRules.accepted(500, true) && WorkdayRules.accepted(200, true), "explicit success required");
        String old = WorkdayRules.recordKey("ENTRY", t("09:01"));
        String fresh = WorkdayRules.recordKey("ENTRY", t("09:02"));
        check(WorkdayRules.newRecord("ENTRY", new HashSet<>(Collections.singletonList(old)), Arrays.asList(old, fresh), morning).equals(t("09:02")), "reconcile with a new server record");
        check(WorkdayRules.newRecord("DEPARTURE", Collections.emptySet(), Collections.singletonList(fresh), morning) == null, "action must match");
        check(WorkdayRules.newRecord("ENTRY", Collections.emptySet(), Collections.singletonList(WorkdayRules.recordKey("ENTRY", t("15:00"))), morning) == null, "unrelated turn cannot confirm a punch");
        check(WorkdayRules.time("2026-11-26T08:00:00Z").equals(t("09:00")), "Rome timezone");
        System.out.println("WorkdayRules: all tests passed");
    }
}

package it.giovaganzerli.planning;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.DayOfWeek;
public class PortalRulesTest {
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        check(PlanningPortal.parse("EPLANNING") == PlanningPortal.EPLANNING, "new portal");
        check(PlanningPortal.parse("EFFIPLAN") == PlanningPortal.EFFIPLAN, "legacy portal");
        check(PlanningPortal.parse(null) == PlanningPortal.EPLANNING, "safe default");
        check(PlanningPortal.EFFIPLAN.firstDay() == DayOfWeek.SUNDAY && PlanningPortal.EPLANNING.firstDay() == DayOfWeek.MONDAY, "week convention belongs to the portal");
        LocalDate sunday = LocalDate.of(2026, 11, 22);
        for (int offset = 0; offset < 7; offset++) {
            check(PlanningPortal.EFFIPLAN.weekStart(sunday.plusDays(offset)).equals(sunday), "Effiplan Sunday through Saturday shares one weekly range");
            check(PlanningPortal.EFFIPLAN.weekNumber(sunday.plusDays(offset)) == PlanningPortal.EFFIPLAN.weekNumber(sunday), "consistent week label across Effiplan range");
            check(PlanningPortal.EPLANNING.weekStart(sunday.plusDays(offset + 1)).equals(sunday.plusDays(1)), "E-Planning remains Monday through Sunday");
        }
        check(PlanningPortal.EFFIPLAN.weekStart(sunday.plusDays(7)).equals(sunday.plusDays(7)), "next Sunday starts a new Effiplan week");
        check(PlanningPortal.EPLANNING.weekStart(sunday).equals(sunday.minusDays(6)), "Sunday still closes E-Planning's previous week");
        LocalDate yearBoundary = LocalDate.of(2027, 1, 1);
        check(PlanningPortal.EFFIPLAN.weekStart(yearBoundary).equals(LocalDate.of(2026, 12, 27)), "Effiplan year boundary");
        check(PlanningPortal.EPLANNING.weekStart(yearBoundary).equals(LocalDate.of(2026, 12, 28)), "E-Planning year boundary unchanged");
        check(PlanningPortal.EFFIPLAN.weekStart(LocalDate.of(2026, 11, 1)).equals(LocalDate.of(2026, 11, 1)), "month beginning Sunday has no extra leading week");
        check(PlanningPortal.EFFIPLAN.weekStart(LocalDate.of(2026, 10, 31)).plusDays(6).equals(LocalDate.of(2026, 10, 31)), "month ending Saturday has no extra trailing week");
        check(PlanningPortal.EPLANNING.calendarPreferences().equals("calendar-settings"), "keep previous calendar settings");
        check(!PlanningPortal.EPLANNING.calendarPreferences().equals(PlanningPortal.EFFIPLAN.calendarPreferences()), "isolate calendar settings");
        check(PlanningPortal.EPLANNING.skillsPrefix().isEmpty() && !PlanningPortal.EFFIPLAN.skillsPrefix().isEmpty(), "isolate team settings without migrating existing data");
        check(EffiplanRules.validSession("opaque_token_123", "000001/SELF", "000001"), "opaque legacy session, not JWT");
        check(!EffiplanRules.validSession("opaque_token_123", "000002/SELF", "000001"), "role must belong to selected user");
        check(!EffiplanRules.validSession("token\r\nInjected: value\r\n", "000001/SELF", "000001"), "prevent header injection");
        check(!EffiplanRules.validSession("", "000001/SELF", "000001"), "missing token");
        check(!EffiplanRules.validSession("t", "000001/", "000001"), "missing role");
        check(!EffiplanRules.validSession("t".repeat(16001), "000001/SELF", "000001"), "bounded credential");
        check(EffiplanRules.time("2026-11-27 09:30").equals(LocalDateTime.of(2026, 11, 27, 9, 30)), "legacy dates normalized");
        check(EffiplanRules.time("2026-11-27T09:30:00").getHour() == 9, "ISO dates retained");
        for (String bad : new String[] { "", "2026-99-27 09:30", "27/11/2026 09:30", "2026-11-27 25:30" }) {
            try { EffiplanRules.time(bad); throw new AssertionError("accepted " + bad); } catch (RuntimeException expected) { }
        }
        System.out.println("PortalRules: all tests passed");
    }
}

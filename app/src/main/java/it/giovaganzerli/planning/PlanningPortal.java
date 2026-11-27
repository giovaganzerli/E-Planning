package it.giovaganzerli.planning;

/** Public identifiers only; operational origins are supplied by build secrets. */
enum PlanningPortal {
    EPLANNING("E-Planning"), EFFIPLAN("Effiplan");
    final String label;
    PlanningPortal(String label) { this.label = label; }
    java.time.DayOfWeek firstDay() { return this == EFFIPLAN ? java.time.DayOfWeek.SUNDAY : java.time.DayOfWeek.MONDAY; }
    java.time.LocalDate weekStart(java.time.LocalDate date) { return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDay())); }
    int weekNumber(java.time.LocalDate date) { return date.get(java.time.temporal.WeekFields.of(firstDay(), 4).weekOfWeekBasedYear()); }
    static PlanningPortal parse(String value) {
        for (PlanningPortal portal : values()) if (portal.name().equals(value)) return portal;
        return EPLANNING;
    }
    String calendarPreferences() { return this == EPLANNING ? "calendar-settings" : "calendar-settings-effiplan"; }
    String calendarPreferences(String accountScope) {
        return calendarPreferences() + (accountScope.isEmpty() ? "" : "-" + accountScope);
    }
    String skillsPrefix() { return this == EPLANNING ? "" : "effiplan:"; }
}

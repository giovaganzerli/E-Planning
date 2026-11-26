package it.giovaganzerli.planning;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Side-effect-free rules: server permission AND a current shift are required. */
final class WorkdayRules {
    static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    enum State { OUTSIDE, READY, DONE, VERIFY }
    static final class Turn {
        final LocalDateTime start, end;
        Turn(LocalDateTime start, LocalDateTime end) {
            if (!end.isAfter(start)) throw new IllegalArgumentException("Intervallo non valido");
            this.start = start; this.end = end;
        }
        String key() { return start + "|" + end; }
    }
    static LocalDateTime time(String value) {
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime(); }
        catch (RuntimeException ignored) { return LocalDateTime.parse(value); }
    }
    static Turn currentTurn(List<Turn> segments, LocalDateTime now) {
        ArrayList<Turn> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparing(t -> t.start));
        Turn merged = null;
        for (Turn next : sorted) {
            if (merged != null && !next.start.isAfter(merged.end)) {
                merged = new Turn(merged.start, next.end.isAfter(merged.end) ? next.end : merged.end);
            } else {
                if (contains(merged, now)) return merged;
                merged = next;
            }
        }
        return contains(merged, now) ? merged : null;
    }
    private static boolean contains(Turn turn, LocalDateTime now) {
        return turn != null && !now.isBefore(turn.start) && now.isBefore(turn.end);
    }
    static boolean permitted(String status) { return "ENTRY".equals(status) || "DEPARTURE".equals(status); }
    static State state(Turn turn, String status, boolean confirmed, boolean pending) {
        if (confirmed) return State.DONE;
        if (pending) return State.VERIFY;
        return turn != null && permitted(status) ? State.READY : State.OUTSIDE;
    }
    static boolean accepted(int httpCode, boolean explicitSuccess) {
        return httpCode >= 200 && httpCode < 300 && explicitSuccess;
    }
    static String recordKey(String action, LocalDateTime time) { return action + "|" + time; }
    static LocalDateTime newRecord(String action, Set<String> before, List<String> records, Turn turn) {
        LocalDateTime result = null;
        for (String record : records) {
            if (!record.startsWith(action + "|") || before.contains(record)) continue;
            LocalDateTime at = time(record.substring(action.length() + 1));
            if (at.isBefore(turn.start.minusMinutes(2)) || at.isAfter(turn.end.plusMinutes(2))) continue;
            if (result == null || at.isAfter(result)) result = at;
        }
        return result;
    }
}

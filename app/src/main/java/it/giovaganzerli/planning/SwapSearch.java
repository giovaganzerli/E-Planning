package it.giovaganzerli.planning;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure, read-only swap rules. No API writes and no Android dependencies. */
final class SwapSearch {
    enum Role { SALES, WORKSHOP, PFL, RECEPTION, MANAGER }
    enum Service { SALES, WORKSHOP, PFL, RECEPTION, BLOCKED, UNKNOWN }

    static final class Segment {
        final String person, label;
        final LocalDateTime start, end;
        final Service service;
        Segment(String person, LocalDateTime start, LocalDateTime end, String label, Service service) {
            this.person = person; this.start = start; this.end = end; this.label = label; this.service = service;
        }
        long minutes() { return Duration.between(start, end).toMinutes(); }
        Segment portion(LocalDateTime from, LocalDateTime to) { return new Segment(person, from, to, label, service); }
    }
    static final class Candidate {
        final String colleague;
        final List<Segment> given, received;
        Candidate(String colleague, List<Segment> given, List<Segment> received) {
            this.colleague = colleague; this.given = new ArrayList<>(given); this.received = new ArrayList<>(received);
        }
    }
    static final class Result {
        String message = "";
        final List<Candidate> candidates = new ArrayList<>();
    }

    static Service service(String translation, String abbreviation) {
        String text = Normalizer.normalize(translation.toLowerCase(Locale.ITALIAN), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]", "");
        String code = abbreviation.toUpperCase(Locale.ITALIAN).replaceAll("[^A-Z0-9]", "");
        if (text.startsWith("perman") || text.startsWith("form") || text.startsWith("respons")) return Service.BLOCKED;
        if (text.startsWith("vend") || text.startsWith("cass") || text.equals("qco") || text.startsWith("cabin") || text.startsWith("scar") || text.equals("ttp")) return Service.SALES;
        if (text.startsWith("work") || text.startsWith("laborator") || text.startsWith("atel")) return Service.WORKSHOP;
        if (text.equals("pfl")) return Service.PFL;
        if (text.startsWith("accogl") || text.equals("acc") || text.startsWith("reception")) return Service.RECEPTION;
        if (code.equals("QCO") || code.equals("TTP")) return Service.SALES;
        if (code.equals("PFL")) return Service.PFL;
        return Service.UNKNOWN;
    }

    static Result find(String user, LocalDateTime freeFrom, LocalDateTime freeTo,
                       List<Segment> planning, Map<String, EnumSet<Role>> skills) {
        return findInternal(user, freeFrom, freeTo, planning, skills, null);
    }
    static Result findSelected(String user, LocalDateTime freeFrom, LocalDateTime freeTo,
                               List<Segment> planning, Map<String, EnumSet<Role>> skills, List<Segment> selected) {
        if (selected.isEmpty()) { Result result = new Result(); result.message = "Seleziona almeno un turno."; return result; }
        LocalDate day = selected.get(0).start.toLocalDate();
        for (Segment s : selected) {
            if (!s.person.equals(user) || !s.start.toLocalDate().equals(day) || !day.equals(freeFrom.toLocalDate())) {
                Result result = new Result(); result.message = "Puoi selezionare solo i tuoi turni di una stessa giornata."; return result;
            }
            boolean exists = false;
            for (Segment original : planning) if (original.person.equals(user) && original.start.equals(s.start) && original.end.equals(s.end) && original.service == s.service && original.label.equals(s.label)) { exists = true; break; }
            if (!exists) { Result result = new Result(); result.message = "I turni selezionati sono cambiati: ripeti la selezione."; return result; }
        }
        return findInternal(user, freeFrom, freeTo, planning, skills, selected);
    }
    private static Result findInternal(String user, LocalDateTime freeFrom, LocalDateTime freeTo,
                                       List<Segment> planning, Map<String, EnumSet<Role>> skills, List<Segment> selected) {
        Result result = new Result();
        if (!freeTo.isAfter(freeFrom)) { result.message = "L'orario finale deve essere successivo a quello iniziale."; return result; }
        LocalDate monday = freeFrom.toLocalDate().minusDays(freeFrom.getDayOfWeek().getValue() - 1);
        LocalDateTime weekStart = monday.atStartOfDay(), weekEnd = monday.plusDays(7).atStartOfDay();
        if (freeFrom.isBefore(weekStart) || freeTo.isAfter(weekEnd)) { result.message = "Scegli un intervallo nella stessa settimana."; return result; }
        EnumSet<Role> ownSkills = skills.get(user);
        if (ownSkills == null) { result.message = "Configura prima le tue abilitazioni."; return result; }
        if (ownSkills.contains(Role.MANAGER)) { result.message = "I responsabili sono esclusi dai cambi turno."; return result; }
        List<Segment> own = new ArrayList<>();
        for (Segment s : selected == null ? planning : selected) if (s.person.equals(user) && overlaps(s.start, s.end, freeFrom, freeTo)) {
            if (s.service == Service.BLOCKED || s.service == Service.UNKNOWN) {
                result.message = "L'intervallo comprende un turno non scambiabile o non riconosciuto: " + s.label + "."; return result;
            }
            own.add(s.portion(later(s.start, freeFrom), earlier(s.end, freeTo)));
        }
        long minutes = total(own);
        if (minutes == 0) { result.message = "Non hai turni da scambiare nell'intervallo selezionato."; return result; }
        List<String> colleagues = new ArrayList<>(skills.keySet()); colleagues.sort(String::compareTo);
        for (String colleague : colleagues) {
            EnumSet<Role> theirSkills = skills.get(colleague);
            if (colleague.equals(user) || theirSkills.contains(Role.MANAGER) || !qualified(theirSkills, own)) continue;
            for (int day = 0; day < 7; day++) {
                LocalDate date = monday.plusDays(day);
                List<Segment> available = new ArrayList<>();
                for (Segment s : planning) if (s.person.equals(colleague) && s.start.toLocalDate().equals(date) && !s.start.isBefore(weekStart) && !s.end.isAfter(weekEnd) && enabled(ownSkills, s.service)) available.add(s);
                available.sort(Comparator.comparing(s -> s.start));
                if (available.isEmpty()) continue;
                LocalDateTime first = available.get(0).start, last = available.get(available.size() - 1).end;
                for (LocalDateTime start = first; !start.plusMinutes(minutes).isAfter(last); start = start.plusMinutes(1)) {
                    LocalDateTime end = start.plusMinutes(minutes);
                    boolean conflictsWithRequest = selected == null && overlaps(start, end, freeFrom, freeTo);
                    if (selected != null) for (Segment s : own) if (overlaps(start, end, s.start, s.end)) conflictsWithRequest = true;
                    if (conflictsWithRequest) continue;
                    List<Segment> returned = new ArrayList<>();
                    for (Segment s : available) if (overlaps(s.start, s.end, start, end)) returned.add(s.portion(later(s.start, start), earlier(s.end, end)));
                    if (!covers(returned, start, end)) continue;
                    if (!validAfterSwap(user, planning, own, returned) || !validAfterSwap(colleague, planning, returned, own)) continue;
                    result.candidates.add(new Candidate(colleague, own, returned));
                    // One representative valid option per colleague and return day.
                    break;
                }
            }
        }
        result.message = result.candidates.isEmpty() ? "Nessun cambio valido con le abilitazioni e i turni disponibili." : "Proposte reciproche a parità di ore, nella stessa settimana.";
        return result;
    }

    private static boolean covers(List<Segment> segments, LocalDateTime start, LocalDateTime end) {
        segments.sort(Comparator.comparing(s -> s.start)); LocalDateTime cursor = start;
        for (Segment s : segments) { if (!s.start.equals(cursor)) return false; cursor = s.end; }
        return cursor.equals(end);
    }
    private static boolean qualified(EnumSet<Role> skills, List<Segment> segments) { for (Segment s : segments) if (!enabled(skills, s.service)) return false; return true; }
    private static boolean enabled(EnumSet<Role> skills, Service service) {
        switch (service) {
            case SALES: return skills.contains(Role.SALES);
            case WORKSHOP: return skills.contains(Role.WORKSHOP);
            case PFL: return skills.contains(Role.PFL);
            case RECEPTION: return skills.contains(Role.RECEPTION);
            default: return false;
        }
    }
    private static boolean validAfterSwap(String person, List<Segment> planning, List<Segment> removed, List<Segment> incoming) {
        List<Segment> remaining = new ArrayList<>();
        for (Segment s : planning) if (s.person.equals(person)) {
            List<Segment> parts = new ArrayList<>(); parts.add(s);
            for (Segment cut : removed) {
                List<Segment> next = new ArrayList<>();
                for (Segment part : parts) {
                    if (!overlaps(part.start, part.end, cut.start, cut.end)) { next.add(part); continue; }
                    if (part.start.isBefore(cut.start)) next.add(part.portion(part.start, cut.start));
                    if (part.end.isAfter(cut.end)) next.add(part.portion(cut.end, part.end));
                }
                parts = next;
            }
            remaining.addAll(parts);
        }
        for (Segment add : incoming) for (Segment s : remaining) if (overlaps(add.start, add.end, s.start, s.end)) return false;
        Set<LocalDate> affected = new HashSet<>();
        for (Segment s : removed) addDates(affected, s); for (Segment s : incoming) addDates(affected, s);
        remaining.addAll(incoming);
        for (LocalDate date : affected) {
            long minutes = 0;
            for (Segment s : remaining) if (overlaps(s.start, s.end, date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
                if (s.service == Service.UNKNOWN) return false;
                minutes += Duration.between(later(s.start, date.atStartOfDay()), earlier(s.end, date.plusDays(1).atStartOfDay())).toMinutes();
            }
            if (minutes > 0 && minutes < 240) return false;
        }
        return true;
    }
    private static void addDates(Set<LocalDate> dates, Segment segment) { for (LocalDate d = segment.start.toLocalDate(); d.atStartOfDay().isBefore(segment.end); d = d.plusDays(1)) dates.add(d); }
    private static long total(List<Segment> segments) { long total = 0; for (Segment s : segments) total += s.minutes(); return total; }
    private static boolean overlaps(LocalDateTime a, LocalDateTime b, LocalDateTime c, LocalDateTime d) { return a.isBefore(d) && c.isBefore(b); }
    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }
    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
}

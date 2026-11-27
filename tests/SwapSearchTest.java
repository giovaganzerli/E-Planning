package it.giovaganzerli.planning;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwapSearchTest {
    static LocalDateTime time(int day, int hour) { return LocalDateTime.of(2026, 11, day, hour, 0); }
    static SwapSearch.Segment shift(String who, int day, int from, int to, SwapSearch.Service service) { return new SwapSearch.Segment(who, time(day, from), time(day, to), service.name(), service); }
    static Map<String, EnumSet<SwapSearch.Role>> skills() { Map<String, EnumSet<SwapSearch.Role>> map = new HashMap<>(); map.put("me", EnumSet.of(SwapSearch.Role.SALES)); map.put("other", EnumSet.of(SwapSearch.Role.SALES)); return map; }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static SwapSearch.Result find(List<SwapSearch.Segment> shifts, int from, int to, Map<String, EnumSet<SwapSearch.Role>> roles) { return SwapSearch.find("me", time(24, from), time(24, to), shifts, roles); }
    public static void main(String[] args) {
        List<SwapSearch.Segment> base = Arrays.asList(shift("me", 24, 10, 16, SwapSearch.Service.SALES), shift("other", 26, 10, 16, SwapSearch.Service.SALES));
        check(find(base, 10, 16, skills()).candidates.size() == 1, "full reciprocal swap");
        check(find(base, 10, 12, skills()).candidates.isEmpty(), "two hour new work day is invalid");
        Map<String, EnumSet<SwapSearch.Role>> manager = skills(); manager.get("other").add(SwapSearch.Role.MANAGER);
        check(find(base, 10, 16, manager).candidates.isEmpty(), "manager excluded");
        manager = skills(); manager.get("me").add(SwapSearch.Role.MANAGER);
        check(find(base, 10, 16, manager).candidates.isEmpty(), "logged in manager excluded");
        Map<String, EnumSet<SwapSearch.Role>> mismatch = skills(); mismatch.put("other", EnumSet.of(SwapSearch.Role.WORKSHOP));
        check(find(base, 10, 16, mismatch).candidates.isEmpty(), "colleague must be qualified");
        List<SwapSearch.Segment> blocked = Arrays.asList(shift("me", 24, 10, 16, SwapSearch.Service.BLOCKED), base.get(1));
        check(find(blocked, 10, 16, skills()).candidates.isEmpty(), "blocked palettes cannot be given");
        blocked = Arrays.asList(base.get(0), shift("other", 26, 10, 16, SwapSearch.Service.BLOCKED));
        check(find(blocked, 10, 16, skills()).candidates.isEmpty(), "blocked palettes cannot be received");
        List<SwapSearch.Segment> overlap = new ArrayList<>(base); overlap.add(shift("other", 24, 12, 18, SwapSearch.Service.SALES));
        check(find(overlap, 10, 16, skills()).candidates.isEmpty(), "no overlap with colleague existing work");
        List<SwapSearch.Segment> outside = Arrays.asList(base.get(0), shift("other", 30, 10, 16, SwapSearch.Service.SALES));
        check(find(outside, 10, 16, skills()).candidates.isEmpty(), "same ISO week only");
        List<SwapSearch.Segment> partial = Arrays.asList(base.get(0), shift("me", 26, 8, 12, SwapSearch.Service.SALES), shift("other", 24, 8, 10, SwapSearch.Service.SALES), shift("other", 26, 10, 16, SwapSearch.Service.SALES));
        check(!find(partial, 10, 12, skills()).candidates.isEmpty(), "partial accepted when daily totals stay at least four hours");
        List<SwapSearch.Segment> shortRemaining = Arrays.asList(base.get(0), shift("other", 26, 10, 16, SwapSearch.Service.SALES));
        check(find(shortRemaining, 10, 14, skills()).candidates.isEmpty(), "remaining two hour work day invalid");
        List<SwapSearch.Segment> sameDay = Arrays.asList(shift("me", 24, 8, 12, SwapSearch.Service.SALES), shift("other", 24, 14, 18, SwapSearch.Service.SALES));
        check(find(sameDay, 8, 12, skills()).candidates.size() == 1, "same day reciprocal swap valid");
        check(SwapSearch.service("Form.", "F") == SwapSearch.Service.BLOCKED, "formation excluded");
        check(SwapSearch.service("Permanenza", "P") == SwapSearch.Service.BLOCKED, "permanence excluded");
        for (String name : Arrays.asList("Vend.", "Cassa", "QCO", "Cabine", "Scar.", "TTP")) check(SwapSearch.service(name, "") == SwapSearch.Service.SALES, "sales includes " + name);
        check(SwapSearch.service("Sconosciuto", "XYZ") == SwapSearch.Service.UNKNOWN, "unknown service fails closed");
        List<SwapSearch.Segment> mixed = Arrays.asList(shift("me", 24, 8, 10, SwapSearch.Service.SALES), shift("me", 24, 10, 12, SwapSearch.Service.WORKSHOP), shift("other", 26, 8, 10, SwapSearch.Service.SALES), shift("other", 26, 10, 12, SwapSearch.Service.WORKSHOP));
        check(find(mixed, 8, 12, skills()).candidates.isEmpty(), "all palettes require matching qualifications");
        Map<String, EnumSet<SwapSearch.Role>> multi = skills(); multi.get("me").add(SwapSearch.Role.WORKSHOP); multi.get("other").add(SwapSearch.Role.WORKSHOP);
        SwapSearch.Result multiResult = find(mixed, 8, 12, multi);
        check(multiResult.candidates.size() == 1 && multiResult.candidates.get(0).received.size() == 2, "adjacent differently qualified portions compose one return interval");
        List<SwapSearch.Segment> returnOverlap = new ArrayList<>(base); returnOverlap.add(shift("me", 26, 8, 18, SwapSearch.Service.SALES));
        check(find(returnOverlap, 10, 16, skills()).candidates.isEmpty(), "no overlap with logged in user existing work");
        Map<String, EnumSet<SwapSearch.Role>> noOwn = skills(); noOwn.remove("me");
        check(find(base, 10, 16, noOwn).candidates.isEmpty(), "own configuration is mandatory");
        check(find(base, 16, 10, skills()).candidates.isEmpty(), "invalid interval rejected");
        SwapSearch.Result selectedSameDay = SwapSearch.findSelected("me", time(24, 8), time(24, 12), sameDay, skills(), Arrays.asList(sameDay.get(0)));
        check(selectedSameDay.candidates.size() == 1 && selectedSameDay.candidates.get(0).received.get(0).start.equals(time(24, 14)), "selected morning can be exchanged for same-day afternoon");
        SwapSearch.Result partialSameDay = SwapSearch.findSelected("me", time(24, 10), time(24, 12), sameDay, skills(), Arrays.asList(sameDay.get(0)));
        check(partialSameDay.candidates.size() == 1 && partialSameDay.candidates.get(0).given.get(0).minutes() == 120, "partial same-day swap retains four daily hours for both");
        List<SwapSearch.Segment> twoOwnDays = new ArrayList<>(base); twoOwnDays.add(shift("me", 25, 10, 16, SwapSearch.Service.SALES));
        check(SwapSearch.findSelected("me", time(24, 10), time(24, 16), twoOwnDays, skills(), Arrays.asList(twoOwnDays.get(0), twoOwnDays.get(2))).candidates.isEmpty(), "selection on multiple days rejected");
        check(SwapSearch.findSelected("me", time(24, 10), time(24, 16), base, skills(), new ArrayList<>()).candidates.isEmpty(), "empty selection rejected");
        check(SwapSearch.findSelected("me", time(24, 10), time(24, 16), base, skills(), Arrays.asList(base.get(1))).candidates.isEmpty(), "cannot select a colleague's shift");
        List<SwapSearch.Segment> separated = Arrays.asList(shift("me", 24, 8, 10, SwapSearch.Service.SALES), shift("me", 24, 10, 12, SwapSearch.Service.SALES), shift("me", 24, 12, 14, SwapSearch.Service.SALES), shift("me", 26, 8, 10, SwapSearch.Service.SALES), shift("other", 26, 10, 16, SwapSearch.Service.SALES), shift("other", 24, 16, 20, SwapSearch.Service.SALES));
        SwapSearch.Result separatedResult = SwapSearch.findSelected("me", time(24, 8), time(24, 14), separated, skills(), Arrays.asList(separated.get(0), separated.get(2)));
        check(!separatedResult.candidates.isEmpty() && separatedResult.candidates.get(0).given.size() == 2 && separatedResult.candidates.get(0).given.get(0).minutes() + separatedResult.candidates.get(0).given.get(1).minutes() == 240, "unselected intervening shift is never silently exchanged");
        List<SwapSearch.Segment> sundayToSaturday = Arrays.asList(shift("me", 22, 10, 16, SwapSearch.Service.SALES), shift("other", 28, 10, 16, SwapSearch.Service.SALES));
        check(SwapSearch.findSelected("me", time(22, 10), time(22, 16), sundayToSaturday, skills(), Arrays.asList(sundayToSaturday.get(0)), DayOfWeek.SUNDAY).candidates.size() == 1, "Effiplan can exchange Sunday for Saturday in the same week");
        check(SwapSearch.findSelected("me", time(22, 10), time(22, 16), sundayToSaturday, skills(), Arrays.asList(sundayToSaturday.get(0))).candidates.isEmpty(), "E-Planning Sunday is not in the following Monday-Sunday week");
        List<SwapSearch.Segment> saturdayToSunday = Arrays.asList(shift("me", 28, 10, 16, SwapSearch.Service.SALES), shift("other", 29, 10, 16, SwapSearch.Service.SALES));
        check(SwapSearch.findSelected("me", time(28, 10), time(28, 16), saturdayToSunday, skills(), Arrays.asList(saturdayToSunday.get(0)), DayOfWeek.SUNDAY).candidates.isEmpty(), "Effiplan cannot exchange Saturday for next Sunday");
        check(SwapSearch.findSelected("me", time(28, 10), time(28, 16), saturdayToSunday, skills(), Arrays.asList(saturdayToSunday.get(0))).candidates.size() == 1, "E-Planning still allows Saturday-Sunday in its same week");
        List<SwapSearch.Segment> sundayToPreviousSaturday = Arrays.asList(shift("me", 29, 10, 16, SwapSearch.Service.SALES), shift("other", 28, 10, 16, SwapSearch.Service.SALES));
        check(SwapSearch.find("me", time(29, 10), time(29, 16), sundayToPreviousSaturday, skills(), DayOfWeek.SUNDAY).candidates.isEmpty(), "Sunday cannot reach back into Effiplan's previous week");
        List<SwapSearch.Segment> yearEnd = Arrays.asList(
            new SwapSearch.Segment("me", LocalDateTime.of(2027, 1, 2, 10, 0), LocalDateTime.of(2027, 1, 2, 16, 0), "Vendita", SwapSearch.Service.SALES),
            new SwapSearch.Segment("other", LocalDateTime.of(2027, 1, 3, 10, 0), LocalDateTime.of(2027, 1, 3, 16, 0), "Vendita", SwapSearch.Service.SALES));
        check(SwapSearch.find("me", yearEnd.get(0).start, yearEnd.get(0).end, yearEnd, skills(), DayOfWeek.SUNDAY).candidates.isEmpty(), "Effiplan week boundary also enforced across New Year");
        check(SwapSearch.find("me", yearEnd.get(0).start, yearEnd.get(0).end, yearEnd, skills()).candidates.size() == 1, "ISO week boundary unchanged across New Year");
        check(SwapSearch.find("me", time(28, 22), time(29, 2), saturdayToSunday, skills(), DayOfWeek.SUNDAY).message.contains("stessa settimana"), "interval crossing Sunday midnight is rejected rather than silently crossing weeks");
        System.out.println("SwapSearch: all rule tests passed");
    }
}

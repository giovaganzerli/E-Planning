package it.giovaganzerli.planning;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.provider.CalendarContract;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.CheckBox;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PORTAL = BuildConfig.PORTAL_URL;
    private static final String API = BuildConfig.API_BASE_URL + "/lite/planning/api/v2/";
    private static final String WEEKLY_API = API + "planning/collaboratorWeek";
    private static final String MONTHLY_API = API + "planning/monthlyPlanningByCollaborator";
    private static final String TEAM_API = API + "planning/collaboratorWeekTeam";
    private static final String TEAM_MEMBERS_API = API + "PlanningManager/getCollaboratorsByTeamWithPlanning";
    private static final String PROFILE_API = BuildConfig.API_BASE_URL + "/api/v2/actor/profile";
    private static final int NAVY = Color.rgb(36, 59, 116), INK = Color.rgb(25, 42, 74), MUTED = Color.rgb(100, 116, 139);
    private static final int CALENDAR_PERMISSION_REQUEST = 41;
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final String CALENDAR_SETTINGS = "calendar-settings";
    private static final String SELECTED_CALENDAR_ID = "selected-calendar-id";
    private static final String SELECTED_CALENDAR_NAME = "selected-calendar-name";
    private static final String EVENT_MARKER = "E-PLANNING:";

    private enum Tab { WEEK, MONTH, TEAM, SEARCH, SETTINGS }
    private enum TeamGrouping { DAY, COLLABORATOR, DAILY }
    private WebView portal;
    private LinearLayout planningContainer, weekList;
    private View header, bottomNavigation, punchPreview;
    private TextView status, weekLabel, weekSummary, subtitle;
    private Button weekButton, monthButton, teamButton, searchButton, previousButton, nextButton, todayButton;
    private ImageButton appMenuButton, refreshButton, calendarSyncButton;
    private TextView calendarSyncIndicator;
    private boolean weekReady, calendarSyncing, planningReadable;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<Shift> shifts = new ArrayList<>();
    private final Map<String, String> collaborators = new HashMap<>();
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN);
    private final DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN);
    private boolean authenticated, clearingSession;
    private int authGeneration;
    private final Handler authHandler = new Handler(Looper.getMainLooper());
    private final Runnable sessionPoll = new Runnable() {
        @Override public void run() {
            if (destroyed || authenticated || clearingSession) return;
            checkForSession(); authHandler.postDelayed(this, 1000);
        }
    };
    private String token = "";
    private String collaboratorUuid = "", teamUuid = "";
    private String collaboratorName = "";
    private LocalDate searchDay = LocalDate.now();
    private final Set<String> searchSelectedShifts = new HashSet<>();
    private int requestGeneration;
    private boolean destroyed;
    private AppUpdater updater;
    private WorkdayController workday;
    private Tab currentTab = Tab.WEEK;
    private LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
    private LocalDate currentWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private LocalDate currentTeamWeek = currentWeek;
    private int teamWeekCount = 1;
    private TeamGrouping teamGrouping = TeamGrouping.DAY;
    private boolean teamDayMode;
    private LocalDate currentTeamDay = LocalDate.now();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        Window window = getWindow(); window.setStatusBarColor(Color.rgb(23, 37, 84));
        header = findViewById(R.id.header); bottomNavigation = findViewById(R.id.bottomNavigation);
        portal = findViewById(R.id.portal); planningContainer = findViewById(R.id.planningContainer); weekList = findViewById(R.id.weekList);
        status = findViewById(R.id.status); weekLabel = findViewById(R.id.weekLabel); weekSummary = findViewById(R.id.weekSummary); subtitle = findViewById(R.id.subtitle);
        refreshButton = findViewById(R.id.refresh); appMenuButton = findViewById(R.id.appMenu); previousButton = findViewById(R.id.previousPeriod); nextButton = findViewById(R.id.nextPeriod); todayButton = findViewById(R.id.today);
        calendarSyncButton = findViewById(R.id.calendarSync); calendarSyncIndicator = findViewById(R.id.calendarSyncIndicator);
        calendarSyncButton.setOnClickListener(v -> confirmCalendarSync());
        punchPreview = findViewById(R.id.punchPreview);
        workday = new WorkdayController(this);
        weekButton = findViewById(R.id.openWeek); monthButton = findViewById(R.id.openMonth); teamButton = findViewById(R.id.openTeam); searchButton = findViewById(R.id.openSearch);
        int side = dp(24), top = dp(24);
        header.setOnApplyWindowInsetsListener((view, insets) -> { view.setPadding(side, top + insets.getSystemWindowInsetTop(), side, dp(24)); return insets; });
        refreshButton.setOnClickListener(v -> loadCurrentTab()); appMenuButton.setOnClickListener(v -> showAppMenu()); previousButton.setOnClickListener(v -> movePeriod(-1)); nextButton.setOnClickListener(v -> movePeriod(1)); todayButton.setOnClickListener(v -> goToday());
        weekButton.setOnClickListener(v -> selectTab(Tab.WEEK)); monthButton.setOnClickListener(v -> selectTab(Tab.MONTH)); teamButton.setOnClickListener(v -> selectTab(Tab.TEAM));
        searchButton.setOnClickListener(v -> selectTab(Tab.SEARCH));
        renderEmpty("I turni appariranno qui", "Attendi l'accesso al portale ePlanning.");
        configurePortal(); showLogin();
        updater = new AppUpdater(this); updater.start();
    }
    @Override protected void onResume() { super.onResume(); if (updater != null) updater.resume(); if (workday != null) workday.resume(); }
    @Override protected void onPause() { if (workday != null) workday.pause(); super.onPause(); }

    private void configurePortal() {
        portal.getSettings().setJavaScriptEnabled(true); portal.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(portal, true);
        portal.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { super.onPageFinished(view, url); if (!authenticated) checkForSession(); }
        });
        portal.loadUrl(PORTAL);
    }

    private void checkForSession() {
        if (destroyed || authenticated || clearingSession) return;
        final int generation = authGeneration;
        String script = "(function(){if(location.origin!=='" + PORTAL + "')return '';for(let storage of [localStorage,sessionStorage]){let value=storage.getItem('auth.token');if(value){try{return JSON.parse(value)||''}catch(e){return value}}}return ''})()";
        portal.evaluateJavascript(script, result -> {
            if (destroyed || authenticated || clearingSession || generation != authGeneration) return;
            String found = decodeJs(result);
            if (!found.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) return;
            token = found; authenticated = true; showApp(); selectTab(Tab.WEEK);
        });
    }

    private void showLogin() {
        header.setVisibility(View.GONE); planningContainer.setVisibility(View.GONE); bottomNavigation.setVisibility(View.GONE); portal.setVisibility(View.VISIBLE);
        authHandler.removeCallbacks(sessionPoll);
        if (!destroyed && !clearingSession && !authenticated) authHandler.post(sessionPoll);
    }

    private void showApp() {
        authHandler.removeCallbacks(sessionPoll);
        header.setVisibility(View.VISIBLE); planningContainer.setVisibility(View.VISIBLE); bottomNavigation.setVisibility(View.VISIBLE); portal.setVisibility(View.GONE);
        subtitle.setText("Il tuo planning, a colpo d’occhio");
    }
    private void showAppMenu() {
        LinearLayout menu = new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL); menu.setPadding(dp(6), dp(6), dp(6), dp(6)); menu.setBackgroundResource(R.drawable.bg_popup_menu);
        PopupWindow popup = new PopupWindow(menu, dp(244), -2, true); popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); popup.setOutsideTouchable(true); popup.setElevation(dp(10));
        menu.addView(menuAction("Impostazioni", R.drawable.ic_settings, () -> selectTab(Tab.SETTINGS)));
        menu.addView(menuAction("Aggiornamenti…", R.drawable.ic_update, () -> updater.open()));
        View divider = new View(this); divider.setBackgroundColor(Color.rgb(203, 213, 225)); menu.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        menu.addView(menuAction("Logout", R.drawable.ic_logout, this::clearSession));
        popup.showAsDropDown(appMenuButton, -dp(202), dp(4));
        for (int i = 0; i < menu.getChildCount(); i++) {
            View item = menu.getChildAt(i);
            if (item instanceof LinearLayout) ((LinearLayout) item).setOnClickListener(v -> { popup.dismiss(); ((Runnable) v.getTag()).run(); });
        }
    }
    private View menuAction(String label, int icon, Runnable action) {
        LinearLayout item = new LinearLayout(this); item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(dp(12), dp(10), dp(12), dp(10)); item.setTag(action);
        ImageView image = new ImageView(this); image.setImageResource(icon); if (icon == R.drawable.ic_update) image.setColorFilter(NAVY); item.addView(image, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = text(label, 15, INK, false); LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.leftMargin = dp(10); item.addView(title, titleParams);
        item.setMinimumHeight(dp(50)); return item;
    }

    private void clearSession() {
        if (destroyed || clearingSession) return;
        clearingSession = true; final int generation = ++authGeneration; authHandler.removeCallbacksAndMessages(null);
        requestGeneration++; authenticated = false; token = ""; collaboratorUuid = ""; teamUuid = ""; collaboratorName = ""; shifts.clear(); collaborators.clear(); weekList.removeAllViews(); searchDay = LocalDate.now(); searchSelectedShifts.clear();
        workday.session("", "");
        currentMonth = LocalDate.now().withDayOfMonth(1); currentWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); currentTeamWeek = currentWeek; currentTeamDay = LocalDate.now();
        showLogin(); portal.stopLoading(); portal.clearHistory(); portal.clearFormData(); portal.clearCache(true);
        // Navigation can cancel evaluateJavascript callbacks: always allow cleanup to finish.
        Runnable finish = new Runnable() {
            private boolean started;
            @Override public void run() {
                if (started || destroyed || generation != authGeneration) return;
                started = true; portal.loadUrl("about:blank");
                WebStorage.getInstance().deleteAllData();
                CookieManager cookies = CookieManager.getInstance();
                cookies.removeAllCookies(ignored -> {
                    if (destroyed || generation != authGeneration) return;
                    cookies.flush(); clearingSession = false; portal.loadUrl(PORTAL); showLogin();
                });
            }
        };
        portal.evaluateJavascript("(function(){try{localStorage.clear();sessionStorage.clear()}catch(e){}return true})()", ignored -> finish.run());
        authHandler.postDelayed(finish, 1500);
    }

    private void selectTab(Tab tab) { currentTab = tab; updateNavigation(); loadCurrentTab(); }
    private void movePeriod(int direction) {
        if (currentTab == Tab.SEARCH) { searchDay = searchDay.plusWeeks(direction); searchSelectedShifts.clear(); loadCurrentTab(); return; }
        if (currentTab == Tab.MONTH) currentMonth = currentMonth.plusMonths(direction);
        else if (currentTab == Tab.TEAM) { if (teamDayMode) { currentTeamDay = currentTeamDay.plusDays(direction); currentTeamWeek = currentTeamDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); } else currentTeamWeek = currentTeamWeek.plusWeeks(direction); }
        else currentWeek = currentWeek.plusWeeks(direction);
        loadCurrentTab();
    }
    private void goToday() { LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); if (currentTab == Tab.SEARCH) { searchDay = LocalDate.now(); searchSelectedShifts.clear(); } else if (currentTab == Tab.MONTH) currentMonth = LocalDate.now().withDayOfMonth(1); else if (currentTab == Tab.TEAM) { currentTeamWeek = monday; currentTeamDay = LocalDate.now(); } else currentWeek = monday; loadCurrentTab(); }
    private void updateNavigation() {
        punchPreview.setVisibility(currentTab == Tab.WEEK ? View.VISIBLE : View.GONE);
        workday.visible(currentTab == Tab.WEEK);
        Button[] buttons = { weekButton, monthButton, teamButton, searchButton };
        Tab[] tabs = { Tab.WEEK, Tab.MONTH, Tab.TEAM, Tab.SEARCH };
        for (int i = 0; i < buttons.length; i++) {
            boolean selected = tabs[i] == currentTab;
            buttons[i].setBackgroundResource(selected ? R.drawable.bg_nav_selected : R.drawable.bg_nav);
            buttons[i].setTextColor(selected ? Color.rgb(30, 64, 175) : MUTED);
        }
    }

    private void loadCurrentTab() {
        if (!authenticated || token.isEmpty()) { showLogin(); return; }
        if (currentTab == Tab.WEEK) workday.refresh();
        weekReady = false; planningReadable = true; calendarSyncIndicator.setVisibility(View.GONE);
        calendarSyncButton.setVisibility(currentTab == Tab.WEEK ? View.VISIBLE : View.GONE); calendarSyncButton.setEnabled(false);
        requestGeneration++; refreshButton.setEnabled(false); shifts.clear(); collaborators.clear(); weekList.removeAllViews(); status.setVisibility(View.GONE);
        if (collaboratorUuid.isEmpty()) { loadProfile(); return; }
        switch (currentTab) {
            case MONTH: loadMonth(); break;
            case TEAM: loadTeam(); break;
            case SEARCH: loadSwapPlanning(); break;
            case SETTINGS: loadSettings(); break;
            default: loadWeek(); break;
        }
    }
    private void loadProfile() {
        weekLabel.setText("Caricamento profilo…"); weekSummary.setText("");
        request(PROFILE_API, raw -> {
            try {
                JSONObject profile = new JSONObject(raw); JSONObject person = profile.getJSONObject("collaborator");
                String uuid = person.getString("uuid");
                if (!validUuid(uuid)) throw new Exception("Identificativo collaboratore mancante");
                JSONObject condition = person.optJSONObject("latestSuccessfulLaborCondition");
                if (condition == null) condition = person.optJSONObject("activeLaborCondition");
                JSONObject team = condition == null ? null : condition.optJSONObject("team");
                String resolvedTeam = team == null ? "" : team.optString("uuid", "");
                collaboratorUuid = uuid; teamUuid = validUuid(resolvedTeam) ? resolvedTeam : "";
                workday.session(token, collaboratorUuid);
                collaboratorName = (person.optString("name", "") + " " + person.optString("firstLastName", "")).trim();
                loadCurrentTab();
            } catch (Exception e) { showLoadError("Impossibile identificare il collaboratore dal profilo ePlanning. Premi aggiorna per riprovare."); }
        });
    }
    private boolean validUuid(String uuid) { return uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"); }
    private void showLoadError(String message) { refreshButton.setEnabled(true); status.setText(message); status.setTextColor(Color.WHITE); status.setVisibility(View.VISIBLE); }

    private void loadWeek() {
        status.setText("Aggiornamento planning settimanale…");
        LocalDate thisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (currentWeek.equals(thisWeek)) request(WEEKLY_API, raw -> { parseShifts(raw); renderWeek(); complete("Planning settimanale aggiornato"); });
        else loadWeekFromMonths(false);
    }
    private void loadWeekFromMonths(boolean secondMonth) {
        LocalDate month = secondMonth ? currentWeek.plusDays(6).withDayOfMonth(1) : currentWeek.withDayOfMonth(1);
        String url = MONTHLY_API + "?filter[month]=" + month + "&filter[collaboratorUuid]=" + collaboratorUuid;
        request(url, raw -> {
            if (secondMonth) appendShifts(raw); else parseShifts(raw);
            if (!secondMonth && !currentWeek.getMonth().equals(currentWeek.plusDays(6).getMonth())) loadWeekFromMonths(true);
            else { renderWeek(); complete("Planning settimanale aggiornato"); }
        });
    }

    private void loadMonth() {
        status.setText("Aggiornamento planning mensile…");
        String url = MONTHLY_API + "?filter[month]=" + currentMonth + "&filter[collaboratorUuid]=" + collaboratorUuid;
        request(url, raw -> { parseShifts(raw); renderMonth(); complete("Planning mensile aggiornato"); });
    }

    private void loadTeam() {
        if (teamUuid.isEmpty()) { showLoadError("Nessuna squadra associata al tuo profilo ePlanning."); return; }
        status.setText("Caricamento collaboratori della squadra…");
        String date = currentTeamWeek.toString();
        String membersUrl = TEAM_MEMBERS_API + "?filter[teamUuid]=" + teamUuid + "&filter[startDateInterval]=" + date + "&filter[withPolyvalences]=true";
        request(membersUrl, membersRaw -> {
            parseCollaborators(membersRaw); shifts.clear(); status.setText("Aggiornamento planning squadra…"); loadTeamWeeks(0);
        });
    }
    private void loadTeamWeeks(int index) {
        if (index >= teamWeekCount) { shifts.sort(Comparator.comparing(s -> s.from)); renderTeam(currentTeamWeek); complete("Planning squadra aggiornato"); return; }
        LocalDate start = currentTeamWeek.plusWeeks(index);
        request(TEAM_API + "?filter[startDateInterval]=" + start, raw -> { appendShifts(raw); loadTeamWeeks(index + 1); });
    }

    private void loadSwapPlanning() {
        weekLabel.setText("Cerca cambio · W" + weekNumber(searchDay)); weekSummary.setText("Caricamento squadra…");
        if (teamUuid.isEmpty()) { showLoadError("Nessuna squadra associata al tuo profilo."); return; }
        LocalDate monday = searchDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        request(TEAM_MEMBERS_API + "?filter[teamUuid]=" + teamUuid + "&filter[startDateInterval]=" + monday + "&filter[withPolyvalences]=true", raw -> {
            try {
                collaborators.clear(); collectCollaborators(new JSONTokener(raw).nextValue());
                if (collaborators.isEmpty()) throw new Exception();
                if (!collaborators.containsKey(collaboratorUuid)) collaborators.put(collaboratorUuid, collaboratorName.isEmpty() ? "Tu" : collaboratorName);
                request(TEAM_API + "?filter[startDateInterval]=" + monday, planning -> {
                    try {
                        shifts.clear(); collectShifts(new JSONTokener(planning).nextValue()); deduplicateShifts();
                        shifts.removeIf(s -> collaboratorUuid.equals(s.collaboratorId));
                        loadSwapOwnPlanning(monday, false);
                    } catch (Exception e) { showLoadError("Planning squadra non leggibile: ricerca non disponibile."); }
                });
            } catch (Exception e) { showLoadError("Impossibile leggere i membri della squadra."); }
        });
    }
    private void loadSettings() {
        weekLabel.setText("Impostazioni"); weekSummary.setText("Caricamento configurazione…");
        if (teamUuid.isEmpty()) { renderSettings(); return; }
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String membersUrl = TEAM_MEMBERS_API + "?filter[teamUuid]=" + teamUuid + "&filter[startDateInterval]=" + monday + "&filter[withPolyvalences]=true";
        request(membersUrl, raw -> {
            parseCollaborators(raw);
            if (!collaboratorUuid.isEmpty() && !collaborators.containsKey(collaboratorUuid)) collaborators.put(collaboratorUuid, collaboratorName.isEmpty() ? "Tu" : collaboratorName);
            renderSettings(); complete("Impostazioni aggiornate");
        });
    }
    private void loadSwapOwnPlanning(LocalDate monday, boolean secondMonth) {
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean current = monday.equals(thisMonday);
        LocalDate month = (secondMonth ? monday.plusDays(6) : monday).withDayOfMonth(1);
        String endpoint = current ? WEEKLY_API : MONTHLY_API + "?filter[month]=" + month + "&filter[collaboratorUuid]=" + collaboratorUuid;
        request(endpoint, raw -> {
            try {
                int offset = shifts.size(); collectShifts(new JSONTokener(raw).nextValue());
                for (int i = offset; i < shifts.size(); i++) { Shift s = shifts.get(i); s.collaboratorId = collaboratorUuid; s.collaborator = collaborators.get(collaboratorUuid); }
                if (!current && !secondMonth && !monday.withDayOfMonth(1).equals(monday.plusDays(6).withDayOfMonth(1))) { loadSwapOwnPlanning(monday, true); return; }
                shifts.removeIf(s -> s.date.isBefore(monday) || s.date.isAfter(monday.plusDays(6))); deduplicateShifts(); shifts.sort(Comparator.comparing(s -> s.from));
                complete("Planning caricato"); renderSwapForm();
            } catch (Exception e) { showLoadError("I tuoi turni non sono leggibili: ricerca non disponibile."); }
        });
    }
    private String skillsKey() { return "skills:" + collaboratorUuid + ":" + teamUuid; }
    private JSONObject storedSkills() { try { return new JSONObject(getSharedPreferences("swap-settings", MODE_PRIVATE).getString(skillsKey(), "{}")); } catch (Exception e) { return new JSONObject(); } }
    private Map<String, EnumSet<SwapSearch.Role>> configuredSkills() {
        JSONObject stored = storedSkills(); Map<String, EnumSet<SwapSearch.Role>> result = new HashMap<>();
        for (String uuid : collaborators.keySet()) {
            int mask = stored.optInt(uuid, -1); if (mask < 0 || mask > 31) continue;
            EnumSet<SwapSearch.Role> roles = EnumSet.noneOf(SwapSearch.Role.class);
            for (SwapSearch.Role role : SwapSearch.Role.values()) if ((mask & (1 << role.ordinal())) != 0) roles.add(role);
            result.put(uuid, roles);
        }
        return result;
    }
    private boolean swapConfigured() { return configuredSkills().size() == collaborators.size() && collaborators.containsKey(collaboratorUuid); }
    private final String[] roleNames = { "Vendita", "Workshop", "PFL", "Accoglienza", "Responsabile" };
    private String skillsSummary(String uuid) {
        EnumSet<SwapSearch.Role> roles = configuredSkills().get(uuid); if (roles == null) return "Da configurare";
        if (roles.isEmpty()) return "Nessuna abilitazione (escluso)";
        ArrayList<String> names = new ArrayList<>(); for (SwapSearch.Role role : roles) names.add(roleNames[role.ordinal()]); return android.text.TextUtils.join(", ", names);
    }
    private void configureSwapTeam() {
        if (currentTab != Tab.SEARCH && currentTab != Tab.SETTINGS) return;
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView help = text("Scorri la tabella orizzontalmente. Vendita include Cassa, QCO, Cabine, Scarico e TTP. Responsabile esclude le altre abilitazioni e i cambi. Nessuna spunta = escluso dalla ricerca.", 12, MUTED, false); content.addView(help);
        HorizontalScrollView horizontal = new HorizontalScrollView(this); LinearLayout table = new LinearLayout(this); table.setOrientation(LinearLayout.VERTICAL); horizontal.addView(table);
        ScrollView vertical = new ScrollView(this); vertical.addView(horizontal); content.addView(vertical, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * .5f)));
        LinearLayout headings = new LinearLayout(this); headings.setBackgroundColor(Color.rgb(232,240,255));
        TextView personHeading = text("Collaboratore", 12, NAVY, true); personHeading.setPadding(dp(8), dp(8), dp(8), dp(8)); headings.addView(personHeading, new LinearLayout.LayoutParams(dp(150), dp(56)));
        for (String role : roleNames) { TextView label = text(role, 11, NAVY, true); label.setGravity(Gravity.CENTER); headings.addView(label, new LinearLayout.LayoutParams(dp(94), dp(56))); }
        table.addView(headings);
        ArrayList<String> ids = new ArrayList<>(collaborators.keySet()); ids.sort((a, b) -> a.equals(collaboratorUuid) ? (b.equals(collaboratorUuid) ? 0 : -1) : b.equals(collaboratorUuid) ? 1 : collaborators.get(a).compareToIgnoreCase(collaborators.get(b)));
        Map<String, CheckBox[]> cells = new HashMap<>(); JSONObject stored = storedSkills();
        final String owner = collaboratorUuid, team = teamUuid;
        for (String id : ids) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setBackgroundColor(id.equals(collaboratorUuid) ? Color.rgb(239,246,255) : Color.WHITE);
            TextView name = text(collaborators.get(id) + (id.equals(collaboratorUuid) ? " · TU" : ""), 12, INK, true); name.setPadding(dp(8), dp(4), dp(8), dp(4)); row.addView(name, new LinearLayout.LayoutParams(dp(150), dp(60)));
            int mask = stored.optInt(id, 0); boolean manager = (mask & 16) != 0; CheckBox[] boxes = new CheckBox[roleNames.length];
            for (int i = 0; i < boxes.length; i++) {
                CheckBox box = new CheckBox(this); boxes[i] = box; box.setContentDescription(collaborators.get(id) + ": " + roleNames[i]); box.setChecked(i == 4 ? manager : !manager && (mask & (1 << i)) != 0); box.setEnabled(i == 4 || !manager);
                android.widget.FrameLayout cell = new android.widget.FrameLayout(this); cell.addView(box, new android.widget.FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)); row.addView(cell, new LinearLayout.LayoutParams(dp(94), dp(60)));
            }
            boxes[4].setOnCheckedChangeListener((button, checked) -> { for (int i = 0; i < 4; i++) { if (checked) boxes[i].setChecked(false); boxes[i].setEnabled(!checked); } });
            cells.put(id, boxes); table.addView(row); View divider = new View(this); divider.setBackgroundColor(Color.rgb(226,232,240)); table.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        AlertDialog dialog = new AppDialogs.Builder(this).setTitle("Configura la tua squadra").setView(content).setNegativeButton("Annulla", null).setPositiveButton("Salva squadra", (d, which) -> {
            if (!authenticated || !owner.equals(collaboratorUuid) || !team.equals(teamUuid)) return;
            try {
                JSONObject updated = storedSkills(); for (String id : ids) { CheckBox[] boxes = cells.get(id); int mask = 0; for (int i = 0; i < boxes.length; i++) if (boxes[i].isChecked()) mask |= 1 << i; updated.put(id, boxes[4].isChecked() ? 16 : mask); }
                getSharedPreferences("swap-settings", MODE_PRIVATE).edit().putString(skillsKey(), updated.toString()).apply();
            } catch (Exception e) { showLoadError("Impossibile salvare la configurazione della squadra."); }
        }).create();
        dialog.setOnDismissListener(d -> {
            if (destroyed || !authenticated || !owner.equals(collaboratorUuid) || !team.equals(teamUuid)) return;
            if (currentTab == Tab.SEARCH) renderSwapForm();
            else if (currentTab == Tab.SETTINGS) renderSettings();
        });
        dialog.show();
    }
    private Button searchAction(String label, LinearLayout parent, Runnable action) {
        Button button = compactButton(label); button.setAllCaps(false); button.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); parent.addView(button, p); button.setOnClickListener(v -> action.run()); return button;
    }
    private String shiftChoiceKey(Shift s) { return s.collaboratorId + "|" + s.from + "|" + s.to + "|" + s.typeLabel(); }
    private ArrayList<Shift> chosenSwapShifts() { ArrayList<Shift> selected = new ArrayList<>(); for (Shift s : shifts) if (isOwnShift(s) && searchSelectedShifts.contains(shiftChoiceKey(s))) selected.add(s); selected.sort(Comparator.comparing(s -> s.from)); return selected; }
    private boolean selectableSwapShift(Shift s) { SwapSearch.Service service = SwapSearch.service(s.translation, s.abbreviation); return service != SwapSearch.Service.BLOCKED && service != SwapSearch.Service.UNKNOWN; }
    private void selectSwapShifts() {
        final String owner = collaboratorUuid; final int generation = requestGeneration;
        ArrayList<Shift> own = new ArrayList<>(); for (Shift s : shifts) if (isOwnShift(s)) own.add(s); own.sort(Comparator.comparing(s -> s.from));
        ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), dp(8), dp(12), dp(8)); scroll.addView(content);
        content.addView(text("Seleziona uno o più turni della stessa giornata. Deseleziona quelli scelti per cambiare giorno. Verranno scambiati i turni selezionati per intero.", 13, MUTED, false));
        Map<Shift, CheckBox> boxes = new HashMap<>();
        LocalDate monday = searchDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i); TextView day = text(capitalize(date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN)) + " " + date.format(dateFormat), 14, NAVY, true); day.setPadding(0, dp(10), 0, dp(3)); content.addView(day);
            boolean planned = false;
            for (Shift s : own) if (s.date.equals(date)) {
                planned = true; CheckBox box = new CheckBox(this); box.setText(s.from.toLocalTime() + "–" + s.to.toLocalTime() + " · " + s.typeLabel() + (selectableSwapShift(s) ? "" : " (non scambiabile)")); box.setTextSize(13); box.setChecked(selectableSwapShift(s) && searchSelectedShifts.contains(shiftChoiceKey(s))); box.setEnabled(selectableSwapShift(s)); boxes.put(s, box); content.addView(box);
            }
            if (!planned) content.addView(text("Riposo", 12, MUTED, false));
        }
        Runnable updateEnabled = () -> {
            LocalDate selectedDay = null; for (Shift s : own) { CheckBox box = boxes.get(s); if (box != null && box.isChecked()) { selectedDay = s.date; break; } }
            for (Shift s : own) { CheckBox box = boxes.get(s); if (box != null) box.setEnabled(selectableSwapShift(s) && (selectedDay == null || selectedDay.equals(s.date))); }
        };
        for (CheckBox box : boxes.values()) box.setOnCheckedChangeListener((button, checked) -> updateEnabled.run()); updateEnabled.run();
        AlertDialog dialog = new AppDialogs.Builder(this).setTitle("I tuoi turni · W" + weekNumber(searchDay)).setView(scroll).setNegativeButton("Annulla", null).setPositiveButton("Seleziona", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!authenticated || !owner.equals(collaboratorUuid) || generation != requestGeneration) { dialog.dismiss(); return; }
            ArrayList<Shift> selected = new ArrayList<>(); for (Shift s : own) if (boxes.containsKey(s) && boxes.get(s).isChecked()) selected.add(s);
            if (selected.isEmpty()) { new AppDialogs.Builder(this).setTitle("Scegli i turni").setMessage("Seleziona almeno un turno scambiabile.").setPositiveButton("Ho capito", null).show(); return; }
            LocalDate date = selected.get(0).date; for (Shift s : selected) if (!date.equals(s.date)) return;
            searchSelectedShifts.clear(); searchDay = date;
            for (Shift s : selected) searchSelectedShifts.add(shiftChoiceKey(s));
            dialog.dismiss(); renderSwapForm();
        })); dialog.show();
    }
    private void renderSwapForm() {
        weekList.removeAllViews(); weekLabel.setText("Cerca cambio · W" + weekNumber(searchDay)); weekSummary.setText(searchDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(dateFormat) + " – " + searchDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(dateFormat));
        boolean configured = swapConfigured();
        LinearLayout setup = new LinearLayout(this); setup.setOrientation(LinearLayout.VERTICAL); setup.setPadding(dp(12), dp(10), dp(12), dp(10)); setup.setBackgroundResource(R.drawable.bg_filter); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.bottomMargin = dp(16); weekList.addView(setup, sp);
        setup.addView(text("LA TUA SQUADRA", 12, NAVY, true));
        setup.addView(text(configured ? "Abilitazioni configurate · puoi cercare un cambio" : "Configura le abilitazioni prima di cercare un cambio", 12, MUTED, false));
        searchAction("Apri Impostazioni squadra", setup, () -> selectTab(Tab.SETTINGS));
        TextView title = text("Quando vuoi liberarti?", 20, INK, true); weekList.addView(title);
        TextView info = text("Scegli i turni da sostituire. Sono inclusi i cambi mattino/pomeriggio nello stesso giorno, a parità di ore e rispettando il minimo giornaliero di 4 ore.", 13, MUTED, false); info.setPadding(0, dp(6), 0, dp(8)); weekList.addView(info);
        ArrayList<Shift> selected = chosenSwapShifts();
        searchAction(selected.isEmpty() ? "Seleziona i tuoi turni della settimana" : "Modifica turni · " + searchDay.format(dateFormat), weekList, () -> selectSwapShifts());
        if (!selected.isEmpty()) {
            for (Shift s : selected) { TextView chosen = text(s.from.toLocalTime() + "–" + s.to.toLocalTime() + " · " + s.typeLabel(), 13, INK, true); chosen.setPadding(dp(8), dp(5), 0, 0); weekList.addView(chosen); }
        }
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        Button find = searchAction("CERCA CAMBI POSSIBILI", weekList, () -> { }); find.setEnabled(configured && !selected.isEmpty()); find.setBackgroundResource(R.drawable.bg_tab_active); find.setTextColor(Color.WHITE);
        find.setOnClickListener(v -> runSwapSearch(find, results)); weekList.addView(results);
    }
    private void runSwapSearch(Button button, LinearLayout results) {
        results.removeAllViews();
        if (!swapConfigured()) { renderSwapForm(); return; }
        ArrayList<SwapSearch.Segment> selected = new ArrayList<>();
        for (Shift s : chosenSwapShifts()) selected.add(new SwapSearch.Segment(s.collaboratorId, s.from, s.to, s.typeLabel(), SwapSearch.service(s.translation, s.abbreviation)));
        if (selected.isEmpty()) { renderSwapForm(); return; }
        LocalDateTime from = selected.get(0).start;
        LocalDateTime to = selected.stream().map(s -> s.end).max(LocalDateTime::compareTo).get();
        ArrayList<SwapSearch.Segment> planning = new ArrayList<>();
        ArrayList<String> unrecognized = new ArrayList<>();
        for (Shift s : shifts) {
            SwapSearch.Service service = SwapSearch.service(s.translation, s.abbreviation);
            if (service == SwapSearch.Service.UNKNOWN && !unrecognized.contains(s.typeLabel())) unrecognized.add(s.typeLabel());
            planning.add(new SwapSearch.Segment(s.collaboratorId, s.from, s.to, s.typeLabel(), service));
        }
        final String user = collaboratorUuid; final int generation = requestGeneration; Map<String, EnumSet<SwapSearch.Role>> skills = configuredSkills(); Map<String, String> names = new HashMap<>(collaborators);
        button.setEnabled(false); button.setText("Analisi in corso…");
        executor.execute(() -> {
            SwapSearch.Result found = SwapSearch.findSelected(user, from, to, planning, skills, selected);
            runOnUiThread(() -> {
                if (destroyed || generation != requestGeneration || currentTab != Tab.SEARCH || results.getParent() != weekList) return;
                button.setEnabled(true); button.setText("CERCA CAMBI POSSIBILI"); TextView summary = text(found.message, 14, INK, true); summary.setPadding(0, dp(14), 0, dp(10)); results.addView(summary);
                if (!unrecognized.isEmpty()) results.addView(text("Servizi non riconosciuti ed esclusi dallo scambio: " + android.text.TextUtils.join(", ", unrecognized), 12, MUTED, false));
                for (SwapSearch.Candidate candidate : found.candidates) {
                    LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(14), dp(12)); card.setBackgroundResource(R.drawable.bg_day); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(10); results.addView(card, p);
                    card.addView(text(names.getOrDefault(candidate.colleague, "Collaboratore"), 17, NAVY, true));
                    card.addView(text("Il collega copre i tuoi turni:", 13, INK, true)); for (SwapSearch.Segment s : candidate.given) card.addView(text(swapSegmentLabel(s), 13, MUTED, false));
                    card.addView(text("Tu copri in cambio:", 13, INK, true)); for (SwapSearch.Segment s : candidate.received) card.addView(text(swapSegmentLabel(s), 13, MUTED, false));
                }
                if (!found.candidates.isEmpty()) { TextView note = text("Una proposta valida per collega e giorno di restituzione. Verifica con il collega e il responsabile: riposi contrattuali, pause e limiti legali non sono verificati da questo strumento.", 12, MUTED, false); results.addView(note); }
            });
        });
    }
    private String swapSegmentLabel(SwapSearch.Segment segment) { return segment.start.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN) + " " + segment.start.format(dateFormat) + " · " + segment.start.toLocalTime() + "–" + segment.end.toLocalTime() + " · " + segment.label; }

    private interface ResponseHandler { void handle(String body); }
    private void request(String endpoint, ResponseHandler handler) {
        final int generation = requestGeneration; final String requestToken = token;
        executor.execute(() -> { HttpURLConnection connection = null; try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + requestToken); connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            int code = connection.getResponseCode(); String body = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
            runOnUiThread(() -> {
                if (destroyed || generation != requestGeneration || !authenticated) return;
                if (code >= 200 && code < 300) handler.handle(body);
                else if (code == 401 || code == 403) clearSession();
                else showLoadError("Impossibile aggiornare il planning (" + code + ").");
            });
        } catch (Exception e) { runOnUiThread(() -> { if (!destroyed && generation == requestGeneration && authenticated) showLoadError("Impossibile aggiornare. Controlla la connessione e riprova."); }); }
        finally { if (connection != null) connection.disconnect(); } });
    }

    private void parseShifts(String raw) {
        try { shifts.clear(); collectShifts(new JSONTokener(raw).nextValue()); deduplicateShifts(); shifts.sort(Comparator.comparing(s -> s.from)); }
        catch (Exception e) { planningReadable = false; shifts.clear(); status.setText("I dati del planning non sono nel formato previsto."); }
    }
    private void appendShifts(String raw) { try { collectShifts(new JSONTokener(raw).nextValue()); deduplicateShifts(); } catch (Exception e) { planningReadable = false; status.setText("I dati del planning non sono nel formato previsto."); } }
    private void deduplicateShifts() { Map<String, Shift> unique = new HashMap<>(); for (Shift shift : shifts) unique.put(shift.from + "|" + shift.to + "|" + shift.collaboratorId + "|" + shift.typeLabel(), shift); shifts.clear(); shifts.addAll(unique.values()); }
    private void collectShifts(Object node) throws Exception {
        if (node instanceof JSONArray) { JSONArray array = (JSONArray) node; for (int i = 0; i < array.length(); i++) collectShifts(array.get(i)); return; }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        if (object.has("fromHour") && object.has("toHour")) { shifts.add(Shift.from(object, collaborators)); return; }
        Iterator<String> keys = object.keys(); while (keys.hasNext()) collectShifts(object.get(keys.next()));
    }
    private void parseCollaborators(String raw) {
        collaborators.clear(); try { collectCollaborators(new JSONTokener(raw).nextValue()); } catch (Exception ignored) { }
    }
    private void collectCollaborators(Object node) throws Exception {
        if (node instanceof JSONArray) { JSONArray array = (JSONArray) node; for (int i = 0; i < array.length(); i++) collectCollaborators(array.get(i)); return; }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        String uuid = first(object, "uuid", "collaboratorUuid");
        String name = first(object, "displayName", "fullName", "name");
        if (name.isEmpty()) { String firstName = first(object, "firstName", "firstname"); String lastName = first(object, "lastName", "lastname"); name = (firstName + " " + lastName).trim(); }
        if (!uuid.isEmpty() && !name.isEmpty() && (object.has("directoryUuid") || object.has("firstLastName") || object.has("actor"))) {
            String surname = object.optString("firstLastName", "");
            collaborators.put(uuid, surname.isEmpty() ? name : name + " " + surname); return;
        }
        Iterator<String> keys = object.keys(); while (keys.hasNext()) collectCollaborators(object.get(keys.next()));
    }
    private static String first(JSONObject object, String... keys) { for (String key : keys) { String value = object.optString(key, ""); if (!value.isEmpty()) return value; } return ""; }
    private String decodeJs(String value) { try { return new JSONArray("[" + value + "]").getString(0); } catch (Exception e) { return ""; } }
    private String read(InputStream stream) throws Exception { if (stream == null) return ""; BufferedReader reader = new BufferedReader(new InputStreamReader(stream)); StringBuilder out = new StringBuilder(); String line; while ((line = reader.readLine()) != null) out.append(line); return out.toString(); }

    private void complete(String message) { refreshButton.setEnabled(true); status.setText(message + " · dati solo sul tuo dispositivo"); }
    private void renderWeek() {
        weekList.removeAllViews(); weekLabel.setText("W" + weekNumber(currentWeek) + " · " + shortRange(currentWeek));
        ArrayList<Shift> visible = shiftsInPeriod(currentWeek, currentWeek.plusDays(6)); long minutes = 0; for (Shift shift : visible) minutes += Duration.between(shift.from, shift.to).toMinutes(); weekSummary.setText(visible.size() + " turni · " + hours(minutes));
        weekReady = planningReadable; updateCalendarSyncIndicator();
        Map<LocalDate, ArrayList<Shift>> byDay = shiftsByDay();
        for (int i = 0; i < 7; i++) {
            LocalDate day = currentWeek.plusDays(i);
            weekList.addView(personalWeekRow(day, byDay.get(day)));
        }
        weekList.post(() -> {
            if (currentTab != Tab.WEEK || weekList.getChildCount() != 7) return;
            int available = ((View) weekList.getParent()).getHeight() - weekList.getPaddingTop() - weekList.getPaddingBottom();
            int used = 0;
            for (int i = 0; i < 7; i++) { View row = weekList.getChildAt(i); LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) row.getLayoutParams(); used += row.getHeight() + p.topMargin + p.bottomMargin; }
            int extra = Math.max(0, available - used) / 7;
            for (int i = 0; i < 7; i++) { View row = weekList.getChildAt(i); row.setMinimumHeight(row.getHeight() + extra); }
        });
    }
    private void renderSettings() {
        weekList.removeAllViews(); weekLabel.setText("Impostazioni"); weekSummary.setText("Preferenze e configurazioni dell’app");
        LinearLayout calendar = settingsSection("GOOGLE CALENDAR", "Scegli il calendario Google dove salvare i turni sincronizzati.");
        long calendarId = selectedCalendarId();
        calendar.addView(text(calendarId < 0 ? "Nessun calendario collegato." : "Calendario selezionato: " + selectedCalendarName(), 14, INK, false));
        Button calendarAction = compactButton(calendarId < 0 ? "COLLEGA CALENDARIO" : "CAMBIA CALENDARIO"); calendarAction.setAllCaps(false); calendarAction.setOnClickListener(v -> chooseGoogleCalendar()); calendar.addView(calendarAction, calendarButtonParams());
        TextView notifications = text("NOTIFICHE DEGLI EVENTI", 11, NAVY, true); notifications.setPadding(0, dp(18), 0, dp(6)); calendar.addView(notifications);
        calendar.addView(text("Scegli fino a due avvisi prima dell’inizio del turno. Le modifiche si applicano alla prossima sincronizzazione della settimana. Le notifiche sono gestite dall’app calendario.", 12, MUTED, false));
        addReminderSetting(calendar, 0, "Primo avviso", 1440);
        addReminderSetting(calendar, 1, "Secondo avviso", 60);
        weekList.addView(calendar);
        LinearLayout team = settingsSection("IL MIO TEAM", "Gestisci le informazioni e le configurazioni della tua squadra.");
        if (teamUuid.isEmpty()) {
            team.addView(text("Nessuna squadra associata al tuo profilo ePlanning.", 14, MUTED, false));
        } else if (collaborators.isEmpty()) {
            team.addView(text("Impossibile caricare la squadra. Premi aggiorna per riprovare.", 14, MUTED, false));
        } else {
            boolean configured = swapConfigured();
            team.addView(text(configured ? "Abilitazioni squadra configurate." : "Abilitazioni squadra da configurare.", 14, INK, false));
            Button teamAction = compactButton(configured ? "MODIFICA SQUADRA" : "CONFIGURA SQUADRA"); teamAction.setAllCaps(false); teamAction.setOnClickListener(v -> configureSwapTeam()); team.addView(teamAction, calendarButtonParams());
        }
        weekList.addView(team);
    }
    private LinearLayout settingsSection(String title, String description) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL); section.setPadding(dp(16), dp(14), dp(16), dp(14)); section.setBackgroundResource(R.drawable.bg_day);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12); section.setLayoutParams(params);
        section.addView(text(title, 11, NAVY, true)); TextView help = text(description, 12, MUTED, false); LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2); helpParams.topMargin = dp(4); helpParams.bottomMargin = dp(10); section.addView(help, helpParams);
        return section;
    }
    private static final int[] REMINDER_TIMES = { 0, 5, 10, 15, 30, 60, 120, 240, 720, 1440, 2880, 10080 };
    private static final String[] REMINDER_LABELS = { "All’inizio", "5 minuti prima", "10 minuti prima", "15 minuti prima", "30 minuti prima", "1 ora prima", "2 ore prima", "4 ore prima", "12 ore prima", "1 giorno prima", "2 giorni prima", "1 settimana prima" };
    private void addReminderSetting(LinearLayout parent, int index, String label, int defaultMinutes) {
        android.content.SharedPreferences prefs = getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox enabled = new CheckBox(this); enabled.setText(label); enabled.setTextSize(13); enabled.setTextColor(INK); enabled.setChecked(prefs.getBoolean("reminder-enabled-" + index, true));
        row.addView(enabled, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Spinner time = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, REMINDER_LABELS); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); time.setAdapter(adapter);
        int minutes = prefs.getInt("reminder-minutes-" + index, defaultMinutes);
        for (int i = 0; i < REMINDER_TIMES.length; i++) if (REMINDER_TIMES[i] == minutes) time.setSelection(i);
        time.setEnabled(enabled.isChecked()); time.setContentDescription("Anticipo " + label.toLowerCase(Locale.ITALIAN)); row.addView(time, new LinearLayout.LayoutParams(0, dp(48), 1.3f));
        enabled.setOnCheckedChangeListener((button, checked) -> { prefs.edit().putBoolean("reminder-enabled-" + index, checked).apply(); time.setEnabled(checked); });
        time.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> view, View selected, int position, long id) { prefs.edit().putInt("reminder-minutes-" + index, REMINDER_TIMES[position]).apply(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> view) { }
        });
        parent.addView(row);
    }
    private ArrayList<Integer> configuredReminders() {
        android.content.SharedPreferences prefs = getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE);
        ArrayList<Integer> minutes = new ArrayList<>();
        for (int i = 0; i < 2; i++) if (prefs.getBoolean("reminder-enabled-" + i, true)) {
            int value = prefs.getInt("reminder-minutes-" + i, i == 0 ? 1440 : 60);
            if (!minutes.contains(value)) minutes.add(value);
        }
        return minutes;
    }
    private String calendarSyncKey(String owner, long calendar, LocalDate week) { return "week-sync:" + owner + ":" + calendar + ":" + week; }
    private String weekSyncFingerprint(List<Shift> visible, List<Integer> reminders) {
        ArrayList<String> events = new ArrayList<>(); for (Shift shift : visible) events.add(eventMarker(shift));
        events.sort(String::compareTo); ArrayList<Integer> alerts = new ArrayList<>(reminders); alerts.sort(Integer::compareTo);
        return events.toString() + "|" + alerts + "|" + ZoneId.systemDefault().getId();
    }
    private void updateCalendarSyncIndicator() {
        boolean visible = currentTab == Tab.WEEK && authenticated;
        calendarSyncButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        calendarSyncButton.setEnabled(visible && weekReady && !calendarSyncing);
        calendarSyncButton.setAlpha(calendarSyncing ? .4f : 1f);
        if (!visible) { calendarSyncIndicator.setVisibility(View.GONE); return; }
        String saved = getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE).getString(calendarSyncKey(collaboratorUuid, selectedCalendarId(), currentWeek), "");
        boolean synced = !saved.isEmpty();
        boolean changed = synced && weekReady && !saved.equals(weekSyncFingerprint(shiftsInPeriod(currentWeek, currentWeek.plusDays(6)), configuredReminders()));
        calendarSyncIndicator.setVisibility(synced ? View.VISIBLE : View.GONE);
        calendarSyncIndicator.setText(changed ? "✓ Calendar · da aggiornare" : "✓ Calendar");
        calendarSyncIndicator.setTextColor(changed ? Color.rgb(254, 215, 170) : Color.rgb(187, 247, 208));
        calendarSyncButton.setColorFilter(synced ? Color.rgb(187, 247, 208) : Color.WHITE);
        calendarSyncButton.setContentDescription(synced ? "Settimana già sincronizzata. Sincronizza nuovamente su Google Calendar" : "Sincronizza settimana su Google Calendar");
    }
    private void confirmCalendarSync() {
        if (!weekReady || currentTab != Tab.WEEK || calendarSyncing) return;
        final long calendar = selectedCalendarId();
        if (calendar < 0) {
            new AppDialogs.Builder(this).setTitle("Collega un calendario").setMessage("Seleziona il calendario Google nelle Impostazioni prima di sincronizzare i turni.").setNegativeButton("Annulla", null).setPositiveButton("Impostazioni", (d, w) -> selectTab(Tab.SETTINGS)).show(); return;
        }
        final String owner = collaboratorUuid; final LocalDate week = currentWeek; final int generation = requestGeneration;
        ArrayList<Shift> visible = shiftsInPeriod(week, week.plusDays(6));
        boolean synced = getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE).contains(calendarSyncKey(owner, calendar, week));
        String message = "Settimana " + week.format(dateFormat) + " – " + week.plusDays(6).format(dateFormat) + "\nCalendario: " + selectedCalendarName() + "\n\n";
        message += synced ? "Questa settimana è già stata sincronizzata. Vuoi aggiornarla? Titoli, turni e notifiche verranno riallineati senza duplicati." : "Vuoi sincronizzare i " + visible.size() + " turni della settimana selezionata?";
        new AppDialogs.Builder(this).setTitle(synced ? "Settimana già sincronizzata" : "Sincronizza settimana").setMessage(message).setNegativeButton("Annulla", null).setPositiveButton(synced ? "Aggiorna" : "Sincronizza", (d, w) -> {
            if (!authenticated || !owner.equals(collaboratorUuid) || !week.equals(currentWeek) || generation != requestGeneration || calendar != selectedCalendarId() || currentTab != Tab.WEEK) return;
            syncWeekToCalendar(visible);
        }).show();
    }
    private LinearLayout.LayoutParams calendarButtonParams() { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44)); params.topMargin = dp(7); return params; }
    private long selectedCalendarId() { return getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE).getLong(SELECTED_CALENDAR_ID, -1); }
    private String selectedCalendarName() { return getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE).getString(SELECTED_CALENDAR_NAME, "Calendario Google"); }
    private boolean hasCalendarPermission() { return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED; }
    private void chooseGoogleCalendar() {
        if (!hasCalendarPermission()) { requestPermissions(new String[] { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR }, CALENDAR_PERMISSION_REQUEST); return; }
        executor.execute(() -> {
            ArrayList<CalendarChoice> calendars = new ArrayList<>();
            try { calendars = readGoogleCalendars(); } catch (SecurityException ignored) { }
            final ArrayList<CalendarChoice> available = calendars;
            runOnUiThread(() -> showCalendarChoices(available));
        });
    }
    private ArrayList<CalendarChoice> readGoogleCalendars() {
            ArrayList<CalendarChoice> calendars = new ArrayList<>();
            String[] projection = { CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME };
            String selection = CalendarContract.Calendars.ACCOUNT_TYPE + "=? AND " + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?";
            try (android.database.Cursor cursor = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection, selection, new String[] { GOOGLE_ACCOUNT_TYPE, String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) }, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE")) {
                if (cursor != null) while (cursor.moveToNext()) calendars.add(new CalendarChoice(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
            }
            return calendars;
    }
    private void showCalendarChoices(ArrayList<CalendarChoice> calendars) {
        if (destroyed) return;
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18), dp(12), dp(18), dp(8));
        TextView help = text(calendars.isEmpty() ? "Nessun calendario disponibile. Premi ↻ per richiedere la sincronizzazione con Google." : "Dove vuoi salvare i tuoi turni?\nSeleziona un calendario e premi Conferma.", 14, MUTED, false); help.setPadding(0, 0, 0, dp(14)); content.addView(help);
        TextView syncHelp = text("Un calendario manca? Premi ↻ e attendi. Se non compare, apri Google Calendar → Impostazioni → Mostra altri e attiva la sincronizzazione del calendario.", 12, MUTED, false); syncHelp.setPadding(0, 0, 0, dp(12)); content.addView(syncHelp);
        ScrollView scroll = new ScrollView(this);
        android.widget.RadioGroup choices = new android.widget.RadioGroup(this);
        CalendarChoice[] selected = { null };
        Runnable populate = () -> {
        long previousId = selected[0] == null ? selectedCalendarId() : selected[0].id;
        choices.removeAllViews(); selected[0] = null;
        for (CalendarChoice calendar : calendars) {
            android.widget.RadioButton choice = new android.widget.RadioButton(this);
            choice.setId(View.generateViewId()); choice.setText(calendar.name + "\n" + calendar.account + (calendar.id == selectedCalendarId() ? "\nCalendario attuale" : ""));
            choice.setTextColor(INK); choice.setTextSize(14); choice.setPadding(dp(12), dp(12), dp(12), dp(12)); choice.setMinHeight(dp(84));
            choice.setButtonTintList(android.content.res.ColorStateList.valueOf(NAVY));
            choice.setBackgroundResource(R.drawable.bg_filter);
            android.widget.RadioGroup.LayoutParams params = new android.widget.RadioGroup.LayoutParams(-1, -2); params.bottomMargin = dp(10); choices.addView(choice, params);
            choice.setOnCheckedChangeListener((button, checked) -> { if (checked) selected[0] = calendar; });
            if (calendar.id == previousId) choice.setChecked(true);
        }
        };
        populate.run();
        scroll.addView(choices); content.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(dp(280), Math.max(dp(100), calendars.size() * dp(100)))));
        AlertDialog dialog = new AppDialogs.Builder(this).setTitle("Il tuo calendario Google").setView(content).setNeutralButton(" ", null).setNegativeButton("Annulla", null).setPositiveButton("Conferma", null).create();
        choices.setOnCheckedChangeListener((group, id) -> {
            for (int i = 0; i < group.getChildCount(); i++) if (group.getChildAt(i).getId() == id) selected[0] = calendars.get(i);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected[0] != null);
        });
        dialog.setOnShowListener(ignored -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE); confirm.setEnabled(selected[0] != null);
            confirm.setOnClickListener(v -> {
            CalendarChoice chosen = selected[0]; if (chosen == null) return;
            getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE).edit().putLong(SELECTED_CALENDAR_ID, chosen.id).putString(SELECTED_CALENDAR_NAME, chosen.name).apply(); dialog.dismiss();
            if (currentTab == Tab.WEEK) renderWeek();
            else if (currentTab == Tab.SETTINGS) renderSettings();
            });
        Button refresh = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        refresh.setText(""); refresh.setContentDescription("Aggiorna elenco calendari");
        refresh.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0);
        refresh.setMinWidth(0); refresh.setMinimumWidth(0);
        refresh.getLayoutParams().width = dp(56);
        Runnable reload = () -> {
            if (destroyed || !dialog.isShowing()) return;
            if (!hasCalendarPermission()) { dialog.dismiss(); chooseGoogleCalendar(); return; }
            refresh.setEnabled(false); refresh.setAlpha(.4f);
            executor.execute(() -> {
                try {
                    ArrayList<CalendarChoice> updated = readGoogleCalendars();
                    runOnUiThread(() -> {
                        if (destroyed || !dialog.isShowing()) return;
                        choices.setOnCheckedChangeListener(null);
                        calendars.clear(); calendars.addAll(updated); populate.run();
                        scroll.getLayoutParams().height = Math.min(dp(280), Math.max(dp(100), calendars.size() * dp(100))); scroll.requestLayout();
                        choices.setOnCheckedChangeListener((group, id) -> {
                            for (int i = 0; i < group.getChildCount(); i++) if (group.getChildAt(i).getId() == id) selected[0] = calendars.get(i);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected[0] != null);
                        });
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected[0] != null);
                        help.setText(updated.isEmpty() ? "Nessun calendario ancora disponibile sul dispositivo." : updated.size() + " calendari disponibili sul dispositivo.\nSeleziona un calendario e premi Conferma.");
                        refresh.setAlpha(1f); refresh.setEnabled(true);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (destroyed || !dialog.isShowing()) return;
                        help.setText("Impossibile aggiornare l’elenco. Controlla i permessi calendario e riprova.");
                        refresh.setAlpha(1f); refresh.setEnabled(true);
                    });
                }
            });
        };
        android.database.ContentObserver observer = new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { reload.run(); }
        };
        getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, true, observer);
        dialog.setOnDismissListener(d -> getContentResolver().unregisterContentObserver(observer));
        refresh.setOnClickListener(v -> {
            if (!hasCalendarPermission()) { dialog.dismiss(); chooseGoogleCalendar(); return; }
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            try {
                // Android's calendar sync adapter owns the remote Google calendar list.
                ContentResolver.requestSync((android.accounts.Account) null, CalendarContract.AUTHORITY, extras);
                syncHelp.setText("Sincronizzazione richiesta a Google. L’elenco si aggiorna automaticamente all’arrivo dei dati. Se il calendario manca ancora, abilitalo in Google Calendar → Impostazioni → Mostra altri.");
                reload.run();
            } catch (Exception e) {
                syncHelp.setText("Impossibile richiedere la sincronizzazione. Apri Google Calendar e aggiorna da lì, poi riprova.");
                reload.run();
            }
        });
        });
        dialog.show();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CALENDAR_PERMISSION_REQUEST) {
            if (hasCalendarPermission()) chooseGoogleCalendar();
            else new AppDialogs.Builder(this).setTitle("Permesso necessario").setMessage("Per sincronizzare i turni, consenti a E-PLANNING di leggere e modificare il calendario.").setPositiveButton("Ho capito", null).show();
        }
    }
    private void syncWeekToCalendar(ArrayList<Shift> visible) {
        if (!hasCalendarPermission()) { chooseGoogleCalendar(); return; }
        if (calendarSyncing) return;
        final long calendarId = selectedCalendarId();
        final LocalDate week = currentWeek;
        final String owner = collaboratorUuid;
        final ArrayList<Integer> reminders = configuredReminders();
        final String fingerprint = weekSyncFingerprint(visible, reminders);
        if (calendarId < 0) { chooseGoogleCalendar(); return; }
        calendarSyncing = true; updateCalendarSyncIndicator();
        executor.execute(() -> {
            try {
                LocalDateTime start = week.atStartOfDay(), end = week.plusDays(7).atStartOfDay();
                ContentResolver resolver = getContentResolver();
                Map<String, Shift> expected = new HashMap<>();
                ArrayList<ContentProviderOperation> operations = new ArrayList<>();
                for (Shift shift : visible) {
                    expected.put(eventMarker(shift), shift);
                }
                String[] projection = { CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION };
                String selection = CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DTSTART + ">=? AND " + CalendarContract.Events.DTSTART + "<?";
                try (android.database.Cursor cursor = resolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, new String[] { String.valueOf(calendarId), String.valueOf(toMillis(start)), String.valueOf(toMillis(end)) }, null)) {
                    if (cursor != null) while (cursor.moveToNext()) {
                        String marker = markerFrom(cursor.getString(1));
                        if (marker == null) continue;
                        Shift existing = expected.remove(marker);
                        if (existing != null) {
                            long id = cursor.getLong(0);
                            operations.add(ContentProviderOperation.newUpdate(CalendarContract.Events.CONTENT_URI).withSelection(CalendarContract.Events._ID + "=?", new String[] { String.valueOf(id) }).withValues(calendarEventValues(calendarId, existing, marker, !reminders.isEmpty())).build());
                            operations.add(ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection(CalendarContract.Reminders.EVENT_ID + "=?", new String[] { String.valueOf(id) }).build());
                            appendCalendarReminders(operations, id, -1, reminders);
                        }
                        else operations.add(ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI).withSelection(CalendarContract.Events._ID + "=?", new String[] { String.valueOf(cursor.getLong(0)) }).build());
                    }
                }
                for (Shift shift : expected.values()) {
                    int reference = operations.size();
                    operations.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(calendarEventValues(calendarId, shift, eventMarker(shift), !reminders.isEmpty())).build());
                    appendCalendarReminders(operations, -1, reference, reminders);
                }
                if (!operations.isEmpty()) resolver.applyBatch(CalendarContract.AUTHORITY, operations);
                getSharedPreferences(CALENDAR_SETTINGS, MODE_PRIVATE).edit().putString(calendarSyncKey(owner, calendarId, week), fingerprint).commit();
                runOnUiThread(() -> { if (!destroyed) { calendarSyncing = false; updateCalendarSyncIndicator(); } });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    calendarSyncing = false; updateCalendarSyncIndicator();
                    new AppDialogs.Builder(this).setTitle("Sincronizzazione non riuscita").setMessage("Controlla il permesso calendario e riprova.").setPositiveButton("Ho capito", null).show();
                });
            }
        });
    }
    private void appendCalendarReminders(ArrayList<ContentProviderOperation> operations, long eventId, int reference, List<Integer> reminders) {
        for (int minutes : reminders) {
            ContentProviderOperation.Builder reminder = ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValue(CalendarContract.Reminders.MINUTES, minutes).withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            if (reference >= 0) reminder.withValueBackReference(CalendarContract.Reminders.EVENT_ID, reference);
            else reminder.withValue(CalendarContract.Reminders.EVENT_ID, eventId);
            operations.add(reminder.build());
        }
    }
    private ContentValues calendarEventValues(long calendarId, Shift shift, String marker, boolean hasAlarm) {
        ContentValues values = new ContentValues(); values.put(CalendarContract.Events.CALENDAR_ID, calendarId); values.put(CalendarContract.Events.TITLE, shift.typeLabel() + " • E-PLANNING"); values.put(CalendarContract.Events.DESCRIPTION, marker + "\nTurno importato da E-PLANNING"); values.put(CalendarContract.Events.DTSTART, toMillis(shift.from)); values.put(CalendarContract.Events.DTEND, toMillis(shift.to)); values.put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().getId()); values.put(CalendarContract.Events.HAS_ALARM, hasAlarm ? 1 : 0); return values;
    }
    private long toMillis(LocalDateTime time) { return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    private String eventMarker(Shift shift) { return EVENT_MARKER + shift.from + "|" + shift.to + "|" + shift.typeLabel(); }
    private String markerFrom(String description) { if (description == null || !description.startsWith(EVENT_MARKER)) return null; int end = description.indexOf('\n'); return end < 0 ? description : description.substring(0, end); }
    private View personalWeekRow(LocalDate day, ArrayList<Shift> list) {
        boolean today = day.equals(LocalDate.now());
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8)); row.setBackgroundResource(today ? R.drawable.bg_day_current : R.drawable.bg_day);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.bottomMargin = dp(6); row.setLayoutParams(rp);
        LinearLayout date = new LinearLayout(this); date.setOrientation(LinearLayout.VERTICAL);
        date.addView(text(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN).toUpperCase() + (today ? " · OGGI" : ""), 11, today ? NAVY : MUTED, true));
        date.addView(text(day.format(dateFormat), 17, INK, true)); row.addView(date, new LinearLayout.LayoutParams(dp(90), -2));
        LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); long minutes = 0;
        if (list == null || list.isEmpty()) details.addView(text("Riposo", 14, MUTED, false));
        else for (Shift shift : list) {
            minutes += Duration.between(shift.from, shift.to).toMinutes();
            LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(8), dp(5), dp(8), dp(5));
            GradientDrawable background = new GradientDrawable(); background.setColor(shift.color); background.setCornerRadius(dp(8)); line.setBackground(background);
            int foreground = contrastText(shift.color);
            TextView times = text(shift.from.toLocalTime() + " – " + shift.to.toLocalTime(), 14, foreground, true);
            line.addView(times, new LinearLayout.LayoutParams(0, -2, 1));
            TextView type = text(shift.typeLabel(), 11, foreground, true);
            type.setMaxWidth(dp(90)); line.addView(type);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); if (details.getChildCount() > 0) lp.topMargin = dp(3); details.addView(line, lp);
        }
        if (minutes > 0) details.addView(text(hours(minutes), 11, MUTED, false));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1)); row.setOnClickListener(v -> showDayShifts(day, list)); return row;
    }
    private void renderMonth() {
        weekList.removeAllViews(); weekLabel.setText(capitalize(currentMonth.format(monthFormat)));
        long minutes = 0; for (Shift shift : shifts) minutes += Duration.between(shift.from, shift.to).toMinutes();
        weekSummary.setText(shifts.size() + " turni · " + hours(minutes));
        Map<LocalDate, ArrayList<Shift>> byDay = shiftsByDay();
        LocalDate cursor = currentMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = currentMonth.plusMonths(1).minusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        while (!cursor.isAfter(end)) { weekList.addView(monthWeek(cursor, byDay)); cursor = cursor.plusWeeks(1); }
        fillMonthHeight();
    }
    private void renderTeam(LocalDate monday) {
        if (teamGrouping == TeamGrouping.DAILY) { renderDailyTimeline(); return; }
        if (teamDayMode) { renderTeamDay(currentTeamDay); return; }
        weekList.removeAllViews(); weekLabel.setText(teamWeeksLabel(monday));
        ArrayList<Shift> visible = shiftsInPeriod(monday, monday.plusDays(6)); long minutes = 0;
        for (Shift shift : visible) minutes += Duration.between(shift.from, shift.to).toMinutes();
        weekSummary.setText(visible.size() + " turni · " + hours(minutes));
        weekList.addView(teamOptions());
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i); ArrayList<Shift> daily = shiftsInPeriod(day, day);
            TeamChart chart = new TeamChart(day, daily, teamGrouping);
            chart.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_day);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(dp(360), teamChartMinHeight(daily))); p.bottomMargin = dp(12);
            weekList.addView(scrollChart(chart, daily, teamGrouping), p);
        }
    }
    private void renderPeriod(LocalDate start, LocalDate end, String title, boolean showWeekDividers) {
        weekList.removeAllViews(); weekLabel.setText(title);
        ArrayList<Shift> visible = shiftsInPeriod(start, end); long minutes = 0; for (Shift shift : visible) minutes += Duration.between(shift.from, shift.to).toMinutes();
        weekSummary.setText(visible.size() + (visible.size() == 1 ? " turno" : " turni") + " · " + hours(minutes));
        Map<LocalDate, ArrayList<Shift>> byDay = shiftsByDay();
        if (currentTab == Tab.TEAM) weekList.addView(teamOptions());
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) { if (showWeekDividers && day.getDayOfWeek() == DayOfWeek.MONDAY) weekList.addView(weekDivider(day)); weekList.addView(dayCard(day, byDay.get(day))); }
    }
    private Map<LocalDate, ArrayList<Shift>> shiftsByDay() { Map<LocalDate, ArrayList<Shift>> byDay = new HashMap<>(); for (Shift shift : shifts) byDay.computeIfAbsent(shift.date, ignored -> new ArrayList<>()).add(shift); return byDay; }
    private ArrayList<Shift> shiftsInPeriod(LocalDate start, LocalDate end) { ArrayList<Shift> result = new ArrayList<>(); for (Shift shift : shifts) if (!shift.date.isBefore(start) && !shift.date.isAfter(end)) result.add(shift); return result; }
    private void renderEmpty(String headline, String body) { weekLabel.setText("Il tuo planning"); weekSummary.setText("Accedi al portale per vedere i turni"); weekList.removeAllViews(); weekList.addView(empty(headline, body)); }
    private View dayCard(LocalDate day, ArrayList<Shift> list) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_day); card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(0, 0, 0, dp(8)); card.setLayoutParams(cp);
        LinearLayout headerRow = new LinearLayout(this); headerRow.setGravity(Gravity.CENTER_VERTICAL); headerRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView dayName = text(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN), 15, INK, true); dayName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        headerRow.addView(dayName); headerRow.addView(text(day.format(dateFormat), 13, MUTED, false)); card.addView(headerRow);
        if (list == null || list.isEmpty()) { TextView rest = text("Riposo", 13, MUTED, false); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(7); rest.setLayoutParams(p); card.addView(rest); return card; }
        long mins = 0; for (Shift shift : list) { mins += Duration.between(shift.from, shift.to).toMinutes(); card.addView(shiftRow(shift)); }
        TextView total = text(hours(mins), 12, MUTED, true); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(7); total.setLayoutParams(p); card.addView(total); return card;
    }
    private View shiftRow(Shift shift) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(lighten(shift.color, .88f)); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1), shift.color); row.setBackground(bg);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(7); row.setLayoutParams(rp);
        TextView time = text(shift.from.toLocalTime().toString() + " – " + shift.to.toLocalTime().toString(), 14, INK, true); time.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(time); row.addView(text(shift.label(), 12, shift.color, true)); return row;
    }
    private View weekDivider(LocalDate monday) {
        TextView divider = text("SETTIMANA " + weekNumber(monday), 12, Color.rgb(71, 85, 105), true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(4), dp(8), 0, dp(6)); divider.setLayoutParams(p); return divider;
    }
    private View teamOptions() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2); pp.bottomMargin = dp(10); panel.setLayoutParams(pp);
        panel.addView(text("INTERVALLO", 10, MUTED, true));
        LinearLayout periods = new LinearLayout(this);
        for (int i = 0; i < 2; i++) {
            final boolean day = i == 0;
            Button button = optionButton(day ? "Giorno" : "Settimana", teamDayMode == day);
            button.setOnClickListener(v -> { teamDayMode = day; if (day && (currentTeamDay.isBefore(currentTeamWeek) || currentTeamDay.isAfter(currentTeamWeek.plusDays(6)))) currentTeamDay = currentTeamWeek; renderTeam(currentTeamWeek); });
            periods.addView(button, optionParams());
        }
        panel.addView(periods); TextView label = text("RAGGRUPPAMENTO", 10, MUTED, true); label.setPadding(0, dp(7), 0, 0); panel.addView(label);
        LinearLayout groups = new LinearLayout(this);
        TeamGrouping[] values = { TeamGrouping.DAY, TeamGrouping.COLLABORATOR, TeamGrouping.DAILY };
        String[] titles = { "Per ora", "Per collaboratore", "Per giornata" };
        for (int i = 0; i < values.length; i++) {
            final TeamGrouping value = values[i]; Button button = optionButton(titles[i], value == teamGrouping);
            button.setOnClickListener(v -> { teamGrouping = value; renderTeam(currentTeamWeek); }); groups.addView(button, optionParams());
        }
        panel.addView(groups); return panel;
    }
    private LinearLayout.LayoutParams optionParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1); p.setMargins(dp(2), dp(3), dp(2), 0); return p; }
    private Button optionButton(String title, boolean selected) { Button button = compactButton(title); button.setAllCaps(false); button.setTextSize(11); button.setBackgroundResource(selected ? R.drawable.bg_tab_active : R.drawable.bg_filter); button.setTextColor(selected ? Color.WHITE : NAVY); button.setSelected(selected); return button; }
    private ArrayList<String> teamPeople(ArrayList<Shift> data) {
        ArrayList<String> people = new ArrayList<>();
        for (String name : collaborators.values()) if (!people.contains(name)) people.add(name);
        for (Shift shift : data) { String name = shift.collaborator.isEmpty() ? "Collaboratore" : shift.collaborator; if (!people.contains(name)) people.add(name); }
        people.sort(String::compareToIgnoreCase);
        String ownName = collaborators.get(collaboratorUuid);
        if (ownName != null && people.remove(ownName)) people.add(0, ownName);
        return people;
    }
    private boolean isOwnShift(Shift shift) { return !collaboratorUuid.isEmpty() && collaboratorUuid.equals(shift.collaboratorId); }
    private boolean isOwnName(String name) { return name.equals(collaborators.get(collaboratorUuid)); }
    private void highlightOwnBand(Canvas canvas, Paint paint, RectF band) { paint.setColor(Color.rgb(232, 240, 255)); canvas.drawRect(band, paint); }
    private void outlineOwnShift(Canvas canvas, Paint paint, RectF box, Shift shift) {
        if (!isOwnShift(shift)) return;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(NAVY);
        canvas.drawRoundRect(box, dp(6), dp(6), paint); paint.setStyle(Paint.Style.FILL); paint.setStrokeWidth(1);
    }
    private Button compactButton(String label) { Button button = new Button(this); button.setText(label); button.setTextSize(12); button.setTextColor(NAVY); button.setTypeface(null, android.graphics.Typeface.BOLD); button.setBackgroundResource(R.drawable.bg_filter); return button; }
    private HorizontalScrollView scrollChart(View chart, ArrayList<Shift> data, TeamGrouping grouping) {
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setFillViewport(true); scroll.setHorizontalScrollBarEnabled(true);
        int width = grouping == TeamGrouping.DAY ? Math.max(dp(360), dp(50 + teamPeople(data).size() * 110)) : dp(1000);
        chart.setMinimumWidth(width);
        scroll.addView(chart, new android.widget.FrameLayout.LayoutParams(width, -1)); return scroll;
    }
    private int teamChartMinHeight(ArrayList<Shift> data) { return teamGrouping == TeamGrouping.COLLABORATOR ? dp(80 + teamPeople(data).size() * 52) : dp(280); }
    private void renderTeamDay(LocalDate day) {
        weekList.removeAllViews(); weekLabel.setText("W" + weekNumber(day) + " · " + day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN).toUpperCase() + " " + day.format(dateFormat).toUpperCase());
        ArrayList<Shift> today = new ArrayList<>(); for (Shift shift : shifts) if (shift.date.equals(day)) today.add(shift); long minutes = 0; for (Shift shift : today) minutes += Duration.between(shift.from, shift.to).toMinutes(); weekSummary.setText(today.size() + " turni · " + hours(minutes));
        weekList.addView(teamOptions()); TeamChart chart = new TeamChart(day, today, teamGrouping); chart.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_day); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(390)); HorizontalScrollView viewport = scrollChart(chart, today, teamGrouping); weekList.addView(viewport, p);
        weekList.post(() -> {
            if (currentTab != Tab.TEAM || !teamDayMode || viewport.getParent() != weekList) return;
            View controls = weekList.getChildAt(0);
            int available = ((View) weekList.getParent()).getHeight() - weekList.getPaddingTop() - weekList.getPaddingBottom() - controls.getHeight() - dp(10);
            viewport.getLayoutParams().height = Math.max(teamChartMinHeight(today), available); viewport.requestLayout();
        });
    }
    private void renderDailyTimeline() {
        weekList.removeAllViews(); LocalDate start = teamDayMode ? currentTeamDay : currentTeamWeek;
        LocalDate end = teamDayMode ? start : start.plusDays(6); ArrayList<Shift> visible = shiftsInPeriod(start, end);
        weekLabel.setText("W" + weekNumber(start) + (teamDayMode ? " · " + start.format(dateFormat).toUpperCase() : ""));
        long minutes = 0; for (Shift shift : visible) minutes += Duration.between(shift.from, shift.to).toMinutes(); weekSummary.setText(visible.size() + " turni · " + hours(minutes));
        weekList.addView(teamOptions());
        DailyTimeline chart = new DailyTimeline(start, teamDayMode ? 1 : 7);
        chart.setMinimumWidth(dp(1400)); chart.setBackgroundResource(R.drawable.bg_day); HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setFillViewport(true);
        scroll.addView(chart, new android.widget.FrameLayout.LayoutParams(dp(1400), -1));
        weekList.addView(scroll, new LinearLayout.LayoutParams(-1, chart.contentHeight));
        weekList.post(() -> {
            if (currentTab != Tab.TEAM || teamGrouping != TeamGrouping.DAILY || scroll.getParent() != weekList) return;
            int available = ((View) weekList.getParent()).getHeight() - weekList.getPaddingBottom() - weekList.getChildAt(0).getHeight() - dp(10);
            scroll.getLayoutParams().height = Math.max(chart.contentHeight, available); scroll.requestLayout();
        });
    }
    private class DailyTimeline extends View {
        private final LocalDate start; private final int days; private final Paint paint = new Paint(1);
        private final ArrayList<ArrayList<ArrayList<Shift>>> lanesByDay = new ArrayList<>();
        private int axisStart = 24 * 60, axisEnd = 0;
        final int contentHeight;
        DailyTimeline(LocalDate start, int days) {
            super(MainActivity.this); this.start = start; this.days = days; int height = dp(48);
            for (int d = 0; d < days; d++) {
                ArrayList<Shift> daily = shiftsInPeriod(start.plusDays(d), start.plusDays(d)); daily.sort(Comparator.comparing((Shift s) -> !isOwnShift(s)).thenComparing(s -> s.from));
                ArrayList<ArrayList<Shift>> lanes = new ArrayList<>();
                for (Shift shift : daily) {
                    axisStart = Math.min(axisStart, shift.from.getHour() * 60);
                    axisEnd = Math.max(axisEnd, (int) (Duration.between(shift.date.atStartOfDay(), shift.to).toMinutes() + 59) / 60 * 60);
                    boolean placed = false;
                    for (ArrayList<Shift> lane : lanes) if (isOwnShift(lane.get(0)) == isOwnShift(shift) && !lane.get(lane.size() - 1).to.isAfter(shift.from)) { lane.add(shift); placed = true; break; }
                    if (!placed) { ArrayList<Shift> lane = new ArrayList<>(); lane.add(shift); lanes.add(lane); }
                }
                lanesByDay.add(lanes);
                height += dp(Math.max(1, lanes.size()) * 44 + 60);
            }
            if (axisEnd <= axisStart) { axisStart = 7 * 60; axisEnd = 21 * 60; }
            contentHeight = height;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float left = dp(116), top = dp(42), unit = (getWidth() - left - dp(20)) / (axisEnd - axisStart);
            paint.setTypeface(android.graphics.Typeface.DEFAULT); paint.setTextSize(dp(11));
            for (int hour = axisStart / 60; hour <= axisEnd / 60; hour++) { float x = left + (hour * 60 - axisStart) * unit; paint.setColor(Color.rgb(226,232,240)); canvas.drawLine(x, top, x, getHeight() - dp(10), paint); paint.setColor(MUTED); canvas.drawText(String.format(Locale.ITALIAN, "%02d:00", hour % 24), x, dp(23), paint); }
            float y = top; float extra = Math.max(0, getHeight() - contentHeight) / (float) days;
            for (int d = 0; d < days; d++) {
                LocalDate day = start.plusDays(d); ArrayList<ArrayList<Shift>> lanes = lanesByDay.get(d); float height = dp(Math.max(1, lanes.size()) * 44 + 60) + extra;
                if (day.equals(LocalDate.now())) { paint.setColor(Color.rgb(239,246,255)); canvas.drawRect(0, y, getWidth(), y + height, paint); }
                paint.setColor(day.equals(LocalDate.now()) ? NAVY : INK); paint.setTextSize(dp(13)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN).toUpperCase() + " " + day.format(dateFormat), dp(10), y + dp(23), paint);
                if (day.equals(LocalDate.now())) { paint.setTextSize(dp(10)); canvas.drawText("OGGI", dp(10), y + dp(40), paint); }
                for (int lane = 0; lane < lanes.size(); lane++) if (isOwnShift(lanes.get(lane).get(0))) highlightOwnBand(canvas, paint, new RectF(left, y + dp(3 + lane * 44), getWidth(), y + dp(47 + lane * 44)));
                for (int lane = 0; lane < lanes.size(); lane++) for (Shift shift : lanes.get(lane)) {
                    float x = left + (shift.from.getHour() * 60 + shift.from.getMinute() - axisStart) * unit;
                    float duration = Duration.between(shift.from, shift.to).toMinutes();
                    RectF box = new RectF(x, y + dp(6 + lane * 44), x + Math.max(dp(4), duration * unit), y + dp(44 + lane * 44));
                    paint.setColor(shift.color); canvas.drawRoundRect(box, dp(6), dp(6), paint);
                    outlineOwnShift(canvas, paint, box, shift);
                    canvas.save(); canvas.clipRect(box); paint.setColor(contrastText(shift.color)); paint.setTextSize(dp(12));
                    canvas.drawText(shift.collaborator + (isOwnShift(shift) ? " · TU" : ""), box.left + dp(6), box.top + dp(15), paint);
                    paint.setTextSize(dp(11)); canvas.drawText(shift.from.toLocalTime() + "–" + shift.to.toLocalTime() + " / " + shift.typeLabel(), box.left + dp(6), box.top + dp(30), paint); canvas.restore();
                }
                paint.setTypeface(android.graphics.Typeface.DEFAULT); paint.setTextSize(dp(11)); paint.setColor(MUTED);
                if (lanes.isEmpty()) canvas.drawText("Nessun turno pianificato", left, y + dp(23), paint);
                paint.setColor(Color.rgb(203,213,225)); canvas.drawLine(0, y + height, getWidth(), y + height, paint); y += height;
            }
        }
    }
    private class TeamChart extends View {
        private final LocalDate day; private final ArrayList<Shift> data; private final TeamGrouping grouping; private final Paint paint = new Paint(1);
        TeamChart(LocalDate day, ArrayList<Shift> data, TeamGrouping grouping) { super(MainActivity.this); this.day = day; this.data = data; this.grouping = grouping; paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); int w = getWidth(), h = getHeight(); float left = dp(grouping == TeamGrouping.DAY ? 48 : 96), top = dp(60), bottom = h - dp(16); int start = 7 * 60, end = 21 * 60;
            paint.setColor(INK); paint.setTextSize(dp(16)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); canvas.drawText(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " " + day.format(dateFormat), dp(16), dp(24), paint);
            ArrayList<String> people = teamPeople(data);
            if (people.isEmpty()) { paint.setColor(MUTED); paint.setTextSize(dp(14)); canvas.drawText("Nessun turno pianificato", dp(16), dp(70), paint); return; }
            if (grouping == TeamGrouping.DAY) drawByTime(canvas, people, left, top, w - dp(10), bottom, start, end); else drawByPerson(canvas, people, left, top, w - dp(10), bottom, start, end);
        }
        private void drawByTime(Canvas canvas, ArrayList<String> people, float left, float top, float right, float bottom, int start, int end) {
            float columnWidth = (right - left) / people.size();
            for (int i = 0; i < people.size(); i++) if (isOwnName(people.get(i))) highlightOwnBand(canvas, paint, new RectF(left + i * columnWidth, top - dp(34), left + (i + 1) * columnWidth, bottom));
            float lane = (right - left) / people.size(); for (int hour = start / 60; hour <= end / 60; hour++) { float y = top + (hour * 60 - start) * (bottom - top) / (end - start); paint.setColor(Color.rgb(203,213,225)); paint.setStrokeWidth(1); canvas.drawLine(left, y, right, y, paint); paint.setColor(MUTED); paint.setTextSize(dp(10)); canvas.drawText(hour + ":00", dp(4), y + dp(3), paint); }
            for (int i = 0; i < people.size(); i++) {
                String name = people.get(i); String[] parts = name.split(" ", 2);
                paint.setColor(isOwnName(name) ? NAVY : MUTED); paint.setTextSize(dp(10)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.save(); canvas.clipRect(left + i * lane, top - dp(34), left + (i + 1) * lane, top);
                canvas.drawText(parts[0], left + i * lane + dp(3), top - dp(21), paint);
                String second = (parts.length > 1 ? parts[1] : "") + (isOwnName(name) ? " · TU" : "");
                float width = paint.measureText(second); if (width > lane - dp(6)) paint.setTextSize(dp(10) * (lane - dp(6)) / width);
                canvas.drawText(second, left + i * lane + dp(3), top - dp(7), paint); canvas.restore();
            }
            for (Shift s : data) { int i = people.indexOf(s.collaborator.isEmpty() ? "Collaboratore" : s.collaborator); float y1 = top + minutes(s.from) * (bottom-top)/(end-start), y2 = top + minutes(s.to)*(bottom-top)/(end-start); drawBlock(canvas, new RectF(left+i*lane+dp(3), y1, left+(i+1)*lane-dp(3), y2), s, false); }
        }
        private void drawByPerson(Canvas canvas, ArrayList<String> people, float left, float top, float right, float bottom, int start, int end) {
            float rowHeight = (bottom - top) / people.size();
            for (int i = 0; i < people.size(); i++) if (isOwnName(people.get(i))) highlightOwnBand(canvas, paint, new RectF(0, top + i * rowHeight, right, top + (i + 1) * rowHeight));
            float row = (bottom-top)/people.size(); for (int hour=start/60; hour<=end/60; hour++) { float x=left+(hour*60-start)*(right-left)/(end-start); paint.setColor(Color.rgb(203,213,225)); canvas.drawLine(x, top, x, bottom, paint); paint.setColor(MUTED); paint.setTextSize(dp(9)); canvas.drawText(hour + ":00", x, top-dp(8), paint); }
            for (int i=0;i<people.size();i++) { float y=top+i*row; paint.setColor(MUTED); paint.setTextSize(dp(11)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); canvas.drawText(people.get(i), dp(4), y+dp(18), paint); paint.setColor(Color.rgb(203,213,225)); canvas.drawLine(left,y+row,right,y+row,paint); }
            for (Shift s:data) { int i=people.indexOf(s.collaborator.isEmpty()?"Collaboratore":s.collaborator); float x1=left+minutes(s.from)*(right-left)/(end-start), x2=left+minutes(s.to)*(right-left)/(end-start); drawBlock(canvas,new RectF(x1,top+i*row+dp(4),x2,top+(i+1)*row-dp(4)),s,true); }
        }
        private int minutes(LocalDateTime time) { return time.getHour()*60+time.getMinute()-7*60; }
        private void drawBlock(Canvas canvas, RectF box, Shift shift, boolean compact) {
            paint.setColor(shift.color); canvas.drawRoundRect(box, dp(6), dp(6), paint);
            outlineOwnShift(canvas, paint, box, shift);
            canvas.save(); canvas.clipRect(box); paint.setColor(contrastText(shift.color)); paint.setTextSize(dp(10)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            String times = shift.from.toLocalTime()+"–"+shift.to.toLocalTime();
            float typeY;
            if (paint.measureText(times) <= box.width()-dp(8)) { canvas.drawText(times,box.left+dp(4),box.top+dp(14),paint); typeY = box.top + dp(28); }
            else { canvas.drawText(shift.from.toLocalTime().toString(),box.left+dp(4),box.top+dp(14),paint); canvas.drawText(shift.to.toLocalTime().toString(),box.left+dp(4),box.top+dp(27),paint); typeY = box.top + dp(40); }
            canvas.drawText(shift.typeLabel(), box.left+dp(4), typeY, paint);
            canvas.restore();
        }
    }
    private void renderTeamByCollaborator(LocalDate start, LocalDate end) {
        weekList.removeAllViews(); weekLabel.setText(teamWeeksLabel(start));
        long minutes = 0; for (Shift shift : shifts) minutes += Duration.between(shift.from, shift.to).toMinutes(); weekSummary.setText(shifts.size() + " turni · " + hours(minutes));
        weekList.addView(teamOptions());
        Map<String, ArrayList<Shift>> byPerson = new HashMap<>(); for (Shift shift : shifts) byPerson.computeIfAbsent(shift.collaborator.isEmpty() ? "Collaboratore" : shift.collaborator, ignored -> new ArrayList<>()).add(shift);
        ArrayList<String> names = new ArrayList<>(byPerson.keySet()); names.sort(String::compareToIgnoreCase);
        for (String name : names) {
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(12), dp(16), dp(12)); card.setBackgroundResource(R.drawable.bg_day);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(0, 0, 0, dp(8)); card.setLayoutParams(cp); card.addView(text(name, 16, INK, true));
            for (Shift shift : byPerson.get(name)) { TextView when = text(shift.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN) + " " + shift.date.format(dateFormat), 12, MUTED, true); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); when.setLayoutParams(p); card.addView(when); card.addView(shiftRowWithoutCollaborator(shift)); }
            weekList.addView(card);
        }
    }
    private View shiftRowWithoutCollaborator(Shift shift) {
        String original = shift.collaborator; shift.collaborator = ""; View row = shiftRow(shift); shift.collaborator = original; return row;
    }
    private View monthWeek(LocalDate monday, Map<LocalDate, ArrayList<Shift>> byDay) {
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); group.setPadding(dp(10), dp(8), dp(10), dp(8)); group.setBackgroundResource(R.drawable.bg_week_group);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2); gp.setMargins(0, 0, 0, dp(8)); group.setLayoutParams(gp);
        TextView title = text("SETTIMANA " + weekNumber(monday), 11, MUTED, true); title.setOnClickListener(v -> { currentWeek = monday; selectTab(Tab.WEEK); }); group.addView(title);
        LinearLayout days = new LinearLayout(this); days.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2); dp.topMargin = dp(4); days.setLayoutParams(dp);
        for (int i = 0; i < 7; i++) { LocalDate day = monday.plusDays(i); days.addView(monthCell(day, byDay.get(day))); } group.addView(days); return group;
    }
    private View monthCell(LocalDate day, ArrayList<Shift> list) {
        LinearLayout cell = new LinearLayout(this); cell.setGravity(Gravity.CENTER); cell.setOrientation(LinearLayout.VERTICAL); cell.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(1), 0, dp(1), 0); cell.setLayoutParams(p);
        boolean inMonth = day.getMonth().equals(currentMonth.getMonth()) && day.getYear() == currentMonth.getYear();
        if (day.equals(LocalDate.now())) cell.setBackgroundResource(R.drawable.bg_day_current);
        TextView number = text(inMonth ? day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN) + "\n" + day.getDayOfMonth() : "", 12, inMonth ? INK : Color.TRANSPARENT, true); number.setGravity(Gravity.CENTER); cell.addView(number, new LinearLayout.LayoutParams(-1, -2));
        long mins = 0; if (list != null) for (Shift shift : list) mins += Duration.between(shift.from, shift.to).toMinutes();
        TextView total = text(!inMonth ? "" : shortHours(mins), 10, MUTED, false); total.setGravity(Gravity.CENTER); total.setPadding(0, dp(5), 0, 0); cell.addView(total, new LinearLayout.LayoutParams(-1, -2)); if (inMonth) cell.setOnClickListener(v -> showDayShifts(day, list)); return cell;
    }
    private void fillMonthHeight() {
        weekList.post(() -> {
            if (currentTab != Tab.MONTH || weekList.getChildCount() == 0) return;
            int available = ((View) weekList.getParent()).getHeight() - weekList.getPaddingTop() - weekList.getPaddingBottom();
            int rowHeight = Math.max(0, available / weekList.getChildCount() - dp(8));
            for (int i = 0; i < weekList.getChildCount(); i++) weekList.getChildAt(i).setMinimumHeight(rowHeight);
        });
    }
    private View weekCell(LocalDate day, ArrayList<Shift> list) { LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setPadding(dp(4), dp(7), dp(4), dp(7)); cell.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_week_cell); cell.setGravity(Gravity.CENTER_HORIZONTAL); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1); p.setMargins(dp(1), 0, dp(1), 0); cell.setLayoutParams(p); TextView label = text(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN).substring(0, 1).toUpperCase() + "\n" + day.getDayOfMonth(), 12, INK, true); label.setGravity(Gravity.CENTER); cell.addView(label); if (list != null) for (Shift s : list) { TextView row = text(s.from.toLocalTime() + "\n" + s.to.toLocalTime(), 10, s.color, true); row.setGravity(Gravity.CENTER); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(7); row.setLayoutParams(rp); cell.addView(row); } return cell; }
    private void showDayShifts(LocalDate day, ArrayList<Shift> list) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (list == null || list.isEmpty()) content.addView(text("Nessun turno previsto · Riposo", 15, MUTED, false));
        else for (Shift shift : list) content.addView(shiftRow(shift));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AppDialogs.Builder(this).setTitle(capitalize(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN)) + " " + day.format(dateFormat)).setView(scroll).setPositiveButton("Chiudi", null).show();
    }
    private String weekNumber(LocalDate date) { return String.valueOf(date.get(WeekFields.ISO.weekOfWeekBasedYear())); }
    private String teamWeeksLabel(LocalDate start) { StringBuilder label = new StringBuilder(); for (int i = 0; i < teamWeekCount; i++) { if (i > 0) label.append(", "); label.append("W").append(weekNumber(start.plusWeeks(i))); } return label.toString(); }
    private String shortRange(LocalDate monday) { return monday.format(DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)).toUpperCase() + " " + monday.getDayOfMonth() + "-" + monday.plusDays(6).getDayOfMonth(); }
    private String capitalize(String value) { return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private View empty(String headline, String body) { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(28), dp(36), dp(28), dp(36)); box.setBackgroundResource(R.drawable.bg_day); box.addView(text(headline, 18, INK, true)); TextView hint = text(body, 14, MUTED, false); hint.setGravity(Gravity.CENTER); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); hint.setLayoutParams(p); box.addView(hint); return box; }
    private TextView text(String value, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view; }
    private String hours(long minutes) { long h = minutes / 60, m = minutes % 60; return m == 0 ? h + " ore" : h + "h " + m + "m"; }
    private String shortHours(long minutes) { long h = minutes / 60, m = minutes % 60; return m == 0 ? h + "h" : h + "h" + m; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private int contrastText(int color) {
        double[] channels = { Color.red(color) / 255.0, Color.green(color) / 255.0, Color.blue(color) / 255.0 };
        for (int i = 0; i < channels.length; i++) channels[i] = channels[i] <= .04045 ? channels[i] / 12.92 : Math.pow((channels[i] + .055) / 1.055, 2.4);
        double luminance = .2126 * channels[0] + .7152 * channels[1] + .0722 * channels[2];
        return luminance > .179 ? Color.BLACK : Color.WHITE;
    }
    private int lighten(int color, float amount) { int r = (int) (Color.red(color) + (255 - Color.red(color)) * amount), g = (int) (Color.green(color) + (255 - Color.green(color)) * amount), b = (int) (Color.blue(color) + (255 - Color.blue(color)) * amount); return Color.rgb(r, g, b); }
    @Override protected void onDestroy() { destroyed = true; if (updater != null) updater.destroy(); if (workday != null) workday.destroy(); authGeneration++; authHandler.removeCallbacksAndMessages(null); requestGeneration++; executor.shutdownNow(); portal.destroy(); super.onDestroy(); }

    private static class Shift {
        LocalDateTime from, to; LocalDate date; String translation, abbreviation, collaborator, collaboratorId; int color;
        static Shift from(JSONObject object, Map<String, String> collaborators) throws Exception {
            Shift shift = new Shift(); shift.from = LocalDateTime.parse(object.getString("fromHour")); shift.to = LocalDateTime.parse(object.getString("toHour"));
            shift.date = LocalDate.parse(object.getString("date").substring(0, 10));
            JSONObject typology = object.optJSONObject("hourTypology");
            shift.translation = typology == null ? object.optString("translation", "Turno") : typology.optString("translation", object.optString("translation", "Turno"));
            shift.abbreviation = typology == null ? object.optString("abbreviation", "") : typology.optString("abbreviation", object.optString("abbreviation", ""));
            shift.collaborator = first(object, "collaboratorUuid", "uuidCollaborator"); JSONObject person = object.optJSONObject("collaborator"); if (shift.collaborator.isEmpty() && person != null) shift.collaborator = person.optString("uuid", "");
            shift.collaboratorId = shift.collaborator;
            String color = typology == null ? object.optString("hourTypologyColor", "#2563EB") : typology.optString("color", object.optString("hourTypologyColor", "#2563EB"));
            try { shift.color = Color.parseColor(color); } catch (Exception ignored) { shift.color = Color.rgb(37, 99, 235); }
            if (collaborators.containsKey(shift.collaborator)) shift.collaborator = collaborators.get(shift.collaborator); else shift.collaborator = "";
            return shift;
        }
        String typeLabel() { return abbreviation.isEmpty() ? translation : abbreviation + "-" + translation; }
        String label() { String type = typeLabel(); return collaborator.isEmpty() ? type : collaborator + " · " + type; }
    }
    private static class CalendarChoice {
        final long id; final String name; final String account;
        CalendarChoice(long id, String name, String account) { this.id = id; this.name = name == null ? "Calendario Google" : name; this.account = account == null ? "" : account; }
    }
}

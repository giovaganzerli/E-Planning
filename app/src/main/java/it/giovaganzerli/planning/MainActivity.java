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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;
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
    private static final String TEAMS_API = BuildConfig.API_BASE_URL + "/lite/api/v2/teams";
    private static final String PROFILE_API = BuildConfig.API_BASE_URL + "/api/v2/actor/profile";
    private static final int NAVY = Color.rgb(36, 59, 116), INK = Color.rgb(25, 42, 74), MUTED = Color.rgb(100, 116, 139);
    private static final int CALENDAR_PERMISSION_REQUEST = 41;
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final String SELECTED_CALENDAR_ID = "selected-calendar-id";
    private static final String SELECTED_CALENDAR_NAME = "selected-calendar-name";
    private static final String EVENT_MARKER = "E-PLANNING:";
    private static final String TEAM_VIEW_PREFERENCES = "team-view";
    private static final String TEAM_VIEW_DAY_MODE = "day-mode";
    private static final String TEAM_VIEW_GROUPING = "grouping";
    private PlanningPortal planningPortal = PlanningPortal.EPLANNING;
    private EffiplanApi effiplan;
    private String effiplanPopulation = "OWN", effiplanPopulationTitle = "La mia squadra";
    private String accountScope = "", profileTeamUuid = "", profileTeamTitle = "", profilePersonnelDivision = "";
    private PortalSetupController setupController;
    private boolean configuringAccount;
    private boolean resolvingMembership;
    private int membershipGeneration;
    private SetupRules.Membership currentMembership;
    private java.util.function.Consumer<CalendarChoice> pendingCalendarSelection;
    private String pendingCalendarScope = "";
    private AlertDialog activeCalendarDialog;
    private final ArrayList<SwapSearch.Segment> effiplanAbsences = new ArrayList<>();
    private boolean updaterStarted;
    private boolean selectingPortal, switchingPortal;

    private enum Tab { WEEK, MONTH, TEAM, SEARCH, SETTINGS }
    private enum TeamGrouping { DAY, COLLABORATOR, DAILY }
    private WebView portal;
    private LinearLayout planningContainer, weekList;
    private View header, bottomNavigation, punchPreview;
    private TextView status, weekLabel, weekSummary, subtitle;
    private Button weekButton, monthButton, teamButton, searchButton, previousButton, nextButton, todayButton;
    private ImageButton appMenuButton, refreshButton, calendarSyncButton;
    private View appMenuBadge, updatesMenuBadge, updatesMenuItem;
    private TextView calendarSyncIndicator;
    private ScrollView planningScroll;
    private Spinner teamIntervalSelector, teamGroupingSelector;
    private boolean syncingTeamControls;
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
            if (destroyed || authenticated || clearingSession || selectingPortal) return;
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
    private LocalDate currentWeek = planningPortal.weekStart(LocalDate.now());
    private LocalDate currentTeamWeek = currentWeek;
    private int teamWeekCount = 1;
    private TeamGrouping teamGrouping = TeamGrouping.DAY;
    private boolean teamDayMode;
    private LocalDate currentTeamDay = LocalDate.now();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        Window window = getWindow(); window.setStatusBarColor(Color.rgb(23, 37, 84));
        header = findViewById(R.id.header); bottomNavigation = findViewById(R.id.bottomNavigation);
        portal = findViewById(R.id.portal); planningContainer = findViewById(R.id.planningContainer); weekList = findViewById(R.id.weekList); planningScroll = findViewById(R.id.planningScroll);
        status = findViewById(R.id.status); weekLabel = findViewById(R.id.weekLabel); weekSummary = findViewById(R.id.weekSummary); subtitle = findViewById(R.id.subtitle);
        refreshButton = findViewById(R.id.refresh); appMenuButton = findViewById(R.id.appMenu); previousButton = findViewById(R.id.previousPeriod); nextButton = findViewById(R.id.nextPeriod); todayButton = findViewById(R.id.today);
        appMenuBadge = findViewById(R.id.appMenuBadge);
        calendarSyncButton = findViewById(R.id.calendarSync); calendarSyncIndicator = findViewById(R.id.calendarSyncIndicator);
        calendarSyncButton.setOnClickListener(v -> confirmCalendarSync());
        punchPreview = findViewById(R.id.punchPreview);
        workday = new WorkdayController(this);
        findViewById(R.id.populationSelector).setOnClickListener(v -> chooseEffiplanPopulation());
        weekButton = findViewById(R.id.openWeek); monthButton = findViewById(R.id.openMonth); teamButton = findViewById(R.id.openTeam); searchButton = findViewById(R.id.openSearch);
        findViewById(R.id.homeIcon).setOnClickListener(v -> openHome());
        findViewById(R.id.title).setOnClickListener(v -> openHome());
        restoreTeamView();
        configureTeamControls();
        View root = findViewById(R.id.appRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        refreshButton.setOnClickListener(v -> loadCurrentTab()); appMenuButton.setOnClickListener(v -> showAppMenu()); previousButton.setOnClickListener(v -> movePeriod(-1)); nextButton.setOnClickListener(v -> movePeriod(1)); todayButton.setOnClickListener(v -> goToday());
        weekButton.setOnClickListener(v -> selectTab(Tab.WEEK)); monthButton.setOnClickListener(v -> selectTab(Tab.MONTH)); teamButton.setOnClickListener(v -> selectTab(Tab.TEAM));
        searchButton.setOnClickListener(v -> selectTab(Tab.SEARCH));
        findViewById(R.id.changeLoginPortal).setOnClickListener(v -> choosePlanningPortal(true));
        stylePortalChoice(R.id.chooseEplanning, "E-Planning", PORTAL);
        stylePortalChoice(R.id.chooseEffiplan, "Effiplan", BuildConfig.EFFIPLAN_ORIGIN);
        ((android.widget.RadioGroup) findViewById(R.id.portalChoices)).setOnCheckedChangeListener((group, checked) -> findViewById(R.id.confirmPortalChoice).setEnabled(checked != -1));
        findViewById(R.id.confirmPortalChoice).setOnClickListener(v -> confirmPlanningPortal());
        findViewById(R.id.cancelPortalChoice).setOnClickListener(v -> returnFromPortalChoice());
        renderEmpty("I turni appariranno qui", "Accedi al portale selezionato.");
        updater = new AppUpdater(this); updater.setOnAvailabilityChanged(this::updateAvailabilityBadges);
        String savedPortal = getSharedPreferences("planning-portal", MODE_PRIVATE).getString("selected", "");
        planningPortal = PlanningPortal.parse(savedPortal);
        if (savedPortal.isEmpty()) { portal.setVisibility(View.GONE); header.setVisibility(View.GONE); planningContainer.setVisibility(View.GONE); bottomNavigation.setVisibility(View.GONE); choosePlanningPortal(false); }
        else startPlanningPortal();
    }
    private void stylePortalChoice(int id, String title, String url) {
        String value = title + "\n" + url;
        SpannableString styled = new SpannableString(value);
        int start = title.length() + 1, end = value.length();
        styled.setSpan(new ForegroundColorSpan(MUTED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new RelativeSizeSpan(.72f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(id)).setText(styled);
    }
    private void openHome() {
        if (!authenticated || configuringAccount || resolvingMembership) return;
        currentWeek = weekStart(LocalDate.now());
        selectTab(Tab.WEEK);
    }
    @Override protected void onResume() { super.onResume(); if (updater != null) updater.resume(); if (workday != null) workday.resume(); }
    @Override protected void onPause() { if (workday != null) workday.pause(); if (updater != null) updater.pause(); super.onPause(); }

    private void configurePortal() {
        portal.getSettings().setJavaScriptEnabled(true); portal.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(portal, true);
        portal.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // The identity provider may land on any path of the authorised portal origin.
                // Never wait for a specific portal home URL before acquiring the stored session.
                if (!authenticated && isPortalUrl(url)) checkForSession();
            }
        });
        portal.loadUrl(portalLoginUrl());
    }
    private String portalOrigin() { return planningPortal == PlanningPortal.EPLANNING ? PORTAL : BuildConfig.EFFIPLAN_ORIGIN; }
    private String portalLoginUrl() { return portalOrigin() + (planningPortal == PlanningPortal.EFFIPLAN ? "/webquartz/ux/home" : ""); }
    private boolean isPortalUrl(String url) {
        try {
            android.net.Uri page = android.net.Uri.parse(url), expected = android.net.Uri.parse(portalOrigin());
            return "https".equals(page.getScheme()) && expected.getHost() != null && expected.getHost().equalsIgnoreCase(page.getHost())
                && expected.getPort() == page.getPort();
        } catch (Exception ignored) { return false; }
    }
    private void startPlanningPortal() {
        if (portalOrigin().isEmpty()) { choosePlanningPortal(false); return; }
        currentWeek = weekStart(LocalDate.now()); currentTeamWeek = currentWeek;
        configurePortal(); showLogin();
        if (!updaterStarted) { updaterStarted = true; updater.start(); }
    }
    private void choosePlanningPortal(boolean switching) {
        if (updater != null) updater.setAccountReady(false);
        selectingPortal = true; switchingPortal = switching; authGeneration++;
        authHandler.removeCallbacks(sessionPoll); workday.visible(false);
        header.setVisibility(View.GONE); planningContainer.setVisibility(View.GONE); bottomNavigation.setVisibility(View.GONE);
        findViewById(R.id.loginPortalBar).setVisibility(View.GONE); portal.setVisibility(View.GONE);
        findViewById(R.id.portalChoicePage).setVisibility(View.VISIBLE);
        findViewById(R.id.portalSetupPage).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.portalChoiceTitle)).setText(switching ? "Cambia portale" : "Il tuo portale di planning");
        ((TextView) findViewById(R.id.portalChoiceHelp)).setText(switching ? "Scegli il portale del tuo negozio. Cambiandolo, la sessione viene chiusa e l’app si riavvia per un nuovo accesso." : "Scegli il portale utilizzato dal tuo negozio. Potrai cambiarlo in seguito dalle Impostazioni.");
        android.widget.RadioGroup choices = findViewById(R.id.portalChoices);
        if (switching) choices.check(planningPortal == PlanningPortal.EPLANNING ? R.id.chooseEplanning : R.id.chooseEffiplan); else choices.clearCheck();
        findViewById(R.id.chooseEffiplan).setEnabled(!BuildConfig.EFFIPLAN_ORIGIN.isEmpty());
        ((Button) findViewById(R.id.confirmPortalChoice)).setText(switching ? "Cambia e riavvia" : "Continua");
        findViewById(R.id.confirmPortalChoice).setEnabled(choices.getCheckedRadioButtonId() != -1);
        findViewById(R.id.cancelPortalChoice).setVisibility(switching ? View.VISIBLE : View.GONE);
    }
    private void confirmPlanningPortal() {
        int selected = ((android.widget.RadioGroup) findViewById(R.id.portalChoices)).getCheckedRadioButtonId();
        if (!selectingPortal || selected == -1 || clearingSession) return;
        PlanningPortal choice = selected == R.id.chooseEffiplan ? PlanningPortal.EFFIPLAN : PlanningPortal.EPLANNING;
        if (choice == PlanningPortal.EFFIPLAN && BuildConfig.EFFIPLAN_ORIGIN.isEmpty()) return;
        if (switchingPortal && choice == planningPortal) { returnFromPortalChoice(); return; }
        getSharedPreferences("planning-portal", MODE_PRIVATE).edit().putString("selected", choice.name()).remove("configure-next-login").apply();
        selectingPortal = false; findViewById(R.id.portalChoicePage).setVisibility(View.GONE);
        if (switchingPortal) clearSession(this::recreate);
        else { planningPortal = choice; startPlanningPortal(); }
    }
    private void returnFromPortalChoice() {
        if (!selectingPortal || !switchingPortal) return;
        selectingPortal = false; findViewById(R.id.portalChoicePage).setVisibility(View.GONE);
        if (configuringAccount) showSetupPage();
        else if (authenticated) { showApp(); selectTab(currentTab); } else showLogin();
    }

    private void checkForSession() {
        if (destroyed || authenticated || clearingSession || selectingPortal) return;
        final int generation = authGeneration;
        String script = planningPortal == PlanningPortal.EPLANNING
            ? "(function(){if(location.origin!==" + JSONObject.quote(portalOrigin()) + ")return '';for(let storage of [localStorage,sessionStorage]){let value=storage.getItem('auth.token');if(!value)continue;try{let parsed=JSON.parse(value);value=typeof parsed==='string'?parsed:(parsed.token||parsed.accessToken||parsed.access_token||'')}catch(e){}if(typeof value==='string'&&value)return JSON.stringify({token:value})}return ''})()"
            : "(function(){if(location.origin!==" + JSONObject.quote(portalOrigin()) + ")return '';let token=localStorage.getItem('token'),role=localStorage.getItem('roleId'),user=localStorage.getItem('userId');return token&&role&&user?JSON.stringify({token:token,role:role,user:user}):''})()";
        portal.evaluateJavascript(script, result -> {
            if (destroyed || authenticated || clearingSession || selectingPortal || generation != authGeneration) return;
            String found = decodeJs(result);
            if (planningPortal == PlanningPortal.EFFIPLAN) {
                try {
                    JSONObject session = new JSONObject(found);
                    effiplan = new EffiplanApi(portalOrigin(), session.getString("token"), session.getString("role"), session.getString("user"));
                    token = session.getString("token");
                } catch (Exception ignored) { return; }
            } else {
                try { token = new JSONObject(found).getString("token"); } catch (Exception ignored) { return; }
                if (!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) return;
            }
            authenticated = true;
            weekReady = false; workday.visible(false); weekList.removeAllViews();
            calendarSyncButton.setVisibility(View.GONE); calendarSyncButton.setEnabled(false); calendarSyncIndicator.setVisibility(View.GONE);
            punchPreview.setVisibility(View.GONE); status.setVisibility(View.GONE);
            findViewById(R.id.loginPortalBar).setVisibility(View.GONE); portal.setVisibility(View.GONE);
            header.setVisibility(View.VISIBLE); planningContainer.setVisibility(View.VISIBLE); bottomNavigation.setVisibility(View.GONE);
            loadProfile();
        });
    }

    private void showLogin() {
        if (updater != null) updater.setAccountReady(false);
        selectingPortal = false; findViewById(R.id.portalChoicePage).setVisibility(View.GONE);
        findViewById(R.id.loginPortalBar).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.loginPortalName)).setText("Accesso · " + planningPortal.label);
        header.setVisibility(View.GONE); planningContainer.setVisibility(View.GONE); bottomNavigation.setVisibility(View.GONE); portal.setVisibility(View.VISIBLE);
        authHandler.removeCallbacks(sessionPoll);
        if (!destroyed && !clearingSession && !authenticated) authHandler.post(sessionPoll);
    }

    private void showApp() {
        if (configuringAccount) { showSetupPage(); return; }
        if (authenticated && !accountScope.isEmpty() && currentMembership == null) { profileReady(); return; }
        selectingPortal = false; findViewById(R.id.portalChoicePage).setVisibility(View.GONE);
        findViewById(R.id.loginPortalBar).setVisibility(View.GONE);
        authHandler.removeCallbacks(sessionPoll);
        header.setVisibility(View.VISIBLE); planningContainer.setVisibility(View.VISIBLE); bottomNavigation.setVisibility(View.VISIBLE); portal.setVisibility(View.GONE);
        subtitle.setText("Portale: " + planningPortal.label);
        if (updater != null) updater.setAccountReady(authenticated && !accountScope.isEmpty() && currentMembership != null && !resolvingMembership);
    }
    private void showAppMenu() {
        LinearLayout menu = new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL); menu.setPadding(dp(6), dp(6), dp(6), dp(6)); menu.setBackgroundResource(R.drawable.bg_popup_menu);
        PopupWindow popup = new PopupWindow(menu, dp(244), -2, true); popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); popup.setOutsideTouchable(true); popup.setElevation(dp(10));
        menu.addView(menuAction("Impostazioni", R.drawable.ic_settings, () -> selectTab(Tab.SETTINGS)));
        updatesMenuItem = menuAction("Aggiornamenti…", R.drawable.ic_update, () -> updater.open());
        menu.addView(updatesMenuItem);
        updateAvailabilityBadges();
        View divider = new View(this); divider.setBackgroundColor(Color.rgb(203, 213, 225)); menu.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        menu.addView(menuAction("Logout", R.drawable.ic_logout, this::clearSession));
        popup.setOnDismissListener(() -> { updatesMenuBadge = null; updatesMenuItem = null; });
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
        if (icon == R.drawable.ic_update) {
            updatesMenuBadge = new View(this); updatesMenuBadge.setBackgroundResource(R.drawable.bg_notification_dot);
            updatesMenuBadge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(10), dp(10)); badgeParams.leftMargin = dp(10);
            item.addView(updatesMenuBadge, badgeParams);
        }
        item.setMinimumHeight(dp(50)); return item;
    }
    private void updateAvailabilityBadges() {
        boolean available = updater != null && updater.hasUpdate();
        appMenuBadge.setVisibility(available ? View.VISIBLE : View.GONE);
        appMenuButton.setContentDescription(available ? "Apri menu · aggiornamento disponibile" : "Apri menu");
        if (updatesMenuBadge != null) updatesMenuBadge.setVisibility(available ? View.VISIBLE : View.GONE);
        if (updatesMenuItem != null) updatesMenuItem.setContentDescription(available ? "Aggiornamenti · nuova versione disponibile" : "Aggiornamenti");
    }

    private void clearSession() { clearSession(null); }
    private void clearSession(Runnable after) {
        if (destroyed || clearingSession) return;
        if (updater != null) updater.setAccountReady(false);
        clearingSession = true; final int generation = ++authGeneration; authHandler.removeCallbacksAndMessages(null);
        if (setupController != null) setupController.destroy();
        setupController = null; configuringAccount = false; resolvingMembership = false; currentMembership = null; accountScope = "";
        profileTeamUuid = ""; profileTeamTitle = ""; profilePersonnelDivision = "";
        pendingCalendarSelection = null; pendingCalendarScope = "";
        if (activeCalendarDialog != null) activeCalendarDialog.dismiss();
        findViewById(R.id.portalSetupPage).setVisibility(View.GONE);
        requestGeneration++; authenticated = false; token = ""; collaboratorUuid = ""; teamUuid = ""; collaboratorName = ""; shifts.clear(); collaborators.clear(); weekList.removeAllViews(); searchDay = LocalDate.now(); searchSelectedShifts.clear();
        weekReady = false; calendarSyncButton.setEnabled(false); calendarSyncButton.setVisibility(View.GONE); calendarSyncIndicator.setVisibility(View.GONE);
        workday.session("", "");
        effiplan = null; effiplanAbsences.clear();
        effiplanPopulation = "OWN"; effiplanPopulationTitle = "La mia squadra";
        currentMonth = LocalDate.now().withDayOfMonth(1); currentWeek = weekStart(LocalDate.now()); currentTeamWeek = currentWeek; currentTeamDay = LocalDate.now();
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
                    cookies.flush(); clearingSession = false;
                    if (after != null) after.run();
                    else { portal.loadUrl(portalLoginUrl()); showLogin(); }
                });
            }
        };
        portal.evaluateJavascript("(function(){try{localStorage.clear();sessionStorage.clear()}catch(e){}return true})()", ignored -> finish.run());
        authHandler.postDelayed(finish, 1500);
    }

    private void selectTab(Tab tab) {
        if (configuringAccount) { showSetupPage(); return; }
        if (resolvingMembership) return;
        if (tab == Tab.TEAM && currentTab != Tab.TEAM) openTeamToday();
        currentTab = tab; updateNavigation(); loadCurrentTab();
    }
    private void openTeamToday() {
        currentTeamDay = LocalDate.now();
        currentTeamWeek = weekStart(currentTeamDay);
    }
    private void restoreTeamView() {
        android.content.SharedPreferences preferences = getSharedPreferences(TEAM_VIEW_PREFERENCES, MODE_PRIVATE);
        teamDayMode = false;
        try { teamGrouping = TeamGrouping.valueOf(preferences.getString(TEAM_VIEW_GROUPING, TeamGrouping.DAY.name())); }
        catch (Exception ignored) { teamGrouping = TeamGrouping.DAY; }
    }
    private void saveTeamView() {
        getSharedPreferences(TEAM_VIEW_PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(TEAM_VIEW_DAY_MODE, teamDayMode).putString(TEAM_VIEW_GROUPING, teamGrouping.name()).apply();
    }
    private void configureTeamControls() {
        teamIntervalSelector = findViewById(R.id.teamIntervalSelector); teamGroupingSelector = findViewById(R.id.teamGroupingSelector);
        ((View) teamIntervalSelector.getParent()).setVisibility(View.GONE);
        ArrayAdapter<String> intervals = new ArrayAdapter<>(this, R.layout.team_spinner_item, new String[] { "Giorno ▾", "Settimana ▾" });
        ArrayAdapter<String> groupings = new ArrayAdapter<>(this, R.layout.team_spinner_item, new String[] { "Per ora ▾", "Per collaboratore ▾", "Per giornata ▾" });
        intervals.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); groupings.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        teamIntervalSelector.setAdapter(intervals); teamGroupingSelector.setAdapter(groupings); syncTeamControls();
        teamIntervalSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingTeamControls || teamDayMode == (position == 0)) return;
                teamDayMode = position == 0; openTeamToday(); saveTeamView(); if (currentTab == Tab.TEAM) loadCurrentTab();
            }
        });
        teamGroupingSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TeamGrouping[] values = { TeamGrouping.DAY, TeamGrouping.COLLABORATOR, TeamGrouping.DAILY };
                if (syncingTeamControls || teamGrouping == values[position]) return;
                teamGrouping = values[position]; saveTeamView(); if (currentTab == Tab.TEAM) renderTeam(currentTeamWeek);
            }
        });
    }
    private void syncTeamControls() {
        if (teamIntervalSelector == null) return;
        syncingTeamControls = true;
        teamIntervalSelector.setSelection(teamDayMode ? 0 : 1, false);
        TeamGrouping[] values = { TeamGrouping.DAY, TeamGrouping.COLLABORATOR, TeamGrouping.DAILY };
        for (int i = 0; i < values.length; i++) if (values[i] == teamGrouping) teamGroupingSelector.setSelection(i, false);
        syncingTeamControls = false;
    }
    private void movePeriod(int direction) {
        if (currentTab == Tab.SEARCH) { searchDay = searchDay.plusWeeks(direction); searchSelectedShifts.clear(); loadCurrentTab(); return; }
        if (currentTab == Tab.MONTH) currentMonth = currentMonth.plusMonths(direction);
        else if (currentTab == Tab.TEAM) { if (teamDayMode) { currentTeamDay = currentTeamDay.plusDays(direction); currentTeamWeek = weekStart(currentTeamDay); } else currentTeamWeek = currentTeamWeek.plusWeeks(direction); }
        else currentWeek = currentWeek.plusWeeks(direction);
        loadCurrentTab();
    }
    private void goToday() { LocalDate start = weekStart(LocalDate.now()); if (currentTab == Tab.SEARCH) { searchDay = LocalDate.now(); searchSelectedShifts.clear(); } else if (currentTab == Tab.MONTH) currentMonth = LocalDate.now().withDayOfMonth(1); else if (currentTab == Tab.TEAM) { currentTeamWeek = start; currentTeamDay = LocalDate.now(); } else currentWeek = start; loadCurrentTab(); }
    private void updateNavigation() {
        findViewById(R.id.periodSummary).setVisibility(currentTab == Tab.SETTINGS ? View.GONE : View.VISIBLE);
        punchPreview.setVisibility(currentTab == Tab.WEEK ? View.VISIBLE : View.GONE);
        workday.visible(currentTab == Tab.WEEK);
        findViewById(R.id.teamControls).setVisibility(currentTab == Tab.TEAM ? View.VISIBLE : View.GONE);
        if (currentTab == Tab.TEAM) syncTeamControls();
        updatePopulationSelector();
        Button[] buttons = { weekButton, monthButton, teamButton, searchButton };
        Tab[] tabs = { Tab.WEEK, Tab.MONTH, Tab.TEAM, Tab.SEARCH };
        for (int i = 0; i < buttons.length; i++) {
            boolean selected = tabs[i] == currentTab;
            buttons[i].setBackgroundResource(selected ? R.drawable.bg_nav_selected : R.drawable.bg_nav);
            buttons[i].setTextColor(selected ? Color.rgb(30, 64, 175) : MUTED);
        }
    }

    private void loadCurrentTab() {
        if (configuringAccount) { showSetupPage(); return; }
        if (resolvingMembership) return;
        if (!authenticated || token.isEmpty()) { showLogin(); return; }
        if (currentTab == Tab.WEEK) workday.refresh();
        weekReady = false; planningReadable = true; calendarSyncIndicator.setVisibility(View.GONE);
        calendarSyncButton.setVisibility(currentTab == Tab.WEEK ? View.VISIBLE : View.GONE); calendarSyncButton.setEnabled(false);
        requestGeneration++; refreshButton.setEnabled(false); shifts.clear(); collaborators.clear(); weekList.removeAllViews(); status.setVisibility(View.GONE);
        weekLabel.setText(currentTab == Tab.TEAM ? "Squadra · " + shortRange(currentTeamWeek) : currentTab == Tab.SEARCH ? "Cerca cambio" : currentTab == Tab.SETTINGS ? "Impostazioni" : "Caricamento planning…");
        weekSummary.setText("");
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
        if (planningPortal == PlanningPortal.EFFIPLAN) {
            requestEffiplan(api -> api.profile().toString(), raw -> {
                try {
                    JSONObject profile = new JSONObject(raw);
                    collaboratorUuid = profile.getString("id"); collaboratorName = profile.getString("name"); teamUuid = "";
                    workday.effiplanSession(effiplan); profileReady();
                } catch (Exception e) { showLoadError("Impossibile leggere il profilo Effiplan."); }
            });
            return;
        }
        request(PROFILE_API, raw -> {
            try {
                JSONObject profile = new JSONObject(raw); JSONObject person = profile.getJSONObject("collaborator");
                String uuid = person.getString("uuid");
                if (!validUuid(uuid)) throw new Exception("Identificativo collaboratore mancante");
                JSONObject condition = SetupData.activeCondition(person);
                JSONObject team = condition == null ? null : condition.optJSONObject("team");
                String resolvedTeam = team == null ? "" : team.optString("uuid", "");
                collaboratorUuid = uuid; teamUuid = validUuid(resolvedTeam) ? resolvedTeam : "";
                profileTeamUuid = teamUuid; profileTeamTitle = team == null ? "" : team.optString("name", team.optString("description", "Squadra del profilo"));
                JSONObject site = condition == null ? null : condition.optJSONObject("site");
                profilePersonnelDivision = site == null ? "" : site.optString("personnelDivision", "");
                workday.session(token, collaboratorUuid);
                collaboratorName = (person.optString("name", "") + " " + person.optString("firstLastName", "")).trim();
                profileReady();
            } catch (Exception e) { showLoadError("Impossibile identificare il collaboratore dal profilo ePlanning. Premi aggiorna per riprovare."); }
        });
    }
    private void profileReady() {
        accountScope = effiplan == null ? EffiplanRules.scopeKey(BuildConfig.API_BASE_URL, collaboratorUuid) : effiplan.scopeKey();
        resolvingMembership = true; weekLabel.setText("Verifica squadra di appartenenza…"); weekSummary.setText("");
        loadOwnMembership(accountScope, actual -> {
            resolvingMembership = false; currentMembership = actual;
            SetupRules.Draft saved = setupDraft();
            boolean transferred = SetupRules.transferred(saved.teamKey, actual.team.key);
            saved.membership(actual);
            if (SetupRules.requiresSetup(calendarSettings().getInt("setup-version", 0), saved, transferred)) { openAccountSetup(false); return; }
            calendarSettings().edit().putString("own-team-title", actual.team.title)
                .putBoolean("own-team-manual", actual.manual).putString("detected-team-key", actual.detectedTeamKey).apply();
            applyOwnTeam(saved); showApp(); selectTab(Tab.WEEK);
        }, error -> {
            resolvingMembership = false; currentMembership = null; openAccountSetup(false);
            if (setupController != null) setupController.error(error);
        });
    }
    private String ownTeamKey() { return calendarSettings().getString("own-team-key", profileTeamUuid); }
    private String ownTeamTitle() { return calendarSettings().getString("own-team-title", profileTeamTitle); }
    private void applyOwnTeam(SetupRules.Draft draft) {
        if (planningPortal == PlanningPortal.EPLANNING) teamUuid = draft.teamKey;
        effiplanPopulation = "OWN"; effiplanPopulationTitle = "La mia squadra · " + draft.teamTitle;
    }
    private SetupRules.Draft setupDraft() {
        android.content.SharedPreferences prefs = calendarSettings();
        SetupRules.Draft draft = new SetupRules.Draft();
        draft.teamKey = ownTeamKey(); draft.teamTitle = ownTeamTitle();
        draft.manualTeam = prefs.getBoolean("own-team-manual", false);
        draft.detectedTeamKey = prefs.getString("detected-team-key", "");
        JSONObject savedRoles = storedSkillsFor(draft.teamKey);
        java.util.Iterator<String> people = savedRoles.keys();
        while (people.hasNext()) { String id = people.next(); draft.roles.put(id, SetupRules.normalizeRoles(savedRoles.optInt(id, 0))); }
        draft.calendarId = prefs.getLong(SELECTED_CALENDAR_ID, -1); draft.calendarName = prefs.getString(SELECTED_CALENDAR_NAME, "");
        draft.calendarEnabled = prefs.getBoolean("calendar-sync-enabled", draft.calendarId >= 0);
        for (int i = 0; i < 2; i++) {
            draft.reminderEnabled[i] = prefs.getBoolean("reminder-enabled-" + i, true);
            draft.reminderMinutes[i] = prefs.getInt("reminder-minutes-" + i, i == 0 ? 1440 : 60);
        }
        return draft;
    }
    private void showSetupPage() {
        if (updater != null) updater.setAccountReady(false);
        header.setVisibility(View.GONE); planningContainer.setVisibility(View.GONE); bottomNavigation.setVisibility(View.GONE);
        portal.setVisibility(View.GONE); findViewById(R.id.loginPortalBar).setVisibility(View.GONE);
        findViewById(R.id.portalChoicePage).setVisibility(View.GONE);
        findViewById(R.id.portalSetupPage).setVisibility(View.VISIBLE);
        workday.visible(false);
    }
    private void openAccountSetup(boolean editing) {
        if (!authenticated || accountScope.isEmpty()) return;
        if (setupController != null) setupController.destroy();
        // Opening an editor or switching portals must not invalidate a saved configuration.
        requestGeneration++; configuringAccount = true; showSetupPage();
        final String scope = accountScope;
        setupController = new PortalSetupController(this, planningPortal.label, setupDraft(), editing, currentMembership, new PortalSetupController.Host() {
            public void loadMembership(java.util.function.Consumer<SetupRules.Membership> success, java.util.function.Consumer<String> failure) {
                loadOwnMembership(scope, actual -> { currentMembership = actual; success.accept(actual); }, failure);
            }
            public void selectTeamManually(Runnable checking, java.util.function.Consumer<SetupRules.Membership> success, java.util.function.Consumer<String> failure) {
                chooseSetupTeam(scope, () -> { currentMembership = null; checking.run(); },
                    actual -> { currentMembership = actual; success.accept(actual); },
                    message -> { currentMembership = null; failure.accept(message); });
            }
            public void configureRoles(SetupRules.Draft draft, Runnable changed) {
                TeamRolesDialog.show(MainActivity.this, draft.members, collaboratorUuid, draft.roles, roles -> {
                    if (!configuringAccount || !scope.equals(accountScope)) return;
                    draft.roles.clear(); draft.roles.putAll(roles); changed.run();
                });
            }
            public void chooseCalendar(java.util.function.Consumer<CalendarChoice> selected) { chooseGoogleCalendar(selected); }
            public boolean calendarPermissionGranted() { return hasCalendarPermission(); }
            public void finish(SetupRules.Draft draft) {
                if (!authenticated || !scope.equals(accountScope) || !configuringAccount || !SetupRules.valid(draft)
                    || currentMembership == null || !currentMembership.team.key.equals(draft.teamKey)) return;
                if (draft.calendarEnabled && !hasCalendarPermission()) { setupController.error("Consenti l’accesso al calendario o disattiva la sincronizzazione."); return; }
                android.content.SharedPreferences.Editor editor = calendarSettings().edit()
                    .putString("own-team-key", draft.teamKey).putString("own-team-title", draft.teamTitle)
                    .putBoolean("own-team-manual", draft.manualTeam).putString("detected-team-key", draft.detectedTeamKey)
                    .putBoolean("calendar-sync-enabled", draft.calendarEnabled)
                    .putLong(SELECTED_CALENDAR_ID, draft.calendarId).putString(SELECTED_CALENDAR_NAME, draft.calendarName)
                    .putString("own-team-roles", new JSONObject(draft.roles).toString())
                    .remove("setup-pending").putInt("setup-version", SetupRules.VERSION);
                for (int i = 0; i < 2; i++) editor.putBoolean("reminder-enabled-" + i, draft.reminderEnabled[i]).putInt("reminder-minutes-" + i, draft.reminderMinutes[i]);
                if (!editor.commit()) { setupController.error("Impossibile salvare la configurazione. Riprova."); return; }
                getSharedPreferences("planning-portal", MODE_PRIVATE).edit().remove("configure-next-login").commit();
                applyOwnTeam(draft); closeAccountSetup(); showApp(); selectTab(editing ? Tab.SETTINGS : Tab.WEEK);
            }
            public void cancel() {
                if (!editing || !scope.equals(accountScope)) return;
                if (currentMembership != null && !currentMembership.team.key.equals(ownTeamKey())) {
                    setupController.error("Il portale indica una nuova squadra. Conferma la configurazione aggiornata per continuare."); return;
                }
                calendarSettings().edit().remove("setup-pending").commit();
                closeAccountSetup(); showApp(); selectTab(Tab.SETTINGS);
            }
            public void changePortal() { if (scope.equals(accountScope)) choosePlanningPortal(true); }
        });
    }
    private void closeAccountSetup() {
        if (setupController != null) setupController.destroy();
        setupController = null; configuringAccount = false;
        findViewById(R.id.portalSetupPage).setVisibility(View.GONE);
        collaborators.clear(); shifts.clear(); effiplanAbsences.clear(); searchSelectedShifts.clear();
    }
    private Object readEplanning(String endpoint, String bearer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setRequestProperty("Authorization", "Bearer " + bearer); connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code == 401) throw new EffiplanApi.AuthenticationException();
            if (code < 200 || code >= 300) throw new Exception("Lettura non autorizzata o non disponibile");
            try (InputStream input = connection.getInputStream()) { return new JSONTokener(read(input)).nextValue(); }
        } finally { connection.disconnect(); }
    }
    private void loadOwnMembership(String scope, java.util.function.Consumer<SetupRules.Membership> success, java.util.function.Consumer<String> failure) {
        final EffiplanApi client = effiplan; final String bearer = token, user = collaboratorUuid, name = collaboratorName;
        final int generation = authGeneration;
        final int membershipRequest = ++membershipGeneration;
        final SetupRules.Draft saved = setupDraft();
        final PlanningPortal selectedPortal = planningPortal;
        executor.execute(() -> {
            try {
                SetupRules.Team team; Map<String, String> members; String siteKey = ""; String detected = ""; boolean manual = false;
                LocalDate start = selectedPortal.weekStart(LocalDate.now());
                if (client != null) {
                    try { team = client.membership(); detected = team.key; }
                    catch (EffiplanApi.AuthenticationException expired) { throw expired; }
                    catch (Exception unavailable) {
                        if (!saved.manualTeam || !SetupRules.explicitTeam(saved.teamKey)) throw unavailable;
                        team = new SetupRules.Team(saved.teamKey, saved.teamTitle); manual = true; detected = saved.detectedTeamKey;
                    }
                    if (saved.manualTeam && detected.equals(saved.detectedTeamKey)) {
                        team = new SetupRules.Team(saved.teamKey, saved.teamTitle); manual = true;
                    }
                    members = client.membershipRoster(team, start);
                } else {
                    JSONObject profile = (JSONObject) readEplanning(PROFILE_API, bearer);
                    team = SetupData.eplanningMembership(profile, user);
                    detected = team.key;
                    JSONObject condition = SetupData.activeCondition(profile.getJSONObject("collaborator"));
                    JSONObject site = condition == null ? null : condition.optJSONObject("site");
                    siteKey = site == null ? "" : site.optString("personnelDivision");
                    if (saved.manualTeam && detected.equals(saved.detectedTeamKey)) {
                        team = new SetupRules.Team(saved.teamKey, saved.teamTitle); manual = true;
                    }
                    Object roster = readEplanning(TEAM_MEMBERS_API + "?filter[teamUuid]=" + team.key + "&filter[startDateInterval]=" + start + "&filter[withPolyvalences]=true", bearer);
                    members = SetupData.eplanningMembers(roster);
                }
                if (members.isEmpty()) throw new Exception("Elenco collaboratori non disponibile");
                if (manual) SetupRules.requireMember(members, user);
                if (!members.containsKey(user)) members.put(user, name.isEmpty() ? "Tu" : name);
                SetupRules.Membership actual = new SetupRules.Membership(team, members, siteKey, manual, detected);
                runOnUiThread(() -> {
                    if (destroyed || !authenticated || generation != authGeneration || membershipRequest != membershipGeneration || !scope.equals(accountScope) || selectingPortal) return;
                    if (client == null) { profileTeamUuid = actual.detectedTeamKey; profilePersonnelDivision = actual.siteKey; }
                    success.accept(actual);
                });
            } catch (EffiplanApi.AuthenticationException expired) {
                runOnUiThread(() -> { if (!destroyed && authenticated && generation == authGeneration && membershipRequest == membershipGeneration && scope.equals(accountScope)) clearSession(); });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!destroyed && authenticated && generation == authGeneration && membershipRequest == membershipGeneration && scope.equals(accountScope) && !selectingPortal)
                        failure.accept("Il portale non ha consentito di riconoscere una squadra univoca. Usa Seleziona squadra manualmente per scegliere tra le squadre autorizzate.");
                });
            }
        });
    }
    private void chooseSetupTeam(String scope, Runnable checking, java.util.function.Consumer<SetupRules.Membership> success, java.util.function.Consumer<String> failure) {
        final EffiplanApi client = effiplan;
        final String bearer = token, division = profilePersonnelDivision, user = collaboratorUuid, name = collaboratorName;
        final int auth = authGeneration, request = membershipGeneration;
        final PlanningPortal selectedPortal = planningPortal;
        final String detected = currentMembership == null ? calendarSettings().getString("detected-team-key", profileTeamUuid) : currentMembership.detectedTeamKey;
        resolvingMembership = false;
        executor.execute(() -> {
            try {
                List<SetupRules.Team> teams = client != null ? SetupData.effiplanTeams(client.populations())
                    : division.isEmpty() ? new ArrayList<>() : SetupData.eplanningTeams(readEplanning(TEAMS_API + "?personnelDivision=" + java.net.URLEncoder.encode(division, "UTF-8"), bearer));
                if (client == null && validUuid(profileTeamUuid) && teams.stream().noneMatch(t -> t.key.equals(profileTeamUuid)))
                    teams.add(0, new SetupRules.Team(profileTeamUuid, profileTeamTitle));
                runOnUiThread(() -> {
                    if (destroyed || !authenticated || !configuringAccount || !scope.equals(accountScope) || auth != authGeneration || request != membershipGeneration) return;
                    if (teams.isEmpty()) { failure.accept("Nessuna squadra disponibile per questo profilo."); return; }
                    LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
                    android.widget.EditText filter = new android.widget.EditText(this); filter.setSingleLine(true); filter.setHint("Cerca squadra"); panel.addView(filter);
                    ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
                    panel.addView(scroll, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * .45f)));
                    AlertDialog dialog = new AppDialogs.Builder(this).setTitle("Seleziona squadra manualmente").setView(panel).setNegativeButton("Annulla", null).create();
                    for (SetupRules.Team team : teams) {
                        Button row = compactButton(team.title); row.setAllCaps(false); row.setTag(team.title.toLowerCase(Locale.ITALIAN)); list.addView(row);
                        row.setOnClickListener(v -> {
                            dialog.dismiss();
                            if (destroyed || !authenticated || !configuringAccount || !scope.equals(accountScope) || auth != authGeneration || request != membershipGeneration) return;
                            final int selectedRequest = ++membershipGeneration;
                            checking.run();
                            executor.execute(() -> {
                                try {
                                    LocalDate start = selectedPortal.weekStart(LocalDate.now());
                                    Map<String, String> members = client != null ? client.membershipRoster(team, start)
                                        : SetupData.eplanningMembers(readEplanning(TEAM_MEMBERS_API + "?filter[teamUuid]=" + team.key + "&filter[startDateInterval]=" + start + "&filter[withPolyvalences]=true", bearer));
                                    if (members.isEmpty()) throw new Exception("Collaboratori non disponibili");
                                    SetupRules.requireMember(members, user);
                                    SetupRules.Membership selected = new SetupRules.Membership(team, members, division, true, detected);
                                    runOnUiThread(() -> {
                                        if (!destroyed && authenticated && configuringAccount && scope.equals(accountScope) && auth == authGeneration && selectedRequest == membershipGeneration) success.accept(selected);
                                    });
                                } catch (IllegalArgumentException notMember) {
                                    runOnUiThread(() -> { if (!destroyed && configuringAccount && scope.equals(accountScope) && auth == authGeneration && selectedRequest == membershipGeneration) failure.accept("Non risulti tra i membri della squadra selezionata. Seleziona la tua squadra."); });
                                } catch (Exception error) {
                                    runOnUiThread(() -> { if (!destroyed && configuringAccount && scope.equals(accountScope) && auth == authGeneration && selectedRequest == membershipGeneration) failure.accept("Impossibile leggere i colleghi della squadra selezionata."); });
                                }
                            });
                        });
                    }
                    filter.addTextChangedListener(new android.text.TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            String query = s.toString().trim().toLowerCase(Locale.ITALIAN);
                            for (int i = 0; i < list.getChildCount(); i++) { View row = list.getChildAt(i); row.setVisibility(row.getTag().toString().contains(query) ? View.VISIBLE : View.GONE); }
                        }
                        public void afterTextChanged(android.text.Editable s) { }
                    });
                    dialog.show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!destroyed && configuringAccount && scope.equals(accountScope) && auth == authGeneration && request == membershipGeneration) failure.accept("Impossibile leggere le squadre autorizzate dal portale."); });
            }
        });
    }
    private void loadBrowseTeams(java.util.function.Consumer<List<SetupRules.Team>> success) {
        final String scope = accountScope, division = profilePersonnelDivision, bearer = token;
        final int generation = requestGeneration;
        executor.execute(() -> {
            try {
                List<SetupRules.Team> teams = division.isEmpty() ? new ArrayList<>() : SetupData.eplanningTeams(readEplanning(TEAMS_API + "?personnelDivision=" + java.net.URLEncoder.encode(division, "UTF-8"), bearer));
                runOnUiThread(() -> { if (!destroyed && authenticated && scope.equals(accountScope) && generation == requestGeneration && currentTab == Tab.TEAM) success.accept(teams); });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!destroyed && authenticated && scope.equals(accountScope) && generation == requestGeneration && currentTab == Tab.TEAM) {
                        success.accept(new ArrayList<>());
                        Toast.makeText(this, "Elenco squadre non disponibile. Puoi tornare alla tua squadra.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    private boolean validUuid(String uuid) { return uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"); }
    private void showLoadError(String message) {
        refreshButton.setEnabled(true); status.setVisibility(View.GONE); weekList.removeAllViews();
        LinearLayout error = new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(Gravity.CENTER);
        error.setPadding(dp(28), dp(40), dp(28), dp(40)); error.setBackgroundResource(R.drawable.bg_error_card);
        TextView title = text("Non riesco a caricare il planning", 20, Color.rgb(153, 27, 27), true); title.setGravity(Gravity.CENTER); error.addView(title);
        TextView description = text(message, 14, Color.rgb(127, 29, 29), false); description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2); descriptionParams.topMargin = dp(10); error.addView(description, descriptionParams);
        Button retry = compactButton("Riprova"); retry.setAllCaps(false); retry.setTextColor(Color.WHITE); retry.setBackgroundResource(R.drawable.bg_error_retry);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-1, dp(48)); retryParams.topMargin = dp(22); error.addView(retry, retryParams);
        retry.setOnClickListener(v -> loadCurrentTab());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2); cardParams.topMargin = dp(8); weekList.addView(error, cardParams);
        weekList.post(() -> {
            View parent = (View) weekList.getParent();
            if (weekList.getChildCount() == 1 && parent != null) error.setMinimumHeight(Math.max(dp(260), parent.getHeight() - dp(24)));
        });
    }

    private void loadWeek() {
        status.setText("Aggiornamento planning settimanale…");
        if (planningPortal == PlanningPortal.EFFIPLAN) {
            final LocalDate start = currentWeek;
            requestEffiplan(api -> api.planning(start, start.plusDays(6)).toString(), raw -> { parseShifts(raw); renderWeek(); complete("Planning Effiplan aggiornato"); });
            return;
        }
        // The E‑Planning weekly endpoint only exposes remaining shifts of the current week.
        // The monthly endpoint is complete, so use it for every personal weekly view.
        loadWeekFromMonths(false);
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
        if (planningPortal == PlanningPortal.EFFIPLAN) {
            final LocalDate month = currentMonth;
            requestEffiplan(api -> api.planning(month, month.plusMonths(1).minusDays(1)).toString(), raw -> { parseShifts(raw); renderMonth(); complete("Planning Effiplan aggiornato"); });
            return;
        }
        String url = MONTHLY_API + "?filter[month]=" + currentMonth + "&filter[collaboratorUuid]=" + collaboratorUuid;
        request(url, raw -> { parseShifts(raw); renderMonth(); complete("Planning mensile aggiornato"); });
    }

    private void loadTeam() {
        if (planningPortal == PlanningPortal.EPLANNING) teamDayMode = "STORE".equals(effiplanPopulation);
        teamUuid = "OWN".equals(effiplanPopulation) ? ownTeamKey() : effiplanPopulation;
        if (planningPortal == PlanningPortal.EFFIPLAN) {
            loadEffiplanTeam(currentTeamWeek, currentTeamWeek.plusWeeks(teamWeekCount).minusDays(1), false, false); return;
        }
        if (teamUuid.isEmpty()) { showLoadError("Nessuna squadra associata al tuo profilo ePlanning."); return; }
        if (profilePersonnelDivision.isEmpty()) { showLoadError("Negozio non disponibile nel profilo E‑Planning."); return; }
        String selectedTeam = teamDayMode ? null : "OWN".equals(effiplanPopulation) ? ownTeamTitle() : effiplanPopulationTitle;
        loadGlobalTeamDay(teamDayMode ? currentTeamDay : currentTeamWeek, teamDayMode ? 1 : 7, 0, selectedTeam);
    }
    private void loadGlobalTeamDay(LocalDate start, int count, int index, String selectedTeam) {
        if (index >= count) {
            deduplicateShifts(); shifts.sort(Comparator.comparing(s -> s.from)); renderTeam(currentTeamWeek); complete("Planning squadra aggiornato"); return;
        }
        LocalDate day = start.plusDays(index);
        status.setText("Caricamento planning globale…");
        String endpoint = API + "planning/planningGlobalByCollaboratorProfile?filter[personnelDivision]="
            + android.net.Uri.encode(profilePersonnelDivision) + "&filter[date]=" + day;
        request(endpoint, raw -> {
            try {
                JSONObject data = SetupData.eplanningGlobalDay(new JSONArray(raw), selectedTeam, day);
                JSONObject members = data.getJSONObject("members");
                Iterator<String> ids = members.keys(); while (ids.hasNext()) { String id = ids.next(); collaborators.put(id, members.getString(id)); }
                collectShifts(data.getJSONArray("shifts"));
            } catch (Exception invalid) { showLoadError("Planning globale della squadra non leggibile."); return; }
            loadGlobalTeamDay(start, count, index + 1, selectedTeam);
        });
    }
    private void loadTeamWeeks(int index) {
        if (index >= teamWeekCount) { shifts.sort(Comparator.comparing(s -> s.from)); renderTeam(currentTeamWeek); complete("Planning squadra aggiornato"); return; }
        LocalDate start = currentTeamWeek.plusWeeks(index);
        request(teamPlanningEndpoint(start), raw -> { appendShifts(raw); loadTeamWeeks(index + 1); });
    }
    private String teamPlanningEndpoint(LocalDate start) {
        return teamUuid.equals(profileTeamUuid) ? TEAM_API + "?filter[startDateInterval]=" + start
            : API + "planning/weekTeamByManager?filter[startDateInterval]=" + start + "&filter[teamUuid]=" + teamUuid;
    }

    private void loadSwapPlanning() {
        teamUuid = ownTeamKey();
        if (planningPortal == PlanningPortal.EFFIPLAN) {
            LocalDate monday = weekStart(searchDay);
            loadEffiplanTeam(monday, monday.plusDays(6), true, false); return;
        }
        weekLabel.setText("Cerca cambio · W" + weekNumber(searchDay)); weekSummary.setText("Caricamento squadra…");
        if (teamUuid.isEmpty()) { showLoadError("Nessuna squadra associata al tuo profilo."); return; }
        LocalDate monday = weekStart(searchDay);
        request(TEAM_MEMBERS_API + "?filter[teamUuid]=" + teamUuid + "&filter[startDateInterval]=" + monday + "&filter[withPolyvalences]=true", raw -> {
            try {
                collaborators.clear(); collectCollaborators(new JSONTokener(raw).nextValue());
                if (collaborators.isEmpty()) throw new Exception();
                if (!collaborators.containsKey(collaboratorUuid)) collaborators.put(collaboratorUuid, collaboratorName.isEmpty() ? "Tu" : collaboratorName);
                request(teamPlanningEndpoint(monday), planning -> {
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
        teamUuid = ownTeamKey();
        weekLabel.setText("Impostazioni"); weekSummary.setText("Caricamento configurazione…");
        renderSettings();
        if (planningPortal == PlanningPortal.EFFIPLAN) {
            LocalDate monday = weekStart(LocalDate.now());
            loadEffiplanTeam(monday, monday.plusDays(6), false, true); return;
        }
        if (teamUuid.isEmpty()) { renderSettings(); return; }
        LocalDate monday = weekStart(LocalDate.now());
        String membersUrl = TEAM_MEMBERS_API + "?filter[teamUuid]=" + teamUuid + "&filter[startDateInterval]=" + monday + "&filter[withPolyvalences]=true";
        request(membersUrl, raw -> {
            parseCollaborators(raw);
            if (!collaboratorUuid.isEmpty() && !collaborators.containsKey(collaboratorUuid)) collaborators.put(collaboratorUuid, collaboratorName.isEmpty() ? "Tu" : collaboratorName);
            request(teamPlanningEndpoint(monday), planning -> { parseShifts(planning); renderSettings(); complete("Impostazioni aggiornate"); });
        });
    }
    private void loadEffiplanTeam(LocalDate start, LocalDate end, boolean search, boolean settings) {
        status.setText("Caricamento planning collettivo Effiplan…");
        effiplanAbsences.clear();
        final String population = search || settings || "OWN".equals(effiplanPopulation) ? ownTeamKey() : effiplanPopulation;
        requestEffiplan(api -> api.team(start, end, search, population).toString(), raw -> {
            try {
                JSONObject data = new JSONObject(raw);
                if (!search && !settings) teamDayMode = data.optBoolean("dailyView");
                teamUuid = data.getString("team");
                if (!search && !settings) effiplanPopulationTitle = data.getString("populationTitle");
                updatePopulationSelector();
                collaborators.clear(); collaborators.putAll(EffiplanData.members(data.getJSONArray("members")));
                if ((search || settings) && !collaborators.containsKey(collaboratorUuid)) collaborators.put(collaboratorUuid, collaboratorName.isEmpty() ? "Tu" : collaboratorName);
                parseShifts(data.getJSONArray("shifts").toString());
                if (!planningReadable) throw new Exception();
                if (settings) { renderSettings(); complete("Squadra Effiplan aggiornata"); return; }
                if (search) {
                    JSONArray blocks = data.getJSONArray("blocks");
                    for (int i = 0; i < blocks.length(); i++) {
                        JSONObject block = blocks.getJSONObject(i);
                        effiplanAbsences.add(new SwapSearch.Segment(block.getString("person"), LocalDateTime.parse(block.getString("from")), LocalDateTime.parse(block.getString("to")), "Assenza", SwapSearch.Service.BLOCKED));
                    }
                    requestEffiplan(api -> api.planning(start, end).toString(), own -> {
                        shifts.removeIf(s -> collaboratorUuid.equals(s.collaboratorId));
                        appendShifts(own);
                        if (!planningReadable) { showLoadError("I tuoi turni Effiplan non sono leggibili."); return; }
                        shifts.sort(Comparator.comparing(s -> s.from));
                        renderSwapForm(); complete("Planning Effiplan caricato");
                    });
                } else { renderTeam(start); complete("Planning squadra Effiplan aggiornato"); }
            } catch (Exception e) { showLoadError("Planning collettivo Effiplan incompleto o non leggibile."); }
        });
    }
    private void loadSwapOwnPlanning(LocalDate monday, boolean secondMonth) {
        LocalDate thisMonday = weekStart(LocalDate.now());
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
    private void updatePopulationSelector() {
        Button selector = findViewById(R.id.populationSelector);
        selector.setVisibility(authenticated && currentTab == Tab.TEAM ? View.VISIBLE : View.GONE);
        selector.setText(effiplanPopulationTitle + "  ▾");
        selector.setContentDescription("Cambia popolazione. Attuale: " + effiplanPopulationTitle);
    }
    private void chooseEffiplanPopulation() {
        if (!authenticated || currentTab != Tab.TEAM) return;
        java.util.function.Consumer<String> showChoices = raw -> {
            try {
                JSONArray choices = new JSONArray(raw);
                if (SetupRules.explicitTeam(ownTeamKey())) {
                    JSONArray withOwn = new JSONArray().put(new JSONObject().put("key", "OWN").put("title", "La mia squadra · " + ownTeamTitle()));
                    for (int i = 0; i < choices.length(); i++) withOwn.put(choices.get(i));
                    choices = withOwn;
                }
                final int generation = requestGeneration;
                LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
                content.setPadding(dp(12), dp(8), dp(12), dp(8));
                content.addView(text(effiplan == null
                    ? "Squadre del negozio autorizzate dal portale. La consultazione non cambia l’appartenenza usata per i cambi turno."
                    : "Sezioni e preferiti provengono dal tuo profilo. Puoi scegliere anche il negozio elencato dal portale o tutta la popolazione autorizzata; quest’ultima dipende dai tuoi permessi.", 12, MUTED, false));
                android.widget.EditText filter = new android.widget.EditText(this); filter.setSingleLine(true); filter.setHint("Cerca squadra o negozio");
                content.addView(filter, new LinearLayout.LayoutParams(-1, dp(52)));
                ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
                content.addView(scroll, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * .45f)));
                AlertDialog dialog = new AppDialogs.Builder(this).setTitle("Popolazione · " + planningPortal.label).setView(content).setNegativeButton("Annulla", null).create();
                for (int i = 0; i < choices.length(); i++) {
                    JSONObject choice = choices.getJSONObject(i);
                    String key = choice.getString("key"), title = choice.getString("title");
                    Button row = compactButton((key.equals(effiplanPopulation) ? "✓ " : "") + title);
                    row.setAllCaps(false); row.setTag(title.toLowerCase(Locale.ITALIAN));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(5); list.addView(row, params);
                    row.setOnClickListener(v -> {
                        if (destroyed || !authenticated || generation != requestGeneration) { dialog.dismiss(); return; }
                        effiplanPopulation = key; effiplanPopulationTitle = title;
                        teamDayMode = planningPortal == PlanningPortal.EPLANNING ? key.equals("STORE") : !key.equals("OWN") && !key.startsWith("SEH:");
                        openTeamToday();
                        teamUuid = ""; shifts.clear(); collaborators.clear(); effiplanAbsences.clear(); searchSelectedShifts.clear();
                        dialog.dismiss(); updatePopulationSelector(); loadCurrentTab();
                    });
                }
                filter.addTextChangedListener(new android.text.TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String query = s.toString().trim().toLowerCase(Locale.ITALIAN);
                        for (int i = 0; i < list.getChildCount(); i++) {
                            View row = list.getChildAt(i); row.setVisibility(row.getTag().toString().contains(query) ? View.VISIBLE : View.GONE);
                        }
                    }
                    public void afterTextChanged(android.text.Editable s) { }
                });
                dialog.show();
            } catch (Exception e) { showLoadError("Impossibile leggere le popolazioni disponibili sul portale."); }
        };
        if (effiplan != null) requestEffiplan(api -> api.populations().toString(), raw -> showChoices.accept(raw));
        else loadBrowseTeams(teams -> {
            JSONArray choices = new JSONArray();
            try {
                choices.put(new JSONObject().put("key", "STORE").put("title", "Tutto il negozio"));
                for (SetupRules.Team team : teams) if (!team.key.equals(ownTeamKey()))
                    choices.put(new JSONObject().put("key", team.key).put("title", team.title));
                showChoices.accept(choices.toString());
            } catch (Exception error) { showLoadError("Elenco squadre non leggibile."); }
        });
    }
    private String skillsKeyFor(String team) { return planningPortal.skillsPrefix() + accountScope + ":skills:" + collaboratorUuid + ":" + team; }
    private JSONObject storedSkillsFor(String team) {
        try {
            if (team.equals(calendarSettings().getString("own-team-key", "")) && calendarSettings().contains("own-team-roles"))
                return new JSONObject(calendarSettings().getString("own-team-roles", "{}"));
            android.content.SharedPreferences legacy = getSharedPreferences("swap-settings", MODE_PRIVATE);
            String key = skillsKeyFor(team);
            if (!legacy.contains(key)) key = planningPortal.skillsPrefix() + (effiplan == null ? "" : effiplan.scopeKey() + ":") + "skills:" + collaboratorUuid + ":" + team;
            return new JSONObject(legacy.getString(key, "{}"));
        } catch (Exception invalid) { return new JSONObject(); }
    }
    private JSONObject storedSkills() { return storedSkillsFor(ownTeamKey()); }
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
    private final String[] roleNames = TeamRolesDialog.LABELS;
    private String skillsSummary(String uuid) {
        EnumSet<SwapSearch.Role> roles = configuredSkills().get(uuid); if (roles == null) return "Da configurare";
        if (roles.isEmpty()) return "Nessuna abilitazione (escluso)";
        ArrayList<String> names = new ArrayList<>(); for (SwapSearch.Role role : roles) names.add(roleNames[role.ordinal()]); return android.text.TextUtils.join(", ", names);
    }
    private void configureSwapTeam() {
        if ((currentTab != Tab.SEARCH && currentTab != Tab.SETTINGS) || !teamUuid.equals(ownTeamKey())) return;
        final String scope = accountScope, team = ownTeamKey();
        Map<String, Integer> masks = new HashMap<>(); JSONObject stored = storedSkills();
        for (String id : collaborators.keySet()) masks.put(id, SetupRules.normalizeRoles(stored.optInt(id, 0)));
        TeamRolesDialog.show(this, new HashMap<>(collaborators), collaboratorUuid, masks, roles -> {
            if (destroyed || !authenticated || !scope.equals(accountScope) || !team.equals(ownTeamKey())) return;
            calendarSettings().edit().putString("own-team-roles", new JSONObject(roles).toString()).apply();
            if (currentTab == Tab.SEARCH) renderSwapForm(); else if (currentTab == Tab.SETTINGS) renderSettings();
        });
    }
    private Button searchAction(String label, LinearLayout parent, Runnable action) {
        Button button = compactButton(label); button.setAllCaps(false); button.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); parent.addView(button, p); button.setOnClickListener(v -> action.run()); return button;
    }
    private String shiftChoiceKey(Shift s) { return s.collaboratorId + "|" + s.from + "|" + s.to + "|" + s.typeLabel(); }
    private ArrayList<Shift> chosenSwapShifts() { ArrayList<Shift> selected = new ArrayList<>(); for (Shift s : shifts) if (isOwnShift(s) && searchSelectedShifts.contains(shiftChoiceKey(s))) selected.add(s); selected.sort(Comparator.comparing(s -> s.from)); return selected; }
    private SwapSearch.Service swapService(Shift s) {
        {
            String override = serviceSettings().getString(s.typeId, "");
            try { return SwapSearch.Service.valueOf(override); } catch (IllegalArgumentException ignored) { }
        }
        return SwapSearch.service(planningPortal == PlanningPortal.EFFIPLAN ? EffiplanRules.serviceLabel(s.translation) : s.translation, s.abbreviation);
    }
    private boolean selectableSwapShift(Shift s) { SwapSearch.Service service = swapService(s); return service != SwapSearch.Service.BLOCKED && service != SwapSearch.Service.UNKNOWN; }
    private void selectSwapShifts() {
        final String owner = collaboratorUuid; final int generation = requestGeneration;
        ArrayList<Shift> own = new ArrayList<>(); for (Shift s : shifts) if (isOwnShift(s)) own.add(s); own.sort(Comparator.comparing(s -> s.from));
        ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), dp(8), dp(12), dp(8)); scroll.addView(content);
        content.addView(text("Seleziona uno o più turni della stessa giornata. Deseleziona quelli scelti per cambiare giorno. Verranno scambiati i turni selezionati per intero.", 13, MUTED, false));
        Map<Shift, CheckBox> boxes = new HashMap<>();
        LocalDate monday = weekStart(searchDay);
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
        weekList.removeAllViews(); weekLabel.setText("Cerca cambio · W" + weekNumber(searchDay)); weekSummary.setText(weekStart(searchDay).format(dateFormat) + " – " + weekStart(searchDay).plusDays(6).format(dateFormat));
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
        for (Shift s : chosenSwapShifts()) selected.add(new SwapSearch.Segment(s.collaboratorId, s.from, s.to, s.typeLabel(), swapService(s)));
        if (selected.isEmpty()) { renderSwapForm(); return; }
        if (planningPortal == PlanningPortal.EFFIPLAN) for (SwapSearch.Segment chosen : selected) for (SwapSearch.Segment block : effiplanAbsences) {
            if (chosen.person.equals(block.person) && chosen.start.isBefore(block.end) && block.start.isBefore(chosen.end)) {
                results.addView(text("Il turno selezionato coincide con un’assenza: verifica il planning sul portale.", 14, INK, true)); return;
            }
        }
        LocalDateTime from = selected.get(0).start;
        LocalDateTime to = selected.stream().map(s -> s.end).max(LocalDateTime::compareTo).get();
        ArrayList<SwapSearch.Segment> planning = new ArrayList<>();
        ArrayList<String> unrecognized = new ArrayList<>();
        for (Shift s : shifts) {
            SwapSearch.Service service = swapService(s);
            if (service == SwapSearch.Service.UNKNOWN && !unrecognized.contains(s.typeLabel())) unrecognized.add(s.typeLabel());
            planning.add(new SwapSearch.Segment(s.collaboratorId, s.from, s.to, s.typeLabel(), service));
        }
        if (planningPortal == PlanningPortal.EFFIPLAN) planning.addAll(effiplanAbsences);
        final String user = collaboratorUuid; final int generation = requestGeneration; Map<String, EnumSet<SwapSearch.Role>> skills = configuredSkills(); Map<String, String> names = new HashMap<>(collaborators);
        final DayOfWeek firstDay = planningPortal.firstDay();
        button.setEnabled(false); button.setText("Analisi in corso…");
        executor.execute(() -> {
            SwapSearch.Result found = SwapSearch.findSelected(user, from, to, planning, skills, selected, firstDay);
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
    private interface EffiplanRequest { String run(EffiplanApi api) throws Exception; }
    private void requestEffiplan(EffiplanRequest query, ResponseHandler handler) {
        final int generation = requestGeneration;
        final EffiplanApi client = effiplan;
        if (client == null) { clearSession(); return; }
        executor.execute(() -> {
            try {
                String body = query.run(client);
                runOnUiThread(() -> { if (!destroyed && authenticated && generation == requestGeneration) handler.handle(body); });
            } catch (EffiplanApi.AuthenticationException e) {
                runOnUiThread(() -> { if (!destroyed && authenticated && generation == requestGeneration) clearSession(); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!destroyed && authenticated && generation == requestGeneration) showLoadError("Impossibile caricare Effiplan. Verifica la connessione, la sessione e i permessi del portale."); });
            }
        });
    }
    private void request(String endpoint, ResponseHandler handler) {
        if (planningPortal != PlanningPortal.EPLANNING) { showLoadError("Funzione non ancora disponibile per Effiplan."); return; }
        final int generation = requestGeneration; final String requestToken = token;
        executor.execute(() -> { HttpURLConnection connection = null; try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + requestToken); connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            int code = connection.getResponseCode(); String body = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (code >= 400) {
                String stage = endpoint.startsWith(TEAM_MEMBERS_API) || endpoint.contains("/getCollaboratorsByTeam/") ? "componenti squadra"
                    : endpoint.startsWith(MONTHLY_API) ? "planning mensile" : "planning";
                android.util.Log.w("PlanningRead", stage + ": HTTP " + code);
            }
            runOnUiThread(() -> {
                if (destroyed || generation != requestGeneration || !authenticated) return;
                if (code >= 200 && code < 300) handler.handle(body);
                else if (code == 401) clearSession();
                else if (SetupData.eplanningAuthorizationDenied(body)) showLoadError("Il tuo profilo E‑Planning non è autorizzato a consultare questa squadra. Il portale consente di leggere solo la squadra di appartenenza. Seleziona la tua squadra oppure chiedi al referente del portale di verificare i permessi.");
                else if (code == 403) showLoadError("Il portale non autorizza la lettura della squadra selezionata. Rivedi la squadra nelle Impostazioni.");
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
        ArrayList<Shift> visible = shiftsInPeriod(currentWeek, currentWeek.plusDays(6)); long minutes = workMinutes(visible); weekSummary.setText(visible.size() + " voci · " + hours(minutes));
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
        weekList.addView(text("Impostazioni", 26, INK, true));
        TextView settingsSubtitle = text("Preferenze e configurazioni dell’app", 14, MUTED, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(4); subtitleParams.bottomMargin = dp(18);
        weekList.addView(settingsSubtitle, subtitleParams);
        LinearLayout provider = settingsSection("PORTALE", "Scegli il portale usato dal tuo negozio. Il cambio chiude la sessione e riavvia l’app; sarà richiesto un nuovo accesso.");
        provider.addView(text(planningPortal.label, 16, INK, true));
        searchAction("Rivedi configurazione iniziale", provider, () -> openAccountSetup(true));
        searchAction("Cambia portale", provider, () -> choosePlanningPortal(true)); weekList.addView(provider);
        LinearLayout calendar = settingsSection("GOOGLE CALENDAR", "Scegli il calendario Google dove salvare i turni sincronizzati.");
        CheckBox syncEnabled = new CheckBox(this); syncEnabled.setText("Abilita sincronizzazione Google Calendar"); syncEnabled.setChecked(calendarEnabled()); calendar.addView(syncEnabled);
        syncEnabled.setOnCheckedChangeListener((button, enabled) -> { calendarSettings().edit().putBoolean("calendar-sync-enabled", enabled).apply(); renderSettings(); });
        LinearLayout calendarOptions = new LinearLayout(this); calendarOptions.setOrientation(LinearLayout.VERTICAL); calendarOptions.setVisibility(calendarEnabled() ? View.VISIBLE : View.GONE); calendar.addView(calendarOptions);
        long calendarId = selectedCalendarId();
        calendarOptions.addView(text(calendarId < 0 ? "Nessun calendario collegato." : "Calendario selezionato: " + selectedCalendarName(), 14, INK, false));
        Button calendarAction = compactButton(calendarId < 0 ? "COLLEGA CALENDARIO" : "CAMBIA CALENDARIO"); calendarAction.setAllCaps(false); calendarAction.setOnClickListener(v -> chooseGoogleCalendar()); calendarOptions.addView(calendarAction, calendarButtonParams());
        TextView notifications = text("NOTIFICHE DEGLI EVENTI", 11, NAVY, true); notifications.setPadding(0, dp(18), 0, dp(6)); calendarOptions.addView(notifications);
        calendarOptions.addView(text("Scegli fino a due avvisi prima dell’inizio del turno. Le modifiche si applicano alla prossima sincronizzazione della settimana. Le notifiche sono gestite dall’app calendario.", 12, MUTED, false));
        addReminderSetting(calendarOptions, 0, "Primo avviso", 1440);
        addReminderSetting(calendarOptions, 1, "Secondo avviso", 60);
        weekList.addView(calendar);
        LinearLayout team = settingsSection("IL MIO TEAM", "Gestisci le informazioni e le configurazioni della tua squadra.");
        team.addView(text("Squadra di appartenenza: " + ownTeamTitle(), 14, INK, true));
        searchAction("Verifica squadra e configurazione", team, () -> { currentMembership = null; openAccountSetup(true); });
        if (teamUuid.isEmpty()) {
            team.addView(text("Nessuna squadra associata al portale selezionato.", 14, MUTED, false));
        } else if (collaborators.isEmpty()) {
            team.addView(text("Impossibile caricare la squadra. Premi aggiorna per riprovare.", 14, MUTED, false));
        } else {
            boolean configured = swapConfigured();
            team.addView(text(configured ? "Abilitazioni squadra configurate." : "Abilitazioni squadra da configurare.", 14, INK, false));
            Button teamAction = compactButton(configured ? "MODIFICA SQUADRA" : "CONFIGURA SQUADRA"); teamAction.setAllCaps(false); teamAction.setOnClickListener(v -> configureSwapTeam()); team.addView(teamAction, calendarButtonParams());
        }
        weekList.addView(team);
        renderServiceSettings();
    }
    private android.content.SharedPreferences serviceSettings() {
        return getSharedPreferences((effiplan == null ? "eplanning-services-" : "effiplan-services-") + accountScope, MODE_PRIVATE);
    }
    private void renderServiceSettings() {
        LinearLayout section = settingsSection("TIPI DI TURNO", "Nomi e codici arrivano dalle API. Per la ricerca cambi puoi assegnare una categoria anche ai servizi specifici del tuo negozio. I tipi non riconosciuti restano esclusi finché non li configuri; colori e nomi del portale non vengono modificati.");
        Map<String, Shift> types = new java.util.TreeMap<>();
        for (Shift shift : shifts) types.put(shift.typeId, shift);
        if (types.isEmpty()) section.addView(text("Nessun tipo di turno disponibile per questa popolazione e settimana.", 12, MUTED, false));
        String[] categories = { "Vendita", "Workshop", "PFL", "Accoglienza", "Non scambiabile", "Non assegnato (escluso)" };
        for (Shift shift : types.values()) {
            Button row = compactButton(shift.typeLabel() + " · " + categories[swapService(shift).ordinal()]);
            row.setAllCaps(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6); section.addView(row, params);
            row.setOnClickListener(v -> {
                final int generation = requestGeneration; final String scope = accountScope;
                final int[] selected = { swapService(shift).ordinal() };
                new AppDialogs.Builder(this).setTitle(shift.typeLabel())
                    .setSingleChoiceItems(categories, selected[0], (d, which) -> selected[0] = which)
                    .setNegativeButton("Annulla", null).setPositiveButton("Salva categoria", (d, which) -> {
                        if (destroyed || !authenticated || !scope.equals(accountScope) || generation != requestGeneration) return;
                        serviceSettings().edit().putString(shift.typeId, SwapSearch.Service.values()[selected[0]].name()).apply();
                        renderSettings();
                    }).show();
            });
        }
        weekList.addView(section);
    }
    private LinearLayout settingsSection(String title, String description) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL); section.setPadding(dp(16), dp(14), dp(16), dp(14)); section.setBackgroundResource(R.drawable.bg_day);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12); section.setLayoutParams(params);
        section.addView(text(title, 11, NAVY, true)); TextView help = text(description, 12, MUTED, false); LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2); helpParams.topMargin = dp(4); helpParams.bottomMargin = dp(10); section.addView(help, helpParams);
        return section;
    }
    private static final int[] REMINDER_TIMES = SetupRules.REMINDER_TIMES;
    private static final String[] REMINDER_LABELS = { "All’inizio", "5 minuti prima", "10 minuti prima", "15 minuti prima", "30 minuti prima", "1 ora prima", "2 ore prima", "4 ore prima", "12 ore prima", "1 giorno prima", "2 giorni prima", "1 settimana prima" };
    private void addReminderSetting(LinearLayout parent, int index, String label, int defaultMinutes) {
        android.content.SharedPreferences prefs = calendarSettings();
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
        android.content.SharedPreferences prefs = calendarSettings();
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
        boolean visible = currentTab == Tab.WEEK && authenticated && !configuringAccount;
        calendarSyncButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        calendarSyncButton.setEnabled(visible && weekReady && !calendarSyncing);
        calendarSyncButton.setAlpha(calendarSyncing ? .4f : 1f);
        if (!visible) { calendarSyncIndicator.setVisibility(View.GONE); return; }
        String saved = calendarEnabled() ? calendarSettings().getString(calendarSyncKey(collaboratorUuid, selectedCalendarId(), currentWeek), "") : "";
        boolean synced = !saved.isEmpty();
        boolean changed = synced && weekReady && !saved.equals(weekSyncFingerprint(shiftsInPeriod(currentWeek, currentWeek.plusDays(6)), configuredReminders()));
        calendarSyncIndicator.setVisibility(synced ? View.VISIBLE : View.GONE);
        calendarSyncIndicator.setText(changed ? "✓ Calendar · da aggiornare" : "✓ Calendar");
        calendarSyncIndicator.setTextColor(changed ? Color.rgb(254, 215, 170) : Color.rgb(187, 247, 208));
        calendarSyncButton.setColorFilter(synced ? Color.rgb(187, 247, 208) : Color.WHITE);
        calendarSyncButton.setContentDescription(synced ? "Settimana già sincronizzata. Sincronizza nuovamente su Google Calendar" : "Sincronizza settimana su Google Calendar");
    }
    private void confirmCalendarSync() {
        if (!authenticated || accountScope.isEmpty() || configuringAccount || !weekReady || currentTab != Tab.WEEK || calendarSyncing) return;
        if (!calendarEnabled() || selectedCalendarId() < 0) { configureCalendarPopup(); return; }
        final long calendar = selectedCalendarId();
        if (calendar < 0) {
            new AppDialogs.Builder(this).setTitle("Collega un calendario").setMessage("Seleziona il calendario Google nelle Impostazioni prima di sincronizzare i turni.").setNegativeButton("Annulla", null).setPositiveButton("Impostazioni", (d, w) -> selectTab(Tab.SETTINGS)).show(); return;
        }
        final String owner = collaboratorUuid; final LocalDate week = currentWeek; final int generation = requestGeneration;
        ArrayList<Shift> visible = shiftsInPeriod(week, week.plusDays(6));
        boolean synced = calendarSettings().contains(calendarSyncKey(owner, calendar, week));
        String message = "Settimana " + week.format(dateFormat) + " – " + week.plusDays(6).format(dateFormat) + "\nCalendario: " + selectedCalendarName() + "\n\n";
        message += synced ? "Questa settimana è già stata sincronizzata. Vuoi aggiornarla? Titoli, turni e notifiche verranno riallineati senza duplicati." : "Vuoi sincronizzare i " + visible.size() + " turni della settimana selezionata?";
        new AppDialogs.Builder(this).setTitle(synced ? "Settimana già sincronizzata" : "Sincronizza settimana").setMessage(message).setNegativeButton("Annulla", null).setPositiveButton(synced ? "Aggiorna" : "Sincronizza", (d, w) -> {
            if (!authenticated || !owner.equals(collaboratorUuid) || !week.equals(currentWeek) || generation != requestGeneration || calendar != selectedCalendarId() || currentTab != Tab.WEEK) return;
            syncWeekToCalendar(visible);
        }).show();
    }
    private LinearLayout.LayoutParams calendarButtonParams() { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44)); params.topMargin = dp(7); return params; }
    private void configureCalendarPopup() {
        final String scope = accountScope;
        final SetupRules.Draft draft = setupDraft();
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(16), dp(8), dp(16), dp(8));
        panel.addView(text("Scegli il calendario e gli avvisi. Nessun evento verrà creato: la sincronizzazione richiederà una conferma separata.", 13, MUTED, false));
        CheckBox enabled = new CheckBox(this); enabled.setText("Abilita Google Calendar"); enabled.setChecked(true); panel.addView(enabled);
        Button choose = compactButton(draft.calendarId >= 0 ? draft.calendarName : "Scegli calendario Google"); panel.addView(choose);
        for (int index = 0; index < 2; index++) {
            final int i = index;
            CheckBox reminder = new CheckBox(this); reminder.setText(i == 0 ? "Primo avviso" : "Secondo avviso"); reminder.setChecked(draft.reminderEnabled[i]); panel.addView(reminder);
            Spinner time = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, REMINDER_LABELS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); time.setAdapter(adapter);
            for (int n = 0; n < REMINDER_TIMES.length; n++) if (REMINDER_TIMES[n] == draft.reminderMinutes[i]) time.setSelection(n);
            time.setEnabled(reminder.isChecked()); panel.addView(time);
            reminder.setOnCheckedChangeListener((button, checked) -> { draft.reminderEnabled[i] = checked; time.setEnabled(checked); });
            time.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { draft.reminderMinutes[i] = REMINDER_TIMES[position]; }
                public void onNothingSelected(AdapterView<?> parent) { }
            });
        }
        AlertDialog dialog = new AppDialogs.Builder(this).setTitle("Configura Google Calendar").setView(panel)
            .setNegativeButton("Annulla", null).setPositiveButton("Salva", null).create();
        choose.setOnClickListener(v -> chooseGoogleCalendar(calendar -> {
            if (!dialog.isShowing() || !scope.equals(accountScope)) return;
            draft.calendarId = calendar.id; draft.calendarName = calendar.name; choose.setText(calendar.name);
        }));
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!authenticated || !scope.equals(accountScope) || configuringAccount) { dialog.dismiss(); return; }
            if (enabled.isChecked() && (draft.calendarId < 0 || !hasCalendarPermission())) {
                Toast.makeText(this, "Scegli un calendario e consenti l’accesso.", Toast.LENGTH_LONG).show(); return;
            }
            android.content.SharedPreferences.Editor editor = calendarSettings().edit()
                .putBoolean("calendar-sync-enabled", enabled.isChecked()).putLong(SELECTED_CALENDAR_ID, draft.calendarId).putString(SELECTED_CALENDAR_NAME, draft.calendarName);
            for (int i = 0; i < 2; i++) editor.putBoolean("reminder-enabled-" + i, draft.reminderEnabled[i]).putInt("reminder-minutes-" + i, draft.reminderMinutes[i]);
            editor.apply(); dialog.dismiss(); updateCalendarSyncIndicator();
        }));
        dialog.show();
    }
    private android.content.SharedPreferences calendarSettings() { return getSharedPreferences(planningPortal.calendarPreferences(accountScope), MODE_PRIVATE); }
    private boolean calendarEnabled() { return calendarSettings().getBoolean("calendar-sync-enabled", selectedCalendarId() >= 0); }
    private long selectedCalendarId() { return calendarSettings().getLong(SELECTED_CALENDAR_ID, -1); }
    private String selectedCalendarName() { return calendarSettings().getString(SELECTED_CALENDAR_NAME, "Calendario Google"); }
    private boolean hasCalendarPermission() { return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED; }
    private void chooseGoogleCalendar() {
        chooseGoogleCalendar(chosen -> {
            calendarSettings().edit().putLong(SELECTED_CALENDAR_ID, chosen.id).putString(SELECTED_CALENDAR_NAME, chosen.name).apply();
            if (currentTab == Tab.WEEK) renderWeek(); else if (currentTab == Tab.SETTINGS) renderSettings();
        });
    }
    private void chooseGoogleCalendar(java.util.function.Consumer<CalendarChoice> selected) {
        if (!authenticated || accountScope.isEmpty()) return;
        final String scope = accountScope;
        if (!hasCalendarPermission()) {
            pendingCalendarSelection = selected; pendingCalendarScope = scope;
            requestPermissions(new String[] { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR }, CALENDAR_PERMISSION_REQUEST); return;
        }
        executor.execute(() -> {
            ArrayList<CalendarChoice> calendars = new ArrayList<>();
            try { calendars = readGoogleCalendars(); } catch (SecurityException ignored) { }
            final ArrayList<CalendarChoice> available = calendars;
            runOnUiThread(() -> { if (!destroyed && authenticated && scope.equals(accountScope) && !selectingPortal) showCalendarChoices(available, selected, scope); });
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
    private void showCalendarChoices(ArrayList<CalendarChoice> calendars, java.util.function.Consumer<CalendarChoice> selection, String scope) {
        if (destroyed || !authenticated || !scope.equals(accountScope)) return;
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
        if (activeCalendarDialog != null) activeCalendarDialog.dismiss();
        activeCalendarDialog = dialog;
        choices.setOnCheckedChangeListener((group, id) -> {
            for (int i = 0; i < group.getChildCount(); i++) if (group.getChildAt(i).getId() == id) selected[0] = calendars.get(i);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected[0] != null);
        });
        dialog.setOnShowListener(ignored -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE); confirm.setEnabled(selected[0] != null);
            confirm.setOnClickListener(v -> {
            CalendarChoice chosen = selected[0]; if (chosen == null || !authenticated || !scope.equals(accountScope)) return;
            dialog.dismiss(); selection.accept(chosen);
            });
        Button refresh = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        refresh.setText(""); refresh.setContentDescription("Aggiorna elenco calendari");
        refresh.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0);
        refresh.setMinWidth(0); refresh.setMinimumWidth(0);
        refresh.getLayoutParams().width = dp(56);
        Runnable reload = () -> {
            if (destroyed || !dialog.isShowing()) return;
            if (!hasCalendarPermission()) { dialog.dismiss(); chooseGoogleCalendar(selection); return; }
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
        dialog.setOnDismissListener(d -> { getContentResolver().unregisterContentObserver(observer); if (activeCalendarDialog == dialog) activeCalendarDialog = null; });
        refresh.setOnClickListener(v -> {
            if (!hasCalendarPermission()) { dialog.dismiss(); chooseGoogleCalendar(selection); return; }
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
            java.util.function.Consumer<CalendarChoice> selected = pendingCalendarSelection;
            String scope = pendingCalendarScope; pendingCalendarSelection = null; pendingCalendarScope = "";
            if (selected == null || !authenticated || !scope.equals(accountScope)) return;
            if (hasCalendarPermission()) chooseGoogleCalendar(selected);
            else if (setupController != null) setupController.error("Permesso calendario negato. Puoi riprovare o proseguire disattivando la sincronizzazione.");
            else new AppDialogs.Builder(this).setTitle("Permesso necessario").setMessage("Per sincronizzare i turni, consenti a E-PLANNING di leggere e modificare il calendario.").setPositiveButton("Ho capito", null).show();
        }
    }
    private void syncWeekToCalendar(ArrayList<Shift> visible) {
        if (!authenticated || accountScope.isEmpty() || !calendarEnabled() || configuringAccount) return;
        if (!hasCalendarPermission()) { chooseGoogleCalendar(); return; }
        if (calendarSyncing) return;
        final long calendarId = selectedCalendarId();
        final LocalDate week = currentWeek;
        final String owner = collaboratorUuid;
        final android.content.SharedPreferences syncSettings = calendarSettings();
        final ArrayList<Integer> reminders = configuredReminders();
        final String fingerprint = weekSyncFingerprint(visible, reminders);
        final String markerPrefix = calendarMarkerPrefix();
        if (calendarId < 0) { chooseGoogleCalendar(); return; }
        calendarSyncing = true; updateCalendarSyncIndicator();
        executor.execute(() -> {
            try {
                LocalDateTime start = week.atStartOfDay(), end = week.plusDays(7).atStartOfDay();
                ContentResolver resolver = getContentResolver();
                Map<String, Shift> expected = new HashMap<>();
                ArrayList<ContentProviderOperation> operations = new ArrayList<>();
                for (Shift shift : visible) {
                    expected.put(eventMarker(shift, markerPrefix), shift);
                }
                String[] projection = { CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION };
                String selection = CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DTSTART + ">=? AND " + CalendarContract.Events.DTSTART + "<?";
                try (android.database.Cursor cursor = resolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, new String[] { String.valueOf(calendarId), String.valueOf(toMillis(start)), String.valueOf(toMillis(end)) }, null)) {
                    if (cursor != null) while (cursor.moveToNext()) {
                        String marker = markerFrom(cursor.getString(1), markerPrefix);
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
                    operations.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(calendarEventValues(calendarId, shift, eventMarker(shift, markerPrefix), !reminders.isEmpty())).build());
                    appendCalendarReminders(operations, -1, reference, reminders);
                }
                if (!operations.isEmpty()) resolver.applyBatch(CalendarContract.AUTHORITY, operations);
                syncSettings.edit().putString(calendarSyncKey(owner, calendarId, week), fingerprint).commit();
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
    private String calendarMarkerPrefix() { return planningPortal == PlanningPortal.EPLANNING ? EVENT_MARKER : "EFFIPLAN:" + (effiplan == null ? "" : effiplan.scopeKey()) + ":"; }
    private String eventMarker(Shift shift) { return eventMarker(shift, calendarMarkerPrefix()); }
    private String eventMarker(Shift shift, String prefix) { return prefix + shift.from + "|" + shift.to + "|" + shift.typeLabel(); }
    private String markerFrom(String description, String prefix) { if (description == null || !description.startsWith(prefix)) return null; int end = description.indexOf('\n'); return end < 0 ? description : description.substring(0, end); }
    private String noShiftLabel() { return planningPortal == PlanningPortal.EFFIPLAN ? "Nessun turno" : "Riposo"; }
    private View personalWeekRow(LocalDate day, ArrayList<Shift> list) {
        boolean today = day.equals(LocalDate.now());
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8)); row.setBackgroundResource(today ? R.drawable.bg_day_current : R.drawable.bg_day);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.bottomMargin = dp(6); row.setLayoutParams(rp);
        LinearLayout date = new LinearLayout(this); date.setOrientation(LinearLayout.VERTICAL);
        date.addView(text(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN).toUpperCase() + (today ? " · OGGI" : ""), 11, today ? NAVY : MUTED, true));
        date.addView(text(day.format(dateFormat), 17, INK, true)); row.addView(date, new LinearLayout.LayoutParams(dp(90), -2));
        LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); long minutes = 0;
        if (list == null || list.isEmpty()) details.addView(text(noShiftLabel(), 14, MUTED, false));
        else for (Shift shift : list) {
            if (!shift.nonWorking && !shift.allDay) minutes += Duration.between(shift.from, shift.to).toMinutes();
            LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(8), dp(5), dp(8), dp(5));
            GradientDrawable background = new GradientDrawable(); background.setColor(shift.color); background.setCornerRadius(dp(8)); line.setBackground(background);
            int foreground = shiftForeground(shift);
            TextView times = text(shift.timeLabel(), 14, foreground, true);
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
        long minutes = workMinutes(shifts);
        weekSummary.setText(shifts.size() + " voci · " + hours(minutes));
        Map<LocalDate, ArrayList<Shift>> byDay = shiftsByDay();
        LocalDate cursor = weekStart(currentMonth);
        LocalDate end = weekStart(currentMonth.plusMonths(1).minusDays(1)).plusDays(6);
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
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i); ArrayList<Shift> daily = shiftsInPeriod(day, day);
            TeamChart chart = new TeamChart(day, daily, teamGrouping);
            chart.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_day);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(dp(360), teamChartMinHeight(daily))); p.bottomMargin = dp(12);
            weekList.addView(scrollChart(chart, daily, teamGrouping), p);
        }
        scrollTeamWeekToToday(monday);
    }
    private void scrollTeamWeekToToday(LocalDate start) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(start) || today.isAfter(start.plusDays(6))) return;
        int index = (int) java.time.temporal.ChronoUnit.DAYS.between(start, today);
        planningScroll.post(() -> {
            if (currentTab != Tab.TEAM || teamDayMode || weekList.getChildCount() <= index) return;
            planningScroll.scrollTo(0, weekList.getChildAt(index).getTop());
        });
    }
    private void renderPeriod(LocalDate start, LocalDate end, String title, boolean showWeekDividers) {
        weekList.removeAllViews(); weekLabel.setText(title);
        ArrayList<Shift> visible = shiftsInPeriod(start, end); long minutes = 0; for (Shift shift : visible) minutes += Duration.between(shift.from, shift.to).toMinutes();
        weekSummary.setText(visible.size() + (visible.size() == 1 ? " turno" : " turni") + " · " + hours(minutes));
        Map<LocalDate, ArrayList<Shift>> byDay = shiftsByDay();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) { if (showWeekDividers && day.getDayOfWeek() == planningPortal.firstDay()) weekList.addView(weekDivider(day)); weekList.addView(dayCard(day, byDay.get(day))); }
    }
    private Map<LocalDate, ArrayList<Shift>> shiftsByDay() { Map<LocalDate, ArrayList<Shift>> byDay = new HashMap<>(); for (Shift shift : shifts) byDay.computeIfAbsent(shift.date, ignored -> new ArrayList<>()).add(shift); return byDay; }
    private ArrayList<Shift> shiftsInPeriod(LocalDate start, LocalDate end) { ArrayList<Shift> result = new ArrayList<>(); for (Shift shift : shifts) if (!shift.date.isBefore(start) && !shift.date.isAfter(end)) result.add(shift); return result; }
    private long workMinutes(List<Shift> list) { long minutes = 0; for (Shift shift : list) if (!shift.nonWorking && !shift.allDay) minutes += Duration.between(shift.from, shift.to).toMinutes(); return minutes; }
    private void renderEmpty(String headline, String body) { weekLabel.setText("Il tuo planning"); weekSummary.setText("Accedi al portale per vedere i turni"); weekList.removeAllViews(); weekList.addView(empty(headline, body)); }
    private View dayCard(LocalDate day, ArrayList<Shift> list) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_day); card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(0, 0, 0, dp(8)); card.setLayoutParams(cp);
        LinearLayout headerRow = new LinearLayout(this); headerRow.setGravity(Gravity.CENTER_VERTICAL); headerRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView dayName = text(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN), 15, INK, true); dayName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        headerRow.addView(dayName); headerRow.addView(text(day.format(dateFormat), 13, MUTED, false)); card.addView(headerRow);
        if (list == null || list.isEmpty()) { TextView rest = text(noShiftLabel(), 13, MUTED, false); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(7); rest.setLayoutParams(p); card.addView(rest); return card; }
        long mins = 0; for (Shift shift : list) { if (!shift.nonWorking && !shift.allDay) mins += Duration.between(shift.from, shift.to).toMinutes(); card.addView(shiftRow(shift)); }
        if (mins > 0) { TextView total = text(hours(mins), 12, MUTED, true); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(7); total.setLayoutParams(p); card.addView(total); } return card;
    }
    private View shiftRow(Shift shift) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(8), dp(10), dp(8));
        boolean officialPalette = planningPortal == PlanningPortal.EFFIPLAN;
        GradientDrawable bg = new GradientDrawable(); bg.setColor(officialPalette ? shift.color : lighten(shift.color, .88f)); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1), shift.color); row.setBackground(bg);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(7); row.setLayoutParams(rp);
        TextView time = text(shift.timeLabel(), 14, officialPalette ? shiftForeground(shift) : INK, true); time.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(time); row.addView(text(shift.label(), 12, officialPalette ? shiftForeground(shift) : shift.color, true)); return row;
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
            button.setOnClickListener(v -> { teamDayMode = day; openTeamToday(); saveTeamView(); loadCurrentTab(); });
            periods.addView(button, optionParams());
        }
        panel.addView(periods); TextView label = text("RAGGRUPPAMENTO", 10, MUTED, true); label.setPadding(0, dp(7), 0, 0); panel.addView(label);
        LinearLayout groups = new LinearLayout(this);
        TeamGrouping[] values = { TeamGrouping.DAY, TeamGrouping.COLLABORATOR, TeamGrouping.DAILY };
        String[] titles = { "Per ora", "Per collaboratore", "Per giornata" };
        for (int i = 0; i < values.length; i++) {
            final TeamGrouping value = values[i]; Button button = optionButton(titles[i], value == teamGrouping);
            button.setOnClickListener(v -> { teamGrouping = value; saveTeamView(); renderTeam(currentTeamWeek); }); groups.addView(button, optionParams());
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
        TeamChart chart = new TeamChart(day, today, teamGrouping); chart.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_day); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(390)); HorizontalScrollView viewport = scrollChart(chart, today, teamGrouping); weekList.addView(viewport, p);
        weekList.post(() -> {
            if (currentTab != Tab.TEAM || !teamDayMode || viewport.getParent() != weekList) return;
            int available = ((View) weekList.getParent()).getHeight() - weekList.getPaddingTop() - weekList.getPaddingBottom();
            viewport.getLayoutParams().height = Math.max(teamChartMinHeight(today), available); viewport.requestLayout();
        });
    }
    private void renderDailyTimeline() {
        weekList.removeAllViews(); LocalDate start = teamDayMode ? currentTeamDay : currentTeamWeek;
        LocalDate end = teamDayMode ? start : start.plusDays(6); ArrayList<Shift> visible = shiftsInPeriod(start, end);
        weekLabel.setText("W" + weekNumber(start) + (teamDayMode ? " · " + start.format(dateFormat).toUpperCase() : ""));
        long minutes = 0; for (Shift shift : visible) minutes += Duration.between(shift.from, shift.to).toMinutes(); weekSummary.setText(visible.size() + " turni · " + hours(minutes));
        DailyTimeline chart = new DailyTimeline(start, teamDayMode ? 1 : 7);
        chart.setMinimumWidth(dp(1400)); chart.setBackgroundResource(R.drawable.bg_day); HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setFillViewport(true);
        scroll.addView(chart, new android.widget.FrameLayout.LayoutParams(dp(1400), -1));
        weekList.addView(scroll, new LinearLayout.LayoutParams(-1, chart.contentHeight));
        weekList.post(() -> {
            if (currentTab != Tab.TEAM || teamGrouping != TeamGrouping.DAILY || scroll.getParent() != weekList) return;
            int available = ((View) weekList.getParent()).getHeight() - weekList.getPaddingBottom();
            scroll.getLayoutParams().height = Math.max(chart.contentHeight, available); scroll.requestLayout();
        });
    }
    private abstract class StickyAxesChart extends View {
        private final ArrayList<Runnable> axisLabels = new ArrayList<>();
        private final android.view.ViewTreeObserver.OnScrollChangedListener scrollListener = this::invalidate;
        StickyAxesChart() { super(MainActivity.this); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); getViewTreeObserver().addOnScrollChangedListener(scrollListener); }
        @Override protected void onDetachedFromWindow() { getViewTreeObserver().removeOnScrollChangedListener(scrollListener); super.onDetachedFromWindow(); }
        void beginAxes() { axisLabels.clear(); }
        void axisLabel(Canvas canvas, String label, float x, float y, Paint source, boolean horizontal) {
            Paint labelPaint = new Paint(source);
            android.graphics.Rect visible = new android.graphics.Rect();
            getLocalVisibleRect(visible);
            float targetX = horizontal ? x : x + visible.left;
            float targetY = horizontal ? Math.max(y, visible.top + y) : y;
            axisLabels.add(() -> canvas.drawText(label, targetX, targetY, labelPaint));
        }
        void endAxes() { for (Runnable draw : axisLabels) draw.run(); }
    }
    private class DailyTimeline extends StickyAxesChart {
        private final LocalDate start; private final int days; private final Paint paint = new Paint(1);
        private final ArrayList<ArrayList<ArrayList<Shift>>> lanesByDay = new ArrayList<>();
        private int axisStart = 24 * 60, axisEnd = 0;
        final int contentHeight;
        DailyTimeline(LocalDate start, int days) {
            super(); this.start = start; this.days = days; int height = dp(48);
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
            super.onDraw(canvas); beginAxes(); float left = dp(116), top = dp(42), unit = (getWidth() - left - dp(20)) / (axisEnd - axisStart);
            paint.setTypeface(android.graphics.Typeface.DEFAULT); paint.setTextSize(dp(11));
            for (int hour = axisStart / 60; hour <= axisEnd / 60; hour++) { float x = left + (hour * 60 - axisStart) * unit; paint.setColor(Color.rgb(226,232,240)); canvas.drawLine(x, top, x, getHeight() - dp(10), paint); paint.setColor(MUTED); axisLabel(canvas, String.format(Locale.ITALIAN, "%02d:00", hour % 24), x, dp(23), paint, true); }
            float y = top; float extra = Math.max(0, getHeight() - contentHeight) / (float) days;
            for (int d = 0; d < days; d++) {
                LocalDate day = start.plusDays(d); ArrayList<ArrayList<Shift>> lanes = lanesByDay.get(d); float height = dp(Math.max(1, lanes.size()) * 44 + 60) + extra;
                if (day.equals(LocalDate.now())) { paint.setColor(Color.rgb(239,246,255)); canvas.drawRect(0, y, getWidth(), y + height, paint); }
                paint.setColor(day.equals(LocalDate.now()) ? NAVY : INK); paint.setTextSize(dp(13)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                axisLabel(canvas, day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN).toUpperCase() + " " + day.format(dateFormat), dp(10), y + dp(23), paint, false);
                if (day.equals(LocalDate.now())) { paint.setTextSize(dp(10)); axisLabel(canvas, "OGGI", dp(10), y + dp(40), paint, false); }
                for (int lane = 0; lane < lanes.size(); lane++) if (isOwnShift(lanes.get(lane).get(0))) highlightOwnBand(canvas, paint, new RectF(left, y + dp(3 + lane * 44), getWidth(), y + dp(47 + lane * 44)));
                for (int lane = 0; lane < lanes.size(); lane++) for (Shift shift : lanes.get(lane)) {
                    float x = left + (shift.from.getHour() * 60 + shift.from.getMinute() - axisStart) * unit;
                    float duration = Duration.between(shift.from, shift.to).toMinutes();
                    RectF box = new RectF(x, y + dp(6 + lane * 44), x + Math.max(dp(4), duration * unit), y + dp(44 + lane * 44));
                    paint.setColor(shift.color); canvas.drawRoundRect(box, dp(6), dp(6), paint);
                    outlineOwnShift(canvas, paint, box, shift);
                    canvas.save(); canvas.clipRect(box); paint.setColor(shiftForeground(shift)); paint.setTextSize(dp(12));
                    canvas.drawText(shift.collaborator + (isOwnShift(shift) ? " · TU" : ""), box.left + dp(6), box.top + dp(15), paint);
                    paint.setTextSize(dp(11)); canvas.drawText(shift.from.toLocalTime() + "–" + shift.to.toLocalTime() + " / " + shift.typeLabel(), box.left + dp(6), box.top + dp(30), paint); canvas.restore();
                }
                paint.setTypeface(android.graphics.Typeface.DEFAULT); paint.setTextSize(dp(11)); paint.setColor(MUTED);
                if (lanes.isEmpty()) canvas.drawText("Nessun turno pianificato", left, y + dp(23), paint);
                paint.setColor(Color.rgb(203,213,225)); canvas.drawLine(0, y + height, getWidth(), y + height, paint); y += height;
            }
            endAxes();
        }
    }
    private class TeamChart extends StickyAxesChart {
        private final LocalDate day; private final ArrayList<Shift> data; private final TeamGrouping grouping; private final Paint paint = new Paint(1);
        TeamChart(LocalDate day, ArrayList<Shift> data, TeamGrouping grouping) { super(); this.day = day; this.data = data; this.grouping = grouping; paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); beginAxes(); int w = getWidth(), h = getHeight(); float left = dp(grouping == TeamGrouping.DAY ? 48 : 96), top = dp(60), bottom = h - dp(16); int start = 7 * 60, end = 21 * 60;
            paint.setColor(INK); paint.setTextSize(dp(16)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); axisLabel(canvas, day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " " + day.format(dateFormat), dp(16), dp(24), paint, false);
            ArrayList<String> people = teamPeople(data);
            if (people.isEmpty()) { paint.setColor(MUTED); paint.setTextSize(dp(14)); canvas.drawText("Nessun turno pianificato", dp(16), dp(70), paint); endAxes(); return; }
            if (grouping == TeamGrouping.DAY) drawByTime(canvas, people, left, top, w - dp(10), bottom, start, end); else drawByPerson(canvas, people, left, top, w - dp(10), bottom, start, end);
            endAxes();
        }
        private void drawByTime(Canvas canvas, ArrayList<String> people, float left, float top, float right, float bottom, int start, int end) {
            float columnWidth = (right - left) / people.size();
            for (int i = 0; i < people.size(); i++) if (isOwnName(people.get(i))) highlightOwnBand(canvas, paint, new RectF(left + i * columnWidth, top - dp(34), left + (i + 1) * columnWidth, bottom));
            float lane = (right - left) / people.size(); for (int hour = start / 60; hour <= end / 60; hour++) { float y = top + (hour * 60 - start) * (bottom - top) / (end - start); paint.setColor(Color.rgb(203,213,225)); paint.setStrokeWidth(1); canvas.drawLine(left, y, right, y, paint); paint.setColor(MUTED); paint.setTextSize(dp(10)); axisLabel(canvas, hour + ":00", dp(4), y + dp(3), paint, false); }
            for (int i = 0; i < people.size(); i++) {
                String name = people.get(i); String[] parts = name.split(" ", 2);
                paint.setColor(isOwnName(name) ? NAVY : MUTED); paint.setTextSize(dp(10)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.save(); canvas.clipRect(left + i * lane, top - dp(34), left + (i + 1) * lane, top);
                axisLabel(canvas, parts[0], left + i * lane + dp(3), top - dp(21), paint, true);
                String second = (parts.length > 1 ? parts[1] : "") + (isOwnName(name) ? " · TU" : "");
                float width = paint.measureText(second); if (width > lane - dp(6)) paint.setTextSize(dp(10) * (lane - dp(6)) / width);
                axisLabel(canvas, second, left + i * lane + dp(3), top - dp(7), paint, true); canvas.restore();
            }
            for (Shift s : data) { int i = people.indexOf(s.collaborator.isEmpty() ? "Collaboratore" : s.collaborator); float y1 = top + minutes(s.from) * (bottom-top)/(end-start), y2 = top + minutes(s.to)*(bottom-top)/(end-start); drawBlock(canvas, new RectF(left+i*lane+dp(3), y1, left+(i+1)*lane-dp(3), y2), s, false); }
        }
        private void drawByPerson(Canvas canvas, ArrayList<String> people, float left, float top, float right, float bottom, int start, int end) {
            float rowHeight = (bottom - top) / people.size();
            for (int i = 0; i < people.size(); i++) if (isOwnName(people.get(i))) highlightOwnBand(canvas, paint, new RectF(0, top + i * rowHeight, right, top + (i + 1) * rowHeight));
            float row = (bottom-top)/people.size(); for (int hour=start/60; hour<=end/60; hour++) { float x=left+(hour*60-start)*(right-left)/(end-start); paint.setColor(Color.rgb(203,213,225)); canvas.drawLine(x, top, x, bottom, paint); paint.setColor(MUTED); paint.setTextSize(dp(9)); axisLabel(canvas, hour + ":00", x, top-dp(8), paint, true); }
            for (int i=0;i<people.size();i++) { float y=top+i*row; paint.setColor(MUTED); paint.setTextSize(dp(11)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); axisLabel(canvas, people.get(i), dp(4), y+dp(18), paint, false); paint.setColor(Color.rgb(203,213,225)); canvas.drawLine(left,y+row,right,y+row,paint); }
            for (Shift s:data) { int i=people.indexOf(s.collaborator.isEmpty()?"Collaboratore":s.collaborator); float x1=left+minutes(s.from)*(right-left)/(end-start), x2=left+minutes(s.to)*(right-left)/(end-start); drawBlock(canvas,new RectF(x1,top+i*row+dp(4),x2,top+(i+1)*row-dp(4)),s,true); }
        }
        private int minutes(LocalDateTime time) { return time.getHour()*60+time.getMinute()-7*60; }
        private void drawBlock(Canvas canvas, RectF box, Shift shift, boolean compact) {
            paint.setColor(shift.color); canvas.drawRoundRect(box, dp(6), dp(6), paint);
            outlineOwnShift(canvas, paint, box, shift);
            canvas.save(); canvas.clipRect(box); paint.setColor(shiftForeground(shift)); paint.setTextSize(dp(10)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
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
    private View weekCell(LocalDate day, ArrayList<Shift> list) {
        LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setPadding(dp(4), dp(7), dp(4), dp(7));
        cell.setBackgroundResource(day.equals(LocalDate.now()) ? R.drawable.bg_day_current : R.drawable.bg_week_cell); cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1); p.setMargins(dp(1), 0, dp(1), 0); cell.setLayoutParams(p);
        TextView label = text(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN).substring(0, 1).toUpperCase() + "\n" + day.getDayOfMonth(), 12, INK, true);
        label.setGravity(Gravity.CENTER); cell.addView(label);
        if (list != null) for (Shift s : list) {
            boolean officialPalette = planningPortal == PlanningPortal.EFFIPLAN;
            TextView row = text(s.from.toLocalTime() + "\n" + s.to.toLocalTime(), 10, officialPalette ? shiftForeground(s) : s.color, true);
            if (officialPalette) { row.setBackgroundColor(s.color); row.setPadding(dp(2), dp(4), dp(2), dp(4)); }
            row.setGravity(Gravity.CENTER); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(7); row.setLayoutParams(rp); cell.addView(row);
        }
        return cell;
    }
    private void showDayShifts(LocalDate day, ArrayList<Shift> list) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (list == null || list.isEmpty()) content.addView(text(planningPortal == PlanningPortal.EFFIPLAN ? "Nessun turno disponibile · assenze non ancora importate" : "Nessun turno previsto · Riposo", 15, MUTED, false));
        else for (Shift shift : list) content.addView(shiftRow(shift));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AppDialogs.Builder(this).setTitle(capitalize(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN)) + " " + day.format(dateFormat)).setView(scroll).setPositiveButton("Chiudi", null).show();
    }
    private LocalDate weekStart(LocalDate date) { return planningPortal.weekStart(date); }
    private String weekNumber(LocalDate date) { return String.valueOf(planningPortal.weekNumber(date)); }
    private String teamWeeksLabel(LocalDate start) { StringBuilder label = new StringBuilder(); for (int i = 0; i < teamWeekCount; i++) { if (i > 0) label.append(", "); label.append("W").append(weekNumber(start.plusWeeks(i))); } return label.toString(); }
    private String shortRange(LocalDate monday) { return monday.format(DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)).toUpperCase() + " " + monday.getDayOfMonth() + "-" + monday.plusDays(6).getDayOfMonth(); }
    private String capitalize(String value) { return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private View empty(String headline, String body) { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(28), dp(36), dp(28), dp(36)); box.setBackgroundResource(R.drawable.bg_day); box.addView(text(headline, 18, INK, true)); TextView hint = text(body, 14, MUTED, false); hint.setGravity(Gravity.CENTER); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); hint.setLayoutParams(p); box.addView(hint); return box; }
    private TextView text(String value, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view; }
    private String hours(long minutes) { long h = minutes / 60, m = minutes % 60; return m == 0 ? h + " ore" : h + "h " + m + "m"; }
    private String shortHours(long minutes) { long h = minutes / 60, m = minutes % 60; return m == 0 ? h + "h" : h + "h" + m; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private int shiftForeground(Shift shift) { return shift.fontColor == null ? contrastText(shift.color) : shift.fontColor; }
    private int contrastText(int color) {
        double[] channels = { Color.red(color) / 255.0, Color.green(color) / 255.0, Color.blue(color) / 255.0 };
        for (int i = 0; i < channels.length; i++) channels[i] = channels[i] <= .04045 ? channels[i] / 12.92 : Math.pow((channels[i] + .055) / 1.055, 2.4);
        double luminance = .2126 * channels[0] + .7152 * channels[1] + .0722 * channels[2];
        return luminance > .179 ? Color.BLACK : Color.WHITE;
    }
    private int lighten(int color, float amount) { int r = (int) (Color.red(color) + (255 - Color.red(color)) * amount), g = (int) (Color.green(color) + (255 - Color.green(color)) * amount), b = (int) (Color.blue(color) + (255 - Color.blue(color)) * amount); return Color.rgb(r, g, b); }
    @Override protected void onDestroy() { destroyed = true; if (setupController != null) setupController.destroy(); if (activeCalendarDialog != null) activeCalendarDialog.dismiss(); pendingCalendarSelection = null; if (updater != null) updater.destroy(); if (workday != null) workday.destroy(); authGeneration++; authHandler.removeCallbacksAndMessages(null); requestGeneration++; executor.shutdownNow(); portal.destroy(); super.onDestroy(); }

    private static class Shift {
        LocalDateTime from, to; LocalDate date; String translation, abbreviation, collaborator, collaboratorId, typeId; int color; Integer fontColor; boolean absence, allDay, nonWorking;
        static Shift from(JSONObject object, Map<String, String> collaborators) throws Exception {
            Shift shift = new Shift(); shift.from = LocalDateTime.parse(object.getString("fromHour")); shift.to = LocalDateTime.parse(object.getString("toHour")); shift.absence = object.optBoolean("absence"); shift.allDay = object.optBoolean("allDay"); shift.nonWorking = object.optBoolean("nonWorking");
            shift.date = LocalDate.parse(object.getString("date").substring(0, 10));
            JSONObject typology = object.optJSONObject("hourTypology");
            shift.translation = typology == null ? object.optString("translation", "Turno") : typology.optString("translation", object.optString("translation", "Turno"));
            shift.abbreviation = typology == null ? object.optString("abbreviation", "") : typology.optString("abbreviation", object.optString("abbreviation", ""));
            shift.typeId = typology == null ? shift.translation : typology.optString("code", typology.optString("uuid", shift.translation));
            shift.collaborator = first(object, "collaboratorUuid", "uuidCollaborator"); JSONObject person = object.optJSONObject("collaborator"); if (shift.collaborator.isEmpty() && person != null) shift.collaborator = person.optString("uuid", "");
            shift.collaboratorId = shift.collaborator;
            String color = typology == null ? object.optString("hourTypologyColor", "#2563EB") : typology.optString("color", object.optString("hourTypologyColor", "#2563EB"));
            try { shift.color = Color.parseColor(color); } catch (Exception ignored) { shift.color = Color.rgb(37, 99, 235); }
            if (typology != null && typology.has("fontColor")) {
                try { shift.fontColor = Color.parseColor(typology.getString("fontColor")); } catch (Exception ignored) { shift.fontColor = null; }
            }
            if (collaborators.containsKey(shift.collaborator)) shift.collaborator = collaborators.get(shift.collaborator); else shift.collaborator = "";
            return shift;
        }
        String typeLabel() { return abbreviation.isEmpty() ? translation : abbreviation + "-" + translation; }
        String timeLabel() { return allDay ? "Tutta la giornata" : from.toLocalTime() + " – " + to.toLocalTime(); }
        String label() { String type = typeLabel(); return collaborator.isEmpty() ? type : collaborator + " · " + type; }
    }
}

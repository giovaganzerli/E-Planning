package it.giovaganzerli.planning;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.util.function.Consumer;

/** Full-page onboarding. The draft is not persisted until the explicit final confirmation. */
final class PortalSetupController {
    interface Host {
        void loadMembership(Consumer<SetupRules.Membership> success, Consumer<String> failure);
        void selectTeamManually(Runnable checking, Consumer<SetupRules.Membership> success, Consumer<String> failure);
        void configureRoles(SetupRules.Draft draft, Runnable changed);
        void chooseCalendar(Consumer<CalendarChoice> selected);
        boolean calendarPermissionGranted();
        void finish(SetupRules.Draft draft);
        void cancel();
        void changePortal();
    }
    private final Activity activity;
    private final Host host;
    private final SetupRules.Draft draft;
    private final LinearLayout content, calendarOptions;
    private final Button calendarButton, finishButton, rolesButton;
    private final TextView status, teamName;
    private SetupRules.Membership membership;
    private boolean active = true, loaded;
    PortalSetupController(Activity activity, String portalName, SetupRules.Draft draft, boolean canCancel, SetupRules.Membership initial, Host host) {
        this.activity = activity; this.host = host; this.draft = draft;
        content = activity.findViewById(R.id.portalSetupContent); content.removeAllViews();
        label("Configura il tuo planning", 28, true, content);
        label("Portale selezionato · " + portalName, 16, true, content);
        action("Cambia portale", content).setOnClickListener(v -> host.changePortal());
        label("Queste preferenze valgono solo per questo account e portale. Potrai modificarle dalle Impostazioni.", 14, false, content);
        label("1 · La tua squadra", 18, true, content);
        teamName = label("Rilevamento della squadra dal portale…", 16, true, content);
        status = label("", 13, false, content);
        label("La squadra non è corretta o non è stata trovata?", 13, false, content);
        action("Seleziona squadra manualmente", content).setOnClickListener(v -> host.selectTeamManually(
            () -> clearTeam("Verifica della squadra selezionata…", false), this::accept,
            message -> clearTeam("⚠ " + message, true)));
        label("La ricerca cambi resta vincolata alla squadra confermata in questa configurazione.", 13, false, content);
        rolesButton = action("Configura i ruoli dei colleghi", content); rolesButton.setEnabled(false);
        rolesButton.setOnClickListener(v -> host.configureRoles(draft, () -> {
            if (active) render();
        }));
        label("2 · Google Calendar", 18, true, content);
        CheckBox enabled = new CheckBox(activity); enabled.setText("Abilita sincronizzazione con Google Calendar"); enabled.setChecked(draft.calendarEnabled); content.addView(enabled);
        label("La sincronizzazione dei turni resta manuale e richiede conferma. Durante la configurazione non viene creato alcun evento.", 13, false, content);
        calendarOptions = new LinearLayout(activity); calendarOptions.setOrientation(LinearLayout.VERTICAL); content.addView(calendarOptions);
        calendarButton = action("Scegli calendario Google", calendarOptions);
        calendarButton.setOnClickListener(v -> host.chooseCalendar(chosen -> {
            if (!active) return;
            draft.calendarId = chosen.id; draft.calendarName = chosen.name; render();
        }));
        label("Avvisi predefiniti degli eventi", 15, true, calendarOptions);
        label("Puoi scegliere fino a due avvisi o disabilitarli entrambi.", 13, false, calendarOptions);
        for (int index = 0; index < 2; index++) addReminder(index);
        enabled.setOnCheckedChangeListener((button, checked) -> { draft.calendarEnabled = checked; render(); });
        finishButton = activity.findViewById(R.id.finishPortalSetup);
        finishButton.setOnClickListener(v -> {
            if (!active || !knownTeam() || !SetupRules.valid(draft)) return;
            if (draft.calendarEnabled && !host.calendarPermissionGranted()) {
                error("Consenti l’accesso al calendario oppure disattiva la sincronizzazione."); return;
            }
            host.finish(draft);
        });
        if (canCancel) action("Annulla", content).setOnClickListener(v -> host.cancel());
        render();
        if (initial == null) load(); else accept(initial);
    }
    void destroy() { active = false; }
    void error(String message) { if (active) status.setText(message); }
    private void clearTeam(String message, boolean failed) {
        if (!active) return;
        loaded = false; membership = null; SetupRules.clearTeam(draft);
        status.setTextColor(failed ? Color.rgb(185, 28, 28) : Color.rgb(25, 42, 74));
        error(message); render();
    }
    private boolean knownTeam() {
        return loaded && membership != null && membership.team.key.equals(draft.teamKey) && !draft.members.isEmpty();
    }
    private void load() {
        loaded = false; rolesButton.setEnabled(false); finishButton.setEnabled(false);
        error("Lettura dell’appartenenza e dei colleghi dal portale…");
        host.loadMembership(this::accept, message -> {
            if (!active) return;
            loaded = false; error(message); render();
        });
    }
    private void accept(SetupRules.Membership actual) {
        if (!active) return;
        membership = actual; draft.membership(actual); loaded = true;
        status.setTextColor(Color.rgb(25, 42, 74));
        error((actual.manual ? "Squadra selezionata manualmente" : "Squadra rilevata dalle API") + " · " + draft.members.size() + " collaboratori. I ruoli saranno salvati solo con Salva e continua.");
        render();
    }
    private void render() {
        teamName.setText(knownTeam() ? draft.teamTitle : "Squadra non ancora verificata");
        rolesButton.setEnabled(knownTeam());
        calendarOptions.setVisibility(draft.calendarEnabled ? View.VISIBLE : View.GONE);
        calendarButton.setText(draft.calendarId >= 0 ? "Calendario · " + draft.calendarName : "Scegli calendario Google");
        if (finishButton != null) {
            finishButton.setEnabled(knownTeam() && SetupRules.valid(draft));
            finishButton.setAlpha(finishButton.isEnabled() ? 1f : .45f);
        }
    }
    private void addReminder(int index) {
        LinearLayout row = new LinearLayout(activity); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        CheckBox enabled = new CheckBox(activity); enabled.setText(index == 0 ? "Primo avviso" : "Secondo avviso"); enabled.setChecked(draft.reminderEnabled[index]);
        row.addView(enabled, new LinearLayout.LayoutParams(0, -2, 1));
        Spinner time = new Spinner(activity);
        String[] labels = { "All’inizio", "5 minuti prima", "10 minuti prima", "15 minuti prima", "30 minuti prima", "1 ora prima", "2 ore prima", "4 ore prima", "12 ore prima", "1 giorno prima", "2 giorni prima", "1 settimana prima" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); time.setAdapter(adapter);
        for (int i = 0; i < SetupRules.REMINDER_TIMES.length; i++) if (SetupRules.REMINDER_TIMES[i] == draft.reminderMinutes[index]) time.setSelection(i);
        time.setEnabled(enabled.isChecked()); time.setContentDescription("Anticipo " + enabled.getText());
        row.addView(time, new LinearLayout.LayoutParams(0, dp(52), 1.3f));
        enabled.setOnCheckedChangeListener((button, checked) -> { draft.reminderEnabled[index] = checked; time.setEnabled(checked); });
        time.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { draft.reminderMinutes[index] = SetupRules.REMINDER_TIMES[position]; }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        calendarOptions.addView(row);
    }
    private TextView label(String text, int size, boolean bold, LinearLayout parent) {
        TextView view = new TextView(activity); view.setText(text); view.setTextSize(size); view.setTextColor(Color.rgb(25, 42, 74));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(12), 0, dp(8)); parent.addView(view); return view;
    }
    private Button action(String text, LinearLayout parent) {
        Button button = new Button(activity); button.setText(text); button.setTextSize(14); button.setAllCaps(false); button.setTextColor(Color.rgb(36, 59, 116));
        button.setBackgroundResource(R.drawable.bg_filter); button.setPadding(dp(10), dp(10), dp(10), dp(10)); button.setMinHeight(dp(52));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(10); parent.addView(button, params); return button;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}

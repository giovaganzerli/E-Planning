package it.giovaganzerli.planning;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

final class TeamRolesDialog {
    static final String[] LABELS = { "Vendita", "Workshop", "PFL", "Accoglienza", "Responsabile" };
    static void show(Activity activity, Map<String, String> members, String owner, Map<String, Integer> roles, Consumer<Map<String, Integer>> save) {
        float density = activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp = n -> Math.round(n * density);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp.applyAsInt(12), dp.applyAsInt(8), dp.applyAsInt(12), dp.applyAsInt(8));
        TextView help = new TextView(activity);
        help.setText("Scorri orizzontalmente. Vendita include Cassa, QCO, Cabine, Scarico e TTP. Responsabile esclude le altre abilitazioni e i cambi. Nessuna spunta = escluso.");
        panel.addView(help);
        HorizontalScrollView horizontal = new HorizontalScrollView(activity);
        LinearLayout table = new LinearLayout(activity); table.setOrientation(LinearLayout.VERTICAL); horizontal.addView(table);
        ScrollView vertical = new ScrollView(activity); vertical.addView(horizontal);
        panel.addView(vertical, new LinearLayout.LayoutParams(-1, (int) (activity.getResources().getDisplayMetrics().heightPixels * .5f)));
        LinearLayout headings = new LinearLayout(activity);
        ArrayList<String> labels = new ArrayList<>(); labels.add("Collaboratore"); labels.addAll(Arrays.asList(LABELS));
        for (int i = 0; i < labels.size(); i++) {
            TextView label = new TextView(activity); label.setText(labels.get(i)); label.setTextSize(12); label.setGravity(Gravity.CENTER);
            headings.addView(label, new LinearLayout.LayoutParams(dp.applyAsInt(i == 0 ? 150 : 94), dp.applyAsInt(52)));
        }
        table.addView(headings);
        ArrayList<String> ids = new ArrayList<>(members.keySet());
        ids.sort((a, b) -> a.equals(owner) ? b.equals(owner) ? 0 : -1 : b.equals(owner) ? 1 : members.get(a).compareToIgnoreCase(members.get(b)));
        Map<String, CheckBox[]> cells = new LinkedHashMap<>();
        for (String id : ids) {
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(id.equals(owner) ? Color.rgb(239, 246, 255) : Color.WHITE);
            TextView name = new TextView(activity); name.setText(members.get(id) + (id.equals(owner) ? " · TU" : "")); name.setTextSize(12);
            row.addView(name, new LinearLayout.LayoutParams(dp.applyAsInt(150), dp.applyAsInt(64)));
            int mask = SetupRules.normalizeRoles(roles.getOrDefault(id, 0));
            CheckBox[] boxes = new CheckBox[5];
            for (int i = 0; i < 5; i++) {
                CheckBox box = new CheckBox(activity); boxes[i] = box;
                box.setContentDescription(members.get(id) + ": " + LABELS[i]); box.setChecked((mask & (1 << i)) != 0); box.setEnabled(i == 4 || mask != 16);
                FrameLayout cell = new FrameLayout(activity);
                cell.addView(box, new FrameLayout.LayoutParams(dp.applyAsInt(48), dp.applyAsInt(48), Gravity.CENTER));
                row.addView(cell, new LinearLayout.LayoutParams(dp.applyAsInt(94), dp.applyAsInt(64)));
            }
            boxes[4].setOnCheckedChangeListener((button, checked) -> {
                for (int i = 0; i < 4; i++) { if (checked) boxes[i].setChecked(false); boxes[i].setEnabled(!checked); }
            });
            cells.put(id, boxes); table.addView(row);
        }
        new AppDialogs.Builder(activity).setTitle("Ruoli della mia squadra").setView(panel).setNegativeButton("Annulla", null)
            .setPositiveButton("Conferma ruoli", (dialog, which) -> {
                Map<String, Integer> result = new LinkedHashMap<>();
                for (String id : ids) {
                    int mask = 0; CheckBox[] boxes = cells.get(id);
                    for (int i = 0; i < 5; i++) if (boxes[i].isChecked()) mask |= 1 << i;
                    result.put(id, SetupRules.normalizeRoles(mask));
                }
                save.accept(result);
            }).show();
    }
}

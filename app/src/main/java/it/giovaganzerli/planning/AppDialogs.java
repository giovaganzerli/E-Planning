package it.giovaganzerli.planning;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared presentation for application dialogs; Android permission prompts stay unchanged. */
final class AppDialogs {
    private static final int BLUE = Color.rgb(36, 59, 116);

    static final class Builder extends AlertDialog.Builder {
        private final Context context;
        Builder(Context context) { super(context); this.context = context; setTitle("E-Planning"); }

        @Override public Builder setTitle(CharSequence title) {
            LinearLayout header = new LinearLayout(context);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(22), dp(22), dp(22), dp(18));
            header.setBackgroundColor(Color.rgb(232, 240, 255));
            ImageView icon = new ImageView(context);
            icon.setImageResource(R.drawable.ic_nav_week); icon.setColorFilter(BLUE);
            header.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView label = new TextView(context);
            label.setText(title); label.setTextColor(BLUE); label.setTextSize(20);
            label.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.leftMargin = dp(12); header.addView(label, params);
            setCustomTitle(header); return this;
        }

        @Override public AlertDialog create() {
            AlertDialog dialog = super.create();
            View decor = dialog.getWindow().getDecorView();
            decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { view.post(() -> decorate(dialog)); }
                @Override public void onViewDetachedFromWindow(View view) { }
            });
            return dialog;
        }
        @Override public AlertDialog show() { AlertDialog dialog = create(); dialog.show(); return dialog; }

        private void decorate(AlertDialog dialog) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE); background.setCornerRadius(dp(22));
            dialog.getWindow().setBackgroundDrawable(background);
            dialog.getWindow().getDecorView().setClipToOutline(true);
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(Color.rgb(71, 85, 105)); message.setTextSize(15);
                message.setLineSpacing(dp(4), 1);
            }
            for (int which : new int[] { AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL }) {
                Button button = dialog.getButton(which);
                if (button == null || button.getVisibility() != View.VISIBLE) continue;
                boolean primary = which == AlertDialog.BUTTON_POSITIVE;
                button.setAllCaps(false); button.setTextSize(13);
                button.setTextColor(primary ? Color.WHITE : BLUE);
                button.setBackgroundResource(primary ? R.drawable.bg_tab_active : R.drawable.bg_filter);
                button.setPadding(dp(16), dp(8), dp(16), dp(8));
                button.setMinimumHeight(dp(48));
                if (button.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) button.getLayoutParams();
                    params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.setMargins(dp(4), 0, dp(4), 0); button.setLayoutParams(params);
                }
                if (button.getParent() instanceof android.view.ViewGroup) {
                    android.view.ViewGroup bar = (android.view.ViewGroup) button.getParent();
                    android.view.ViewGroup.LayoutParams params = bar.getLayoutParams();
                    params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                    bar.setLayoutParams(params);
                    bar.setMinimumHeight(dp(72));
                    bar.setPadding(dp(12), dp(12), dp(12), dp(12));
                }
            }
        }
        private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

public class TryAnotherWayBottomSheet extends BottomSheet {

    public static final int METHOD_SIM = 1;
    public static final int METHOD_PHONE = 2;
    public static final int METHOD_PASSKEY = 3;

    public interface OnOptionSelectedListener {
        void onOptionSelected(int method);
    }

    public TryAnotherWayBottomSheet(Context context, int currentMethod, OnOptionSelectedListener listener) {
        super(context, false);
        fixNavigationBar();

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(24));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setText(LocaleController.getString(R.string.OtherWaysToSignIn));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray, resourcesProvider));
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setText(LocaleController.getString(R.string.OtherWaysSubtitle));
        linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));

        // Enforce the Golden Rule: Current method is always hidden
        if (currentMethod != METHOD_SIM) {
            addOption(linearLayout, R.drawable.msg2_devices,
                LocaleController.getString(R.string.OptionSimTitle),
                LocaleController.getString(R.string.OptionSimSubtitle),
                METHOD_SIM, listener);
        }

        if (currentMethod != METHOD_PASSKEY && BuildVars.SUPPORTS_PASSKEYS && Build.VERSION.SDK_INT >= 28) {
            addOption(linearLayout, R.drawable.msg2_permissions,
                LocaleController.getString(R.string.OptionPasskeyTitle),
                LocaleController.getString(R.string.OptionPasskeySubtitle),
                METHOD_PASSKEY, listener);
        }

        if (currentMethod != METHOD_PHONE) {
            addOption(linearLayout, R.drawable.msg2_call_earpiece,
                LocaleController.getString(R.string.OptionPhoneTitle),
                LocaleController.getString(R.string.OptionPhoneSubtitle),
                METHOD_PHONE, listener);
        }

        setCustomView(linearLayout);
    }

    private void addOption(LinearLayout container, int iconResId, String title, String subtitle, int method, OnOptionSelectedListener listener) {
        Context context = container.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        row.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(iconResId);
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), PorterDuff.Mode.SRC_IN));
        row.addView(iconView, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleTextView = new TextView(context);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleTextView.setText(title);
        textLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        TextView subtitleTextView = new TextView(context);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        subtitleTextView.setText(subtitle);
        textLayout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        row.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        row.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onOptionSelected(method);
            }
        });

        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
    }
}

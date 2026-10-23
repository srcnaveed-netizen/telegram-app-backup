package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.provider.ContactsContract;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SlideView;
import org.telegram.ui.Components.TryAnotherWayBottomSheet;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SimVerificationView extends SlideView {

    public static final int STATE_LOADING = 0;
    public static final int STATE_ACCOUNT_FOUND = 1;
    public static final int STATE_NO_ACCOUNT = 2;

    public static class AccountItem {
        public long id;
        public String displayName = "";
        public String userName = "";
        public String phoneNumber = "";
        public TLRPC.User user;
        public boolean selected;
        public View cardView;
        public ImageView checkImageView;
    }

    private final LoginActivity parentActivity;
    private int currentState = STATE_LOADING;

    private TextView titleView;
    private TextView subtitleView;

    // Loading State Container
    private LinearLayout loadingContainer;
    private RadialProgressView radialProgressView;
    private TextView loadingTextView;

    // Content Containers
    private LinearLayout cardsContainer;
    private FrameLayout continueButton;
    private LinearLayout continueButtonContent;
    private TextView continueButtonText;
    private RadialProgressView continueProgressView;
    private boolean isVerifying = false;
    private FrameLayout tryAnotherWayPillButton;

    private final ArrayList<AccountItem> items = new ArrayList<>();
    private String detectedCountryCode = "";
    private boolean isDetecting = false;

    public SimVerificationView(Context context, LoginActivity activity) {
        super(context);
        this.parentActivity = activity;

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));

        // 1. Header: Title & Subtitle (Clean Telegram Typography)
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(LocaleController.getString(R.string.YourPhone));
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 8));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(LocaleController.getString(R.string.ConfirmPhoneSubtitle));
        addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 28));

        // 2. Loading State Indicator (Centred Telegram Radial Progress Spinner)
        loadingContainer = new LinearLayout(context);
        loadingContainer.setOrientation(VERTICAL);
        loadingContainer.setGravity(Gravity.CENTER);
        loadingContainer.setPadding(0, AndroidUtilities.dp(28), 0, AndroidUtilities.dp(28));

        radialProgressView = new RadialProgressView(context);
        radialProgressView.setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        radialProgressView.setSize(AndroidUtilities.dp(40));
        loadingContainer.addView(radialProgressView, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        loadingTextView = new TextView(context);
        loadingTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        loadingTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        loadingTextView.setGravity(Gravity.CENTER);
        loadingTextView.setText("Searching for SIM cards...");
        loadingContainer.addView(loadingTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        addView(loadingContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));

        // 3. Selectable Cards Container (For Found Account or Informative No-Account Card)
        cardsContainer = new LinearLayout(context);
        cardsContainer.setOrientation(VERTICAL);
        cardsContainer.setVisibility(View.GONE);
        addView(cardsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));

        // 4. Primary Action Button (Full Telegram Pill Capsule: 50dp height, 25dp radius) with inline spinner
        continueButton = new FrameLayout(context);
        continueButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(25),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)
        ));
        continueButton.setOnClickListener(v -> onContinuePressed());
        continueButton.setVisibility(View.GONE);

        continueButtonContent = new LinearLayout(context);
        continueButtonContent.setOrientation(HORIZONTAL);
        continueButtonContent.setGravity(Gravity.CENTER);

        continueProgressView = new RadialProgressView(context);
        continueProgressView.setSize(AndroidUtilities.dp(20));
        continueProgressView.setProgressColor(0xFFFFFFFF);
        continueProgressView.setVisibility(View.GONE);
        continueButtonContent.addView(continueProgressView, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        continueButtonText = new TextView(context);
        continueButtonText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        continueButtonText.setTypeface(AndroidUtilities.bold());
        continueButtonText.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        continueButtonText.setGravity(Gravity.CENTER);
        continueButtonText.setText(LocaleController.getString(R.string.Continue));
        continueButtonContent.addView(continueButtonText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        continueButton.addView(continueButtonContent, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        addView(continueButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 0, 0, 16));

        // 5. Secondary Action: "TRY ANOTHER WAY ›" (Matching Pill Button with Translucent Selector & Native Ripple)
        buildTryAnotherWayButton(context);
        addView(tryAnotherWayPillButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 0, 0, 8));

        // SIM detection & backend account matching runs asynchronously when view is shown (onShow)
    }

    private void buildTryAnotherWayButton(Context context) {
        tryAnotherWayPillButton = new FrameLayout(context);
        int blueColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);

        // Translucent background with ripple feedback: ~10% opacity normal, ~20% pressed
        int normalBgColor = (blueColor & 0x00FFFFFF) | 0x18000000;
        int pressedBgColor = (blueColor & 0x00FFFFFF) | 0x32000000;
        tryAnotherWayPillButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(25),
                normalBgColor,
                pressedBgColor
        ));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(HORIZONTAL);
        contentLayout.setGravity(Gravity.CENTER);

        TextView labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        labelView.setTypeface(AndroidUtilities.bold());
        labelView.setTextColor(blueColor);
        labelView.setGravity(Gravity.CENTER);
        labelView.setText(LocaleController.getString(R.string.TryAnotherWay).toUpperCase());
        contentLayout.addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        ImageView arrowView = new ImageView(context);
        arrowView.setImageResource(R.drawable.arrow_more);
        arrowView.setColorFilter(new PorterDuffColorFilter(blueColor, PorterDuff.Mode.SRC_IN));
        contentLayout.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 8, 1, 0, 0));

        tryAnotherWayPillButton.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        tryAnotherWayPillButton.setOnClickListener(v -> openTryAnotherWay());
    }

    public boolean hasDetectedSim() {
        if (!items.isEmpty()) {
            return true;
        }
        try {
            Context ctx = ApplicationLoader.applicationContext;
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && (tm.getSimState() == TelephonyManager.SIM_STATE_READY || tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE)) {
                return true;
            }
        } catch (Throwable ignore) {}
        return false;
    }

    @Override
    public void onShow() {
        super.onShow();
        startDetection();
    }

    public void startDetection() {
        if (isDetecting) return;
        isDetecting = true;
        setUiState(STATE_LOADING);

        final long startDetectionTime = android.os.SystemClock.uptimeMillis();
        Utilities.globalQueue.postRunnable(() -> {
            performFullDetectionAndMatching(startDetectionTime);
        });
    }

    private void performFullDetectionAndMatching(final long startDetectionTime) {
        Context ctx = ApplicationLoader.applicationContext;
        final ArrayList<String> candidatePhones = new ArrayList<>();

        // 1. Retrieve Country Calling Code from SIM
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String countryIso = tm.getSimCountryIso();
                if (!TextUtils.isEmpty(countryIso)) {
                    String callingCode = PhoneFormat.getInstance().callingCodeForCountryCode(countryIso.toUpperCase());
                    if (!TextUtils.isEmpty(callingCode)) {
                        detectedCountryCode = "+" + callingCode;
                    }
                }
            }
        } catch (Throwable ignore) {}

        // 2. Query Android SubscriptionManager / TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager sm = (SubscriptionManager) ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm != null) {
                    List<SubscriptionInfo> subList = sm.getActiveSubscriptionInfoList();
                    if (subList != null) {
                        for (SubscriptionInfo info : subList) {
                            String num = null;
                            if (Build.VERSION.SDK_INT >= 33) {
                                try {
                                    num = sm.getPhoneNumber(info.getSubscriptionId());
                                } catch (Throwable ignore) {}
                            }
                            if (TextUtils.isEmpty(num) && info.getNumber() != null && !info.getNumber().isEmpty()) {
                                num = info.getNumber();
                            }
                            if (!TextUtils.isEmpty(num)) {
                                String clean = num.startsWith("+") ? num : "+" + num;
                                if (!candidatePhones.contains(clean)) candidatePhones.add(clean);
                            }
                        }
                    }
                }
            } catch (Throwable ignore) {}
        }

        // 3. Fallback to TelephonyManager Line1Number
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String line1 = tm.getLine1Number();
                if (!TextUtils.isEmpty(line1)) {
                    String clean = line1.startsWith("+") ? line1 : "+" + line1;
                    if (!candidatePhones.contains(clean)) candidatePhones.add(clean);
                }
            }
        } catch (Throwable ignore) {}

        // 4. Saved Preferences Fallback
        try {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            String savedPhone = prefs.getString("login_phone", null);
            if (TextUtils.isEmpty(savedPhone)) {
                savedPhone = prefs.getString("last_phone", null);
            }
            if (TextUtils.isEmpty(savedPhone)) {
                savedPhone = prefs.getString("sim_phone", null);
            }
            if (!TextUtils.isEmpty(savedPhone)) {
                String clean = savedPhone.startsWith("+") ? savedPhone : "+" + savedPhone;
                if (!candidatePhones.contains(clean)) candidatePhones.add(clean);
            }
        } catch (Throwable ignore) {}

        final ArrayList<AccountItem> matchedAccounts = new ArrayList<>();

        // 5. Check Local Telegram UserConfig Accounts (0..3)
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig config = AccountInstance.getInstance(a).getUserConfig();
                if (config != null && config.isClientActivated() && config.getCurrentUser() != null) {
                    TLRPC.User u = config.getCurrentUser();
                    AccountItem item = new AccountItem();
                    item.id = u.id;
                    item.user = u;
                    item.displayName = UserObject.getUserName(u);
                    if (TextUtils.isEmpty(item.displayName)) {
                        item.displayName = ContactsController.formatName(u.first_name, u.last_name);
                    }
                    if (TextUtils.isEmpty(item.displayName)) {
                        item.displayName = LocaleController.getString(R.string.AccountSettings);
                    }
                    if (!TextUtils.isEmpty(u.phone)) {
                        item.phoneNumber = PhoneFormat.getInstance().format("+" + u.phone);
                    }
                    matchedAccounts.add(item);
                }
            }
        } catch (Throwable ignore) {}

        // 6. Connect to Backend to Match Candidate Phone Numbers with Real Users
        for (String phoneCandidate : candidatePhones) {
            boolean alreadyMatched = false;
            for (AccountItem item : matchedAccounts) {
                if (PhoneFormat.stripExceptNumbers(phoneCandidate).equals(PhoneFormat.stripExceptNumbers(item.phoneNumber))) {
                    alreadyMatched = true;
                    break;
                }
            }
            if (alreadyMatched) continue;

            AccountItem backendItem = lookupAccountFromBackend(phoneCandidate);
            if (backendItem != null) {
                matchedAccounts.add(backendItem);
            }
        }

        // Post-processing on Main UI Thread with minimum 1.5s search duration
        long elapsed = android.os.SystemClock.uptimeMillis() - startDetectionTime;
        long remainingDelay = Math.max(0, 1500L - elapsed);

        AndroidUtilities.runOnUIThread(() -> {
            isDetecting = false;
            items.clear();
            items.addAll(matchedAccounts);

            if (!items.isEmpty()) {
                items.get(0).selected = true;
                setUiState(STATE_ACCOUNT_FOUND);
            } else {
                setUiState(STATE_NO_ACCOUNT);
            }
        }, remainingDelay);
    }

    private AccountItem lookupAccountFromBackend(String phone) {
        String[] hosts = new String[]{"192.168.1.63:5050", "192.168.1.63:8088", "10.0.2.2:5050", "10.0.2.2:8088", "127.0.0.1:5050", "127.0.0.1:8088"};
        for (String host : hosts) {
            HttpURLConnection conn = null;
            try {
                String cleanPhone = PhoneFormat.stripExceptNumbers(phone);
                String urlStr = "http://" + host + "/api/v1/client/user/lookup-phone?phone=" + URLEncoder.encode(cleanPhone, "UTF-8");
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optBoolean("found", false) && json.has("user")) {
                        JSONObject u = json.getJSONObject("user");
                        AccountItem item = new AccountItem();
                        item.id = u.optLong("userId", 1);
                        item.displayName = u.optString("displayName", "");
                        item.userName = u.optString("userName", "");
                        String rawPhone = u.optString("phoneNumber", phone);
                        item.phoneNumber = PhoneFormat.getInstance().format(rawPhone.startsWith("+") ? rawPhone : "+" + rawPhone);
                        return item;
                    }
                }
            } catch (Throwable t) {
                FileLog.e("SimVerificationView backend lookup failed for host " + host + ": " + t.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private void setUiState(int state) {
        currentState = state;

        if (state == STATE_LOADING) {
            loadingContainer.setVisibility(View.VISIBLE);
            cardsContainer.setVisibility(View.GONE);
            continueButton.setVisibility(View.GONE);
            titleView.setText(LocaleController.getString(R.string.YourPhone));
            subtitleView.setText("Searching for SIM cards...");
        } else if (state == STATE_ACCOUNT_FOUND) {
            loadingContainer.setVisibility(View.GONE);
            cardsContainer.setVisibility(View.VISIBLE);
            continueButton.setVisibility(View.VISIBLE);
            titleView.setText(LocaleController.getString(R.string.YourPhone));
            subtitleView.setText(LocaleController.getString(R.string.ConfirmPhoneSubtitle));
            rebuildAccountCards();
            updateContinueButtonText();
        } else if (state == STATE_NO_ACCOUNT) {
            loadingContainer.setVisibility(View.GONE);
            cardsContainer.setVisibility(View.VISIBLE);
            continueButton.setVisibility(View.VISIBLE);
            titleView.setText(LocaleController.getString(R.string.YourPhone));
            subtitleView.setText("No account found on this SIM card.");
            buildNoAccountCard();
            updateContinueButtonText();
        }
    }

    private void rebuildAccountCards() {
        cardsContainer.removeAllViews();
        Context context = getContext();

        for (int i = 0; i < items.size(); i++) {
            final AccountItem item = items.get(i);

            LinearLayout cardView = new LinearLayout(context);
            cardView.setOrientation(HORIZONTAL);
            cardView.setGravity(Gravity.CENTER_VERTICAL);
            cardView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            cardView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    AndroidUtilities.dp(16),
                    Theme.getColor(Theme.key_windowBackgroundGray),
                    Theme.getColor(Theme.key_listSelector)
            ));

            // Profile Picture / Avatar
            BackupImageView avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(AndroidUtilities.dp(24));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(18));
            if (item.user != null) {
                avatarDrawable.setInfo(item.user);
                avatarView.setForUserOrChat(item.user, avatarDrawable);
            } else {
                avatarDrawable.setInfo(item.id, item.displayName, null);
                avatarView.setImageDrawable(avatarDrawable);
            }
            cardView.addView(avatarView, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

            // Name & Phone
            LinearLayout infoLayout = new LinearLayout(context);
            infoLayout.setOrientation(VERTICAL);

            TextView nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameView.setText(item.displayName);
            nameView.setMaxLines(1);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            infoLayout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));

            TextView phoneView = new TextView(context);
            phoneView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            phoneView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            phoneView.setText(item.phoneNumber);
            phoneView.setMaxLines(1);
            infoLayout.addView(phoneView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            cardView.addView(infoLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

            // Selection Checkmark Badge
            ImageView checkImageView = new ImageView(context);
            checkImageView.setImageResource(R.drawable.account_check);
            checkImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
            checkImageView.setVisibility(item.selected ? View.VISIBLE : View.INVISIBLE);
            cardView.addView(checkImageView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            item.cardView = cardView;
            item.checkImageView = checkImageView;

            cardView.setOnClickListener(v -> selectItem(item));
            cardsContainer.addView(cardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        }
    }

    private void buildNoAccountCard() {
        cardsContainer.removeAllViews();
        Context context = getContext();

        LinearLayout cardView = new LinearLayout(context);
        cardView.setOrientation(HORIZONTAL);
        cardView.setGravity(Gravity.CENTER_VERTICAL);
        cardView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        cardView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundGray),
                Theme.getColor(Theme.key_listSelector)
        ));

        // Circular Avatar Badge with SIM Initials
        BackupImageView iconView = new BackupImageView(context);
        iconView.setRoundRadius(AndroidUtilities.dp(24));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(16));
        avatarDrawable.setInfo(999, "SIM", null);
        iconView.setImageDrawable(avatarDrawable);
        cardView.addView(iconView, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        // Text Layout
        LinearLayout infoLayout = new LinearLayout(context);
        infoLayout.setOrientation(VERTICAL);

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText("No account found on this SIM");
        infoLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView desc = new TextView(context);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        desc.setText("Enter your phone number manually to sign in or create an account.");
        infoLayout.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        cardView.addView(infoLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.arrow_more);
        chevron.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
        cardView.addView(chevron, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        cardView.setOnClickListener(v -> {
            if (parentActivity != null) {
                parentActivity.setPage(0, true, null, true);
            }
        });
        cardsContainer.addView(cardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void selectItem(AccountItem selected) {
        for (AccountItem it : items) {
            it.selected = (it == selected);
            if (it.checkImageView != null) {
                it.checkImageView.setVisibility(it.selected ? View.VISIBLE : View.INVISIBLE);
            }
        }
        updateContinueButtonText();
    }

    private AccountItem getSelectedItem() {
        for (AccountItem it : items) {
            if (it.selected) {
                return it;
            }
        }
        return items.isEmpty() ? null : items.get(0);
    }

    private void updateContinueButtonText() {
        if (continueButtonText == null) return;
        if (isVerifying) {
            AccountItem selected = getSelectedItem();
            if (selected != null && !TextUtils.isEmpty(selected.displayName)) {
                continueButtonText.setText("VERIFYING AS " + selected.displayName.toUpperCase() + "...");
            } else {
                continueButtonText.setText("VERIFYING...");
            }
            return;
        }
        if (currentState == STATE_NO_ACCOUNT) {
            continueButtonText.setText("ENTER PHONE NUMBER");
            return;
        }
        AccountItem selected = getSelectedItem();
        if (selected != null && !TextUtils.isEmpty(selected.displayName)) {
            continueButtonText.setText("CONTINUE AS " + selected.displayName.toUpperCase());
        } else {
            continueButtonText.setText(LocaleController.getString(R.string.Continue).toUpperCase());
        }
    }

    public void setVerifying(boolean verifying) {
        isVerifying = verifying;
        if (continueButton != null) {
            continueButton.setEnabled(!verifying);
            continueButton.setAlpha(verifying ? 0.85f : 1.0f);
        }
        if (continueProgressView != null) {
            continueProgressView.setVisibility(verifying ? View.VISIBLE : View.GONE);
        }
        updateContinueButtonText();
    }

    public void resetContinueButton() {
        setVerifying(false);
    }

    private void onContinuePressed() {
        if (parentActivity == null || isVerifying) return;
        if (currentState == STATE_NO_ACCOUNT || items.isEmpty()) {
            parentActivity.setPage(0, true, null, true);
            return;
        }

        AccountItem item = getSelectedItem();
        if (item != null && !TextUtils.isEmpty(item.phoneNumber)) {
            setVerifying(true);
            parentActivity.startAuthWithPhoneNumber(item.phoneNumber);
            postDelayed(() -> {
                if (isVerifying) {
                    setVerifying(false);
                }
            }, 15000);
        } else {
            parentActivity.setPage(0, true, null, true);
        }
    }

    private void openTryAnotherWay() {
        if (parentActivity == null) return;
        TryAnotherWayBottomSheet sheet = new TryAnotherWayBottomSheet(
                getContext(),
                TryAnotherWayBottomSheet.METHOD_SIM,
                method -> {
                    if (method == TryAnotherWayBottomSheet.METHOD_PHONE) {
                        parentActivity.setPage(0, true, null, true);
                    } else if (method == TryAnotherWayBottomSheet.METHOD_PASSKEY) {
                        parentActivity.requestPasskey(true);
                    }
                }
        );
        sheet.show();
    }

    @Override
    public String getHeaderName() {
        return LocaleController.getString(R.string.YourPhone);
    }

    @Override
    public boolean needBackButton() {
        return false;
    }
}

package org.telegram.ui.Components;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public class CustomerSupportController {
    public static final long SUPPORT_USER_ID = 333000L;

    public static final int STEP_CATEGORIES = 0;
    public static final int STEP_SUBCATEGORIES = 1;
    public static final int STEP_EXPLANATION = 2;
    public static final int STEP_ACTIVE_TICKET = 3;

    private int currentStep = STEP_CATEGORIES;
    private String selectedCategory;
    private String selectedSubcategory;
    private boolean awaitingCustomExplanation;

    private final ChatActivity chatActivity;
    private final ChatActivityEnterView enterView;
    private final int currentAccount;

    public CustomerSupportController(ChatActivity chatActivity, ChatActivityEnterView enterView, int currentAccount) {
        this.chatActivity = chatActivity;
        this.enterView = enterView;
        this.currentAccount = currentAccount;
    }

    public static boolean isSupportChat(long dialogId) {
        return dialogId == SUPPORT_USER_ID;
    }

    public void init() {
        if (!isSupportChat(chatActivity.getDialogId())) {
            return;
        }
        showStep(STEP_CATEGORIES);
    }

    public void showStep(int step) {
        this.currentStep = step;
        MessageObject supportMsg = buildKeyboardMessage(step);
        if (enterView != null) {
            enterView.setButtons(supportMsg, true, false);
        }
    }

    public boolean onButtonPressed(TL_keyboard.KeyboardButtonProto button) {
        if (button == null || !(button instanceof TL_keyboard.TL_keyboardButton)) {
            return false;
        }
        String text = ((TL_keyboard.TL_keyboardButton) button).text;
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        if (text.equals("« Back to main menu") || text.equals("« Back to categories")) {
            awaitingCustomExplanation = false;
            if (text.equals("« Back to main menu")) {
                selectedCategory = null;
                selectedSubcategory = null;
                showStep(STEP_CATEGORIES);
            } else {
                selectedSubcategory = null;
                showStep(STEP_SUBCATEGORIES);
            }
            return true;
        }

        if (text.contains("Human Agent") || text.contains("Talk to a Human Agent")) {
            awaitingCustomExplanation = false;
            sendTicketCard("Live Human Support", "Request Human Agent", "User requested immediate live assistance from a support agent.");
            insertLocalSupportMessage("Your request has been prioritized for our human support team. An agent will reply to you directly in this chat shortly.");
            showStep(STEP_ACTIVE_TICKET);
            return true;
        }

        if (currentStep == STEP_CATEGORIES) {
            selectedCategory = text;
            insertLocalSupportMessage("Choose an option with the buttons below.");
            showStep(STEP_SUBCATEGORIES);
            return true;
        } else if (currentStep == STEP_SUBCATEGORIES) {
            selectedSubcategory = text;
            insertLocalSupportMessage("Please explain your issue below or choose a quick description:");
            showStep(STEP_EXPLANATION);
            return true;
        } else if (currentStep == STEP_EXPLANATION) {
            if (text.contains("Type my own explanation")) {
                awaitingCustomExplanation = true;
                if (enterView != null) {
                    enterView.openKeyboard();
                    if (enterView.getEditField() != null) {
                        enterView.getEditField().requestFocus();
                        enterView.setFieldText("");
                    }
                }
                return true;
            } else {
                awaitingCustomExplanation = false;
                sendTicketCard(selectedCategory, selectedSubcategory, text);
                insertLocalSupportMessage("Thank you! We have received your inquiry. A support specialist will respond to you right here shortly.");
                showStep(STEP_ACTIVE_TICKET);
                return true;
            }
        } else if (currentStep == STEP_ACTIVE_TICKET) {
            if (text.contains("Add more details")) {
                awaitingCustomExplanation = true;
                if (enterView != null) {
                    enterView.openKeyboard();
                    if (enterView.getEditField() != null) {
                        enterView.getEditField().requestFocus();
                    }
                }
                return true;
            } else if (text.contains("Submit another inquiry") || text.contains("Start new request")) {
                selectedCategory = null;
                selectedSubcategory = null;
                awaitingCustomExplanation = false;
                showStep(STEP_CATEGORIES);
                return true;
            }
        }

        return false;
    }

    public boolean onTextMessageSent(CharSequence text) {
        if (awaitingCustomExplanation && selectedCategory != null) {
            awaitingCustomExplanation = false;
            String explanation = text.toString();
            sendTicketCard(selectedCategory, selectedSubcategory != null ? selectedSubcategory : "General Topic", explanation);
            insertLocalSupportMessage("Thank you! We have received your details. A support specialist will review your request and reply to you here shortly.");
            showStep(STEP_ACTIVE_TICKET);
            return true;
        }
        return false;
    }

    private void sendTicketCard(String category, String subcategory, String details) {
        String card = "📋 **Customer Support Request**\n" +
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "📁 **Category:** " + category + "\n" +
                "🏷️ **Topic:** " + subcategory + "\n" +
                "📝 **Details:**\n" + details + "\n" +
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "⏱️ *Status: Open • Support team will respond here*";

        CharSequence[] msgArr = new CharSequence[]{card};
        ArrayList<TLRPC.MessageEntity> entities = chatActivity.getMediaDataController().getEntities(msgArr, true);

        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                msgArr[0].toString(),
                SUPPORT_USER_ID,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                true,
                0,
                0,
                null,
                false
        );
        params.entities = entities;
        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
    }

    public void insertLocalSupportMessage(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            TLRPC.TL_updateShortMessage shortMsg = new TLRPC.TL_updateShortMessage();
            shortMsg.id = UserConfig.getInstance(currentAccount).getNewMessageId();
            shortMsg.user_id = SUPPORT_USER_ID;
            shortMsg.message = text;
            shortMsg.pts = MessagesStorage.getInstance(currentAccount).getLastPtsValue();
            shortMsg.pts_count = 1;
            shortMsg.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            shortMsg.out = false;
            MessagesController.getInstance(currentAccount).processUpdates(shortMsg, false);
        }, 300);
    }

    private MessageObject buildKeyboardMessage(int step) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = -999999;
        message.dialog_id = SUPPORT_USER_ID;
        message.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = SUPPORT_USER_ID;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = SUPPORT_USER_ID;

        TLRPC.TL_replyKeyboardMarkup markup = new TLRPC.TL_replyKeyboardMarkup();
        markup.resize = true;
        markup.is_persistent = true;
        markup.rows = new ArrayList<>();

        ArrayList<String> buttons = getButtonList(step);
        for (String label : buttons) {
            TL_keyboard.KeyboardButtonRow row = new TL_keyboard.KeyboardButtonRow();
            TL_keyboard.TL_keyboardButton btn = new TL_keyboard.TL_keyboardButton();
            btn.text = label;
            btn.type = new TL_keyboard.TL_buttonTypeDefault();
            row.buttons.add(btn);
            markup.rows.add(row);
        }

        message.reply_markup = markup;
        return new MessageObject(currentAccount, message, false, false);
    }

    private ArrayList<String> getButtonList(int step) {
        ArrayList<String> list = new ArrayList<>();
        if (step == STEP_CATEGORIES) {
            list.add("I want to know how to ...");
            list.add("Account & Login Issues");
            list.add("How to delete account?");
            list.add("Report a Problem or Bug");
            list.add("Talk to a Human Agent");
        } else if (step == STEP_SUBCATEGORIES) {
            if ("Account & Login Issues".equals(selectedCategory)) {
                list.add("Cannot receive SMS code");
                list.add("Forgot 2FA password");
                list.add("Change phone number");
                list.add("Session terminated unexpectedly");
            } else if ("I want to know how to ...".equals(selectedCategory)) {
                list.add("How to delete account?");
                list.add("How to change phone number?");
                list.add("How to set up Two-Step Verification?");
                list.add("How to manage active sessions?");
            } else if ("How to delete account?".equals(selectedCategory)) {
                list.add("I no longer want to use Synch");
                list.add("Just curious, I'm not deleting it now");
                list.add("I want to fix a problem");
                list.add("Another reason");
            } else if ("Report a Problem or Bug".equals(selectedCategory)) {
                list.add("App crashes or freezes");
                list.add("Messages not sending or delivering");
                list.add("Voice or video call problem");
                list.add("UI or visual glitch");
            } else {
                list.add("General inquiry");
                list.add("Need assistance");
            }
            list.add("« Back to main menu");
        } else if (step == STEP_EXPLANATION) {
            list.add("✏️ Type my own explanation");
            if ("Cannot receive SMS code".equals(selectedSubcategory)) {
                list.add("SMS code is not arriving on my phone");
                list.add("Code was sent to another device that is offline");
            } else if ("Forgot 2FA password".equals(selectedSubcategory)) {
                list.add("I forgot my cloud password and have no recovery email");
                list.add("Recovery email is not receiving reset code");
            } else if ("I no longer want to use Synch".equals(selectedSubcategory)) {
                list.add("Please guide me to delete my account immediately");
            } else {
                list.add("I need help resolving this issue on my account");
                list.add("Experiencing this problem since the last update");
            }
            list.add("« Back to categories");
        } else if (step == STEP_ACTIVE_TICKET) {
            list.add("💬 Add more details");
            list.add("🎧 Talk to a Human Agent");
            list.add("🔄 Submit another inquiry");
        }
        return list;
    }
}

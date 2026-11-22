package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

import org.telegram.tgnet.TLRPC;
import org.telegram.utils.settings.SharedSettings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small helpers used by ButterGram features. Kept in one place so hooks in Telegram code stay one-liners. */
public final class ButterGram {
    private ButterGram() {
    }

    // ---------------------------------------------------------------------------------------------
    // Deleted messages
    // ---------------------------------------------------------------------------------------------

    private static SharedPreferences deletedPrefs;

    private static synchronized SharedPreferences deleted() {
        if (deletedPrefs == null) {
            deletedPrefs = ApplicationLoader.applicationContext.getSharedPreferences("buttergram_deleted", Context.MODE_PRIVATE);
        }
        return deletedPrefs;
    }

    private static String messageKey(int account, long channelId, int id) {
        return account + "_" + channelId + "_" + id;
    }

    /** Remember that messages were deleted by someone else, so a trash mark can be drawn on them. */
    public static void markDeleted(int account, long channelId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            SharedPreferences.Editor editor = deleted().edit();
            for (int i = 0; i < ids.size(); i++) {
                editor.putBoolean(messageKey(account, channelId, ids.get(i)), true);
            }
            editor.apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static int undoSendSeconds() {
        return org.telegram.utils.settings.SharedSettings.buttergramUndoSend.get();
    }

    /** Holds the send back for the configured time; Undo runs restore instead, leaving the chat or timeout runs send. */
    public static void sendWithUndo(org.telegram.ui.ActionBar.BaseFragment fragment, Runnable send, Runnable restore) {
        int seconds = undoSendSeconds();
        if (seconds <= 0 || fragment == null || fragment.getParentActivity() == null) {
            send.run();
            return;
        }
        org.telegram.ui.Components.BulletinFactory.of(fragment)
            .createUndoBulletin(LocaleController.getString(R.string.ButterGramUndoSending), restore, send)
            .setDuration(seconds * 1000)
            .show();
    }

    /** Id of the fake messages used by the settings previews. */
    public static final int PREVIEW_MESSAGE_ID = -7777;

    public static boolean isDeleted(MessageObject message) {
        if (message == null || message.messageOwner == null || !SharedSettings.buttergramMarkDeleted.get()) {
            return false;
        }
        if (message.messageOwner.id == PREVIEW_MESSAGE_ID) {
            return true;
        }
        try {
            long channelId = message.messageOwner.peer_id != null ? message.messageOwner.peer_id.channel_id : 0;
            return deleted().getBoolean(messageKey(message.currentAccount, channelId, message.getId()), false);
        } catch (Throwable e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Time and phone
    // ---------------------------------------------------------------------------------------------

    private static SimpleDateFormat secondsFormat;
    private static boolean secondsFormat24;

    /** Time of day for a message bubble, with seconds when "exact time" is on. */
    public static synchronized String formatTime(long dateSeconds) {
        if (!SharedSettings.buttergramExactTime.get()) {
            return LocaleController.getInstance().getFormatterDay().format(dateSeconds * 1000);
        }
        boolean is24 = DateFormat.is24HourFormat(ApplicationLoader.applicationContext);
        if (secondsFormat == null || secondsFormat24 != is24) {
            secondsFormat24 = is24;
            secondsFormat = new SimpleDateFormat(is24 ? "HH:mm:ss" : "h:mm:ss a", Locale.getDefault());
        }
        return secondsFormat.format(dateSeconds * 1000);
    }

    /** Replaces every digit of an already formatted phone number except the first one. */
    public static String maskPhone(String phone) {
        if (phone == null || !SharedSettings.buttergramHidePhone.get()) {
            return phone;
        }
        StringBuilder sb = new StringBuilder(phone.length());
        boolean first = true;
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (c >= '0' && c <= '9') {
                if (first) {
                    sb.append(c);
                    first = false;
                } else {
                    sb.append('•');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------------------
    // Offensive text warning
    // ---------------------------------------------------------------------------------------------

    private static final String[] OFFENSIVE = {
        // Russian
        "хуй", "хуя", "хуе", "хуи", "пизд", "ебан", "ебат", "ебал", "ебуч", "ебла", "ебло", "уеб", "заеб", "наеб", "съеб", "бляд", "блят",
        "сука", "суки", "сучк", "мудак", "мудил", "пидор", "пидар", "педик", "гондон", "залуп", "долбоеб", "долбаеб", "ублюд", "шлюх",
        "мразь", "мрази", "тварь", "твари", "дебил", "идиот", "урод", "чмо ", "выродок", "ничтожест", "сдохни", "сдохн",
        // English
        "fuck", "shit", "bitch", "asshole", "cunt", "dickhead", "motherfucker", "bastard", "retard", "kill yourself"
    };

    /** A very simple local check: nothing is sent anywhere, the words are compared on the device only. */
    public static boolean looksOffensive(CharSequence text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        String t = " " + text.toString().toLowerCase(Locale.ROOT).replace('ё', 'е') + " ";
        for (String word : OFFENSIVE) {
            if (t.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Edit history
    // ---------------------------------------------------------------------------------------------

    private static final String SEP_ENTRY = "\u0002";
    private static final String SEP_FIELD = "\u0001";
    private static final int MAX_EDITS = 20;

    private static SharedPreferences editsPrefs;

    private static synchronized SharedPreferences edits() {
        if (editsPrefs == null) {
            editsPrefs = ApplicationLoader.applicationContext.getSharedPreferences("buttergram_edits", Context.MODE_PRIVATE);
        }
        return editsPrefs;
    }

    /** Called with the text a message had before an edit arrived. */
    public static void saveEdit(int account, long channelId, int id, int date, String oldText) {
        if (!SharedSettings.buttergramEditHistory.get() || oldText == null || oldText.isEmpty()) {
            return;
        }
        try {
            String key = messageKey(account, channelId, id);
            String current = edits().getString(key, "");
            String[] parts = current.isEmpty() ? new String[0] : current.split(SEP_ENTRY, -1);
            if (parts.length > 0) {
                String[] last = parts[parts.length - 1].split(SEP_FIELD, 2);
                if (last.length == 2 && last[1].equals(oldText)) {
                    return; // same text already stored
                }
            }
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, parts.length - (MAX_EDITS - 1));
            for (int i = start; i < parts.length; i++) {
                sb.append(parts[i]).append(SEP_ENTRY);
            }
            sb.append(date).append(SEP_FIELD).append(oldText);
            edits().edit().putString(key, sb.toString()).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Older versions of a message as {date, text} pairs, oldest first. Empty if it was never edited here. */
    public static List<Object[]> getEdits(MessageObject message) {
        List<Object[]> result = new ArrayList<>();
        if (message == null || message.messageOwner == null) {
            return result;
        }
        try {
            long channelId = message.messageOwner.peer_id != null ? message.messageOwner.peer_id.channel_id : 0;
            String stored = edits().getString(messageKey(message.currentAccount, channelId, message.getId()), "");
            if (stored.isEmpty()) {
                return result;
            }
            for (String entry : stored.split(SEP_ENTRY, -1)) {
                String[] f = entry.split(SEP_FIELD, 2);
                if (f.length == 2) {
                    result.add(new Object[]{Integer.parseInt(f[0]), f[1]});
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    /** For symmetric use in places that only have a raw message object. */
    public static long channelIdOf(TLRPC.Message message) {
        return message != null && message.peer_id != null ? message.peer_id.channel_id : 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Official ButterGram badge (local to this client, it is NOT a Telegram verification)
    // ---------------------------------------------------------------------------------------------

    public static final int OFFICIAL_NONE = 0;
    public static final int OFFICIAL_RESOURCE = 1;
    public static final int OFFICIAL_TEAM = 2;
    public static final int OFFICIAL_SUPPORTER = 3;

    private static final long[] OFFICIAL_CHANNELS = {4392732376L, 4372744306L};
    private static final long[] TEAM_USERS = {5074845818L};

    public static int officialKind(boolean user, long id) {
        long[] list = user ? TEAM_USERS : OFFICIAL_CHANNELS;
        for (long v : list) {
            if (v == Math.abs(id)) {
                return user ? OFFICIAL_TEAM : OFFICIAL_RESOURCE;
            }
        }
        if (user && ButterGramSupporters.get(id) != null) {
            return OFFICIAL_SUPPORTER;
        }
        return OFFICIAL_NONE;
    }

    /**
     * The badge to draw next to a name, or null. Supporters with a custom emoji get it as an emoji status instead
     * (see applySupporter), so no extra badge is drawn for them.
     */
    public static android.graphics.drawable.Drawable badgeFor(boolean user, long id) {
        int kind = officialKind(user, id);
        if (kind == OFFICIAL_NONE) {
            return null;
        }
        if (kind == OFFICIAL_SUPPORTER) {
            ButterGramSupporters.Entry e = ButterGramSupporters.get(id);
            if (e == null || e.emoji != 0) {
                return null;
            }
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.buttergram_supporter);
            return d != null ? d.mutate() : null;
        }
        return badge();
    }

    /** Supporters who paid for a custom badge get their emoji as a local emoji status (shown only in ButterGram). */
    public static void applySupporter(TLRPC.User user) {
        if (user == null || user.id == 0) {
            return;
        }
        ButterGramSupporters.Entry e = ButterGramSupporters.get(user.id);
        if (e == null || e.emoji == 0) {
            return;
        }
        TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
        status.document_id = e.emoji;
        user.emoji_status = status;
        user.premium = true;
    }

    /** Puts every ButterGram setting back to its default value. */
    public static void resetAll() {
        for (java.lang.reflect.Field f : SharedSettings.class.getDeclaredFields()) {
            try {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Object v = f.get(null);
                if (v instanceof org.telegram.utils.settings.BooleanSetting && ((org.telegram.utils.settings.BooleanSetting) v).getName().startsWith("buttergram_")) {
                    ((org.telegram.utils.settings.BooleanSetting) v).reset();
                } else if (v instanceof org.telegram.utils.settings.IntSetting && ((org.telegram.utils.settings.IntSetting) v).getName().startsWith("buttergram_")) {
                    ((org.telegram.utils.settings.IntSetting) v).reset();
                } else if (v instanceof org.telegram.utils.settings.StringSetting && ((org.telegram.utils.settings.StringSetting) v).getName().startsWith("buttergram_")) {
                    ((org.telegram.utils.settings.StringSetting) v).reset();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public static android.graphics.drawable.Drawable badge() {
        android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.buttergram_badge);
        return d != null ? d.mutate() : null;
    }

    /** Text shown when the badge is tapped. */
    public static String officialText(int kind, TLRPC.User user) {
        if (kind == OFFICIAL_TEAM) {
            String nick = null;
            if (user != null) {
                nick = UserObject.getPublicUsername(user);
                if (nick != null) {
                    nick = "@" + nick;
                } else {
                    nick = UserObject.getUserName(user);
                }
            }
            return LocaleController.formatString(R.string.ButterGramTeamMember, nick == null ? "" : nick);
        }
        return LocaleController.getString(R.string.ButterGramOfficialResource);
    }

    // ---------------------------------------------------------------------------------------------
    // Interface options
    // ---------------------------------------------------------------------------------------------

    public static String listTitle() {
        String t = SharedSettings.buttergramCustomTitle.get();
        return t == null || t.trim().isEmpty() ? "ButterGram" : t.trim();
    }

    /** Scales an avatar corner radius: 100 = circle (unchanged), 0 = square. */
    public static int avatarRadius(int radius) {
        int p = SharedSettings.buttergramAvatarRadius.get();
        if (p >= 100) {
            return radius;
        }
        return Math.max(0, radius * Math.max(0, p) / 100);
    }

    public static float stickerScale() {
        return Math.max(40, Math.min(200, SharedSettings.buttergramStickerScale.get())) / 100f;
    }

    public static boolean hideReactions(MessageObject message) {
        if (message == null) {
            return false;
        }
        return message.isOutOwner() ? SharedSettings.buttergramHideReactionsOut.get() : SharedSettings.buttergramHideReactionsIn.get();
    }

    public static int recentLimit(int serverLimit) {
        return SharedSettings.buttergramEndlessRecents.get() ? 5000 : serverLimit;
    }

    /** Removes the items the user chose to hide from a message context menu. */
    public static void filterMenu(List<?> items, List<Integer> options, List<Integer> icons) {
        if (items == null || options == null || items.size() != options.size()) {
            return;
        }
        for (int i = options.size() - 1; i >= 0; i--) {
            if (isMenuOptionHidden(options.get(i))) {
                items.remove(i);
                options.remove(i);
                if (icons != null && icons.size() > i) {
                    icons.remove(i);
                }
            }
        }
    }

    private static boolean isMenuOptionHidden(int option) {
        switch (option) {
            case 8: return SharedSettings.buttergramHideMenuReply.get();       // reply
            case 2: return SharedSettings.buttergramHideMenuForward.get();     // forward
            case 3: return SharedSettings.buttergramHideMenuCopy.get();        // copy
            case 22: return SharedSettings.buttergramHideMenuCopyLink.get();   // copy link
            case 6: return SharedSettings.buttergramHideMenuShare.get();       // share
            case 23: return SharedSettings.buttergramHideMenuReport.get();     // report
            case 4:
            case 10: return SharedSettings.buttergramHideMenuSave.get();       // save
            case 29: return SharedSettings.buttergramHideMenuTranslate.get();  // translate
            case 13:
            case 14: return SharedSettings.buttergramHideMenuPin.get();        // pin / unpin
            default: return false;
        }
    }
}

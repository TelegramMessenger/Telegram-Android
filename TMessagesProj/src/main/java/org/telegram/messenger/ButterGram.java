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

    public static boolean isDeleted(MessageObject message) {
        if (message == null || message.messageOwner == null || !SharedSettings.buttergramMarkDeleted.get()) {
            return false;
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
}

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/** A local log of every message that was edited or deleted, shown in "Change center". */
public class ButterGramChangeLog {
    public static final int KIND_EDITED = 0;
    public static final int KIND_DELETED = 1;
    private static final int MAX = 500;

    public static class Item {
        public int kind;
        public int account;
        public long dialogId;
        public int messageId;
        public int time;
        public String text;
        public String newText;
    }

    private static SharedPreferences prefs;

    private static synchronized SharedPreferences prefs() {
        if (prefs == null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("buttergram_log", Context.MODE_PRIVATE);
        }
        return prefs;
    }

    private static synchronized void add(Item item) {
        try {
            JSONArray old = new JSONArray(prefs().getString("log", "[]"));
            JSONArray next = new JSONArray();
            int start = Math.max(0, old.length() - (MAX - 1));
            for (int i = start; i < old.length(); i++) {
                next.put(old.get(i));
            }
            JSONObject o = new JSONObject();
            o.put("k", item.kind);
            o.put("a", item.account);
            o.put("d", item.dialogId);
            o.put("m", item.messageId);
            o.put("t", item.time);
            o.put("x", item.text == null ? "" : item.text);
            if (item.newText != null) {
                o.put("n", item.newText);
            }
            next.put(o);
            prefs().edit().putString("log", next.toString()).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Newest first. */
    public static synchronized List<Item> all() {
        List<Item> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs().getString("log", "[]"));
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject o = array.getJSONObject(i);
                Item item = new Item();
                item.kind = o.optInt("k");
                item.account = o.optInt("a");
                item.dialogId = o.optLong("d");
                item.messageId = o.optInt("m");
                item.time = o.optInt("t");
                item.text = o.optString("x");
                item.newText = o.has("n") ? o.optString("n") : null;
                result.add(item);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    public static synchronized void clear() {
        prefs().edit().remove("log").apply();
    }

    public static void logEdit(int account, long dialogId, int messageId, String oldText, String newText) {
        Item item = new Item();
        item.kind = KIND_EDITED;
        item.account = account;
        item.dialogId = dialogId;
        item.messageId = messageId;
        item.time = (int) (System.currentTimeMillis() / 1000);
        item.text = oldText;
        item.newText = newText;
        add(item);
    }

    /** Looks the deleted messages up in the local database (they are still there) and logs them. */
    public static void logDeleted(MessagesStorage storage, int account, long channelId, List<Integer> ids) {
        if (storage == null || ids == null || ids.isEmpty()) {
            return;
        }
        final List<Integer> copy = new ArrayList<>(ids);
        storage.getStorageQueue().postRunnable(() -> {
            for (int mid : copy) {
                SQLiteCursor cursor = null;
                try {
                    String query = channelId != 0
                        ? "SELECT uid, data FROM messages_v2 WHERE uid = " + (-channelId) + " AND mid = " + mid + " LIMIT 1"
                        : "SELECT uid, data FROM messages_v2 WHERE mid = " + mid + " LIMIT 1";
                    cursor = storage.getDatabase().queryFinalized(query);
                    if (cursor.next()) {
                        long uid = cursor.longValue(0);
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        String text = "";
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            if (message != null && message.message != null) {
                                text = message.message;
                            }
                        }
                        Item item = new Item();
                        item.kind = KIND_DELETED;
                        item.account = account;
                        item.dialogId = uid;
                        item.messageId = mid;
                        item.time = (int) (System.currentTimeMillis() / 1000);
                        item.text = text;
                        add(item);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
            }
        });
    }
}

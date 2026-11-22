package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * People who supported ButterGram. The list is a plain JSON file in the project repository
 * (supporters.json), downloaded in the background and cached on the device.
 * The badge is local to ButterGram: it has nothing to do with Telegram's own verification.
 */
public final class ButterGramSupporters {
    private static final String URL = "https://raw.githubusercontent.com/xenone-beta/ButterGram/master/supporters.json";
    private static final String PREFS = "buttergram_supporters";
    private static final long REFRESH_INTERVAL = 6 * 60 * 60 * 1000L;

    public static final class Entry {
        public final long id;
        /** Custom emoji document id, 0 for the standard supporter badge. */
        public final long emoji;
        /** Optional text shown in the badge menu. */
        public final String text;

        Entry(long id, long emoji, String text) {
            this.id = id;
            this.emoji = emoji;
            this.text = text == null ? "" : text;
        }
    }

    private static volatile Map<Long, Entry> map;
    private static boolean refreshing;

    private ButterGramSupporters() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Entry get(long userId) {
        Map<Long, Entry> m = map;
        if (m == null) {
            m = parse(prefs().getString("json", ""));
            map = m;
        }
        return m.get(Math.abs(userId));
    }

    private static Map<Long, Entry> parse(String json) {
        Map<Long, Entry> result = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONObject(json).optJSONArray("supporters");
            if (array == null) {
                return result;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                long id = o.optLong("id", 0);
                if (id == 0) {
                    continue;
                }
                long emoji = 0;
                try {
                    emoji = Long.parseLong(o.optString("emoji", "0").trim());
                } catch (Exception ignore) {
                }
                result.put(id, new Entry(id, emoji, o.optString("text", "")));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    /** Downloads the list in the background, at most once in a few hours. */
    public static void refreshAsync() {
        synchronized (ButterGramSupporters.class) {
            if (refreshing) {
                return;
            }
            long last = prefs().getLong("time", 0);
            if (System.currentTimeMillis() - last < REFRESH_INTERVAL) {
                return;
            }
            refreshing = true;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                if (connection.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null && sb.length() < 200_000) {
                            sb.append(line).append('\n');
                        }
                    }
                    String json = sb.toString();
                    Map<Long, Entry> parsed = parse(json);
                    prefs().edit().putString("json", json).putLong("time", System.currentTimeMillis()).apply();
                    map = parsed;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                synchronized (ButterGramSupporters.class) {
                    refreshing = false;
                }
            }
        }, "ButterGramSupporters").start();
    }
}

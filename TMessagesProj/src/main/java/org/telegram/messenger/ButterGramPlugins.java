package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButterGramPluginInstallSheet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ButterGram add-ons: a ".baddon" file is a zip with manifest.json and Python sources.
 * The Python engine itself lives in the app module (ButterGramPyEngine) and is optional:
 * when the build has no Chaquopy, {@link #engine()} is null and add-ons can only be listed or removed.
 */
public final class ButterGramPlugins implements NotificationCenter.NotificationCenterDelegate {

    public static final String EXTENSION = ".baddon";
    private static final int MAX_ENTRIES = 300;
    private static final long MAX_TOTAL_BYTES = 24L * 1024 * 1024;

    /** Implemented by the app module when Chaquopy is available. Methods may throw; callers catch. */
    public interface Engine {
        boolean start(Context context);

        /** @return null on success, otherwise an error text. */
        String load(String id, String dir, String main);

        void unload(String id);

        /** @return the text to send or null to cancel the message. */
        String outgoing(String text, long dialogId);

        void incoming(String text, long dialogId, long fromId, int messageId, boolean out);

        /** @return JSON array describing the settings page, "[]" if none. */
        String settings(String id);

        void action(String id, String key);
    }

    public static final class Info {
        public String id;
        public String name;
        public String version = "";
        public String author = "";
        public String description = "";
        public String main = "main.py";
        /** "packShortName/index" of a sticker or emoji pack, or a single emoji character. */
        public String icon = "";
        public final ArrayList<String> permissions = new ArrayList<>();
        public boolean enabled;
        public String error;
        public File dir;
    }

    public static final class InstallResult {
        public Info info;
        public String error;
    }

    private static final ButterGramPlugins INSTANCE = new ButterGramPlugins();
    private static final DispatchQueue queue = new DispatchQueue("buttergramPlugins");
    private static final Set<String> loaded = Collections.synchronizedSet(new HashSet<>());
    private static final ThreadLocal<Boolean> bypass = new ThreadLocal<>();

    private static Engine engine;
    private static boolean engineResolved;
    private static boolean started;

    private ButterGramPlugins() {
    }

    // ---------------------------------------------------------------- engine

    public static synchronized Engine engine() {
        if (!engineResolved) {
            engineResolved = true;
            try {
                Class<?> c = Class.forName("org.telegram.messenger.ButterGramPyEngine");
                Engine e = (Engine) c.getDeclaredConstructor().newInstance();
                engine = e.start(ApplicationLoader.applicationContext) ? e : null;
            } catch (Throwable t) {
                FileLog.e("ButterGram plugins: engine is not available in this build", t);
                engine = null;
            }
        }
        return engine;
    }

    public static boolean isEngineAvailable() {
        return engine() != null;
    }

    // ---------------------------------------------------------------- storage

    private static File root() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "buttergram_plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("buttergram_plugins", Context.MODE_PRIVATE);
    }

    static SharedPreferences settingsPrefs(String id) {
        return ApplicationLoader.applicationContext.getSharedPreferences("buttergram_plugin_" + id, Context.MODE_PRIVATE);
    }

    public static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9][a-z0-9_.-]{0,47}");
    }

    private static Info readManifest(File dir) {
        File manifest = new File(dir, "manifest.json");
        if (!manifest.isFile()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(new String(readAll(new FileInputStream(manifest), 256 * 1024), "UTF-8"));
            Info info = new Info();
            info.id = o.optString("id");
            if (!validId(info.id)) {
                return null;
            }
            info.name = o.optString("name", info.id);
            info.version = o.optString("version", "");
            info.author = o.optString("author", "");
            info.description = o.optString("description", "");
            info.main = o.optString("main", "main.py");
            info.icon = o.optString("icon", "");
            JSONArray perms = o.optJSONArray("permissions");
            if (perms != null) {
                for (int i = 0; i < perms.length(); i++) {
                    info.permissions.add(perms.optString(i));
                }
            }
            info.dir = dir;
            info.enabled = prefs().getBoolean("enabled_" + info.id, false);
            return info;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static ArrayList<Info> list() {
        ArrayList<Info> result = new ArrayList<>();
        File[] dirs = root().listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                if (d.isDirectory() && !d.getName().startsWith("_")) {
                    Info info = readManifest(d);
                    if (info != null) {
                        result.add(info);
                    }
                }
            }
        }
        Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    public static Info get(String id) {
        if (!validId(id)) {
            return null;
        }
        return readManifest(new File(root(), id));
    }

    private static byte[] readAll(InputStream in, int limit) throws java.io.IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > limit) {
                    throw new java.io.IOException("too big");
                }
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    // ---------------------------------------------------------------- install

    /** Reads manifest.json of a .baddon without installing it. */
    public static Info peek(File file) {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if ("manifest.json".equals(e.getName())) {
                    File tmp = new File(root(), "_peek");
                    deleteRecursive(tmp);
                    tmp.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(new File(tmp, "manifest.json"))) {
                        byte[] buf = new byte[4096];
                        int total = 0, n;
                        while ((n = zip.read(buf)) > 0) {
                            total += n;
                            if (total > 256 * 1024) {
                                return null;
                            }
                            out.write(buf, 0, n);
                        }
                    }
                    Info info = readManifest(tmp);
                    deleteRecursive(tmp);
                    return info;
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return null;
    }

    public static InstallResult install(File file) {
        InstallResult result = new InstallResult();
        File tmp = new File(root(), "_install");
        deleteRecursive(tmp);
        tmp.mkdirs();
        try {
            long total = 0;
            int count = 0;
            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
                ZipEntry e;
                String base = tmp.getCanonicalPath() + File.separator;
                while ((e = zip.getNextEntry()) != null) {
                    if (++count > MAX_ENTRIES) {
                        result.error = "too many files";
                        return result;
                    }
                    File out = new File(tmp, e.getName());
                    if (!out.getCanonicalPath().startsWith(base)) {
                        result.error = "bad path in archive";
                        return result;
                    }
                    if (e.isDirectory()) {
                        out.mkdirs();
                        continue;
                    }
                    out.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zip.read(buf)) > 0) {
                            total += n;
                            if (total > MAX_TOTAL_BYTES) {
                                result.error = "archive is too big";
                                return result;
                            }
                            fos.write(buf, 0, n);
                        }
                    }
                }
            }
            Info info = readManifest(tmp);
            if (info == null) {
                result.error = "manifest.json is missing or invalid";
                return result;
            }
            if (!new File(tmp, info.main).isFile()) {
                result.error = info.main + " is missing";
                return result;
            }
            unload(info.id);
            File dest = new File(root(), info.id);
            deleteRecursive(dest);
            if (!tmp.renameTo(dest)) {
                result.error = "cannot move files";
                return result;
            }
            result.info = readManifest(dest);
            return result;
        } catch (Throwable t) {
            FileLog.e(t);
            result.error = String.valueOf(t.getMessage());
            return result;
        } finally {
            deleteRecursive(tmp);
        }
    }

    public static void remove(String id) {
        if (!validId(id)) {
            return;
        }
        unload(id);
        deleteRecursive(new File(root(), id));
        prefs().edit().remove("enabled_" + id).apply();
        settingsPrefs(id).edit().clear().apply();
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursive(c);
            }
        }
        f.delete();
    }

    /** Channels with the plugins badge (see ButterGram.PLUGIN_CHANNELS) are shown as "Trusted source". */
    public static boolean isTrustedChannel(long channelId) {
        return ButterGram.isPluginChannel(channelId);
    }

    /** True when the file was posted in a trusted channel or forwarded from one. */
    public static boolean isTrusted(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return false;
        }
        long dialogId = message.getDialogId();
        if (dialogId < 0 && isTrustedChannel(dialogId)) {
            return true;
        }
        TLRPC.MessageFwdHeader fwd = message.messageOwner.fwd_from;
        if (fwd != null && fwd.from_id instanceof TLRPC.TL_peerChannel) {
            return isTrustedChannel(((TLRPC.TL_peerChannel) fwd.from_id).channel_id);
        }
        return false;
    }

    /** Called from the chat when the user taps a downloaded .baddon file, or with message == null for a picked file. */
    public static void showInstallDialog(BaseFragment fragment, File file, MessageObject message) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Info info = peek(file);
        if (info == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.ButterGramPlugins));
            builder.setMessage(LocaleController.getString(R.string.ButterGramPluginsBad));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            fragment.showDialog(builder.create());
            return;
        }
        ButterGramPluginInstallSheet.show(fragment, file, info, isTrusted(message));
    }

    public static void installAndReport(BaseFragment fragment, File file, boolean enable) {
        InstallResult r = install(file);
        if (r.info == null) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, String.valueOf(r.error)).show();
            return;
        }
        setEnabled(r.info.id, enable);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.ButterGramPluginsInstalled)).show();
    }

    // ---------------------------------------------------------------- run

    public static void setEnabled(String id, boolean enabled) {
        prefs().edit().putBoolean("enabled_" + id, enabled).apply();
        if (enabled) {
            ensureObservers();
            load(id);
        } else {
            unload(id);
        }
    }

    public static boolean isLoaded(String id) {
        return loaded.contains(id);
    }

    public static void load(String id) {
        final Info info = get(id);
        if (info == null || !info.enabled) {
            return;
        }
        queue.postRunnable(() -> {
            Engine e = engine();
            if (e == null || loaded.contains(id)) {
                return;
            }
            String error;
            try {
                error = e.load(id, info.dir.getAbsolutePath(), info.main);
            } catch (Throwable t) {
                FileLog.e(t);
                error = String.valueOf(t);
            }
            if (error == null) {
                loaded.add(id);
            } else {
                FileLog.e("ButterGram plugin " + id + ": " + error);
                final String message = error;
                AndroidUtilities.runOnUIThread(() -> ButterGramPluginApi.dialogNow(info.name, message));
            }
        });
    }

    public static void unload(String id) {
        if (!loaded.remove(id)) {
            return;
        }
        queue.postRunnable(() -> {
            try {
                Engine e = engine();
                if (e != null) {
                    e.unload(id);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /** Starts enabled add-ons. Cheap when there are none: the Python runtime is not touched. */
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        queue.postRunnable(() -> {
            boolean any = false;
            for (Info info : list()) {
                if (info.enabled) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    NotificationCenter.getInstance(a).addObserver(INSTANCE, NotificationCenter.didReceiveNewMessages);
                }
            });
            for (Info info : list()) {
                if (info.enabled) {
                    load(info.id);
                }
            }
        });
    }

    /** Makes sure the incoming-message observer exists even when the first add-on was installed later. */
    static void ensureObservers() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).removeObserver(INSTANCE, NotificationCenter.didReceiveNewMessages);
                NotificationCenter.getInstance(a).addObserver(INSTANCE, NotificationCenter.didReceiveNewMessages);
            }
        });
    }

    /** Message hook used by SendMessagesHelper. Returns the text to send, or null to cancel. */
    public static String outgoing(String text, long dialogId) {
        if (text == null || loaded.isEmpty() || Boolean.TRUE.equals(bypass.get())) {
            return text;
        }
        try {
            Engine e = engine();
            if (e == null) {
                return text;
            }
            return e.outgoing(text, dialogId);
        } catch (Throwable t) {
            FileLog.e(t);
            return text;
        }
    }

    static void sendBypassing(Runnable r) {
        bypass.set(Boolean.TRUE);
        try {
            r.run();
        } finally {
            bypass.set(Boolean.FALSE);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didReceiveNewMessages || loaded.isEmpty()) {
            return;
        }
        final long dialogId = (Long) args[0];
        final ArrayList<MessageObject> messages = new ArrayList<>((ArrayList<MessageObject>) args[1]);
        queue.postRunnable(() -> {
            Engine e = engine();
            if (e == null) {
                return;
            }
            for (MessageObject m : messages) {
                if (m == null || m.messageOwner == null || TextUtils.isEmpty(m.messageText) && TextUtils.isEmpty(m.messageOwner.message)) {
                    continue;
                }
                try {
                    String text = m.messageOwner.message != null ? m.messageOwner.message : String.valueOf(m.messageText);
                    e.incoming(text, dialogId, m.getFromChatId(), m.getId(), m.isOut());
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        });
    }

    public static String settingsJson(String id) {
        try {
            Engine e = engine();
            if (e != null && loaded.contains(id)) {
                return e.settings(id);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return "[]";
    }

    public static void action(String id, String key) {
        queue.postRunnable(() -> {
            try {
                Engine e = engine();
                if (e != null && loaded.contains(id)) {
                    e.action(id, key);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }
}

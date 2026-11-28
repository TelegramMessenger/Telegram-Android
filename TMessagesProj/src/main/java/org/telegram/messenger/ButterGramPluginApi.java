package org.telegram.messenger;

import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONTokener;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

/**
 * The only Java surface that add-ons are meant to use (the Python SDK wraps it). Kept from obfuscation.
 * All values travel as strings or numbers so that the Python side stays simple.
 */
@SuppressWarnings("unused")
public final class ButterGramPluginApi {

    private ButterGramPluginApi() {
    }

    public static int apiVersion() {
        return 1;
    }

    public static String appVersion() {
        return BuildVars.BUILD_VERSION_STRING + " " + ButterGram.MOD_VERSION;
    }

    public static void log(String pluginId, String message) {
        FileLog.d("ButterGram plugin " + pluginId + ": " + message);
    }

    public static void toast(String text) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show());
    }

    public static void dialog(String title, String message) {
        AndroidUtilities.runOnUIThread(() -> dialogNow(title, message));
    }

    static void dialogNow(String title, String message) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) {
            toast(message);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(builder.create());
    }

    public static long currentUserId() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    /** Sends a text without running it through add-on hooks again. */
    public static void sendMessage(long dialogId, String text) {
        AndroidUtilities.runOnUIThread(() -> ButterGramPlugins.sendBypassing(() ->
            SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId))));
    }

    /** @return the stored JSON text for the key, or null. */
    public static String getSetting(String pluginId, String key) {
        SharedPreferences p = ButterGramPlugins.settingsPrefs(pluginId);
        return p.getString(key, null);
    }

    /** @param jsonValue a JSON value, for example true, 12, "text". */
    public static void setSetting(String pluginId, String key, String jsonValue) {
        ButterGramPlugins.settingsPrefs(pluginId).edit().putString(key, jsonValue).apply();
    }

    /** Helper for the settings page: decodes a stored JSON value to a plain Java object. */
    public static Object decode(String json) {
        if (json == null) {
            return null;
        }
        try {
            return new JSONTokener(json).nextValue();
        } catch (Throwable t) {
            return null;
        }
    }
}

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * ButterGram: biometric / device-credential lock for selected chats.
 * A chat stays unlocked while it is open and for a short grace period after leaving it.
 */
public final class ButterGramChatLock {
    private static final String PREFS = "buttergram_chat_lock";
    private static final String KEY = "locked";
    private static final long GRACE_MS = 30_000L;

    private static final HashMap<Long, Long> unlockedUntil = new HashMap<>();

    private ButterGramChatLock() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isLocked(long dialogId) {
        return prefs().getStringSet(KEY, new HashSet<>()).contains(String.valueOf(dialogId));
    }

    public static int count() {
        return prefs().getStringSet(KEY, new HashSet<>()).size();
    }

    public static void setLocked(long dialogId, boolean locked) {
        final Set<String> set = new HashSet<>(prefs().getStringSet(KEY, new HashSet<>()));
        if (locked) {
            set.add(String.valueOf(dialogId));
        } else {
            set.remove(String.valueOf(dialogId));
        }
        prefs().edit().putStringSet(KEY, set).apply();
        unlockedUntil.remove(dialogId);
    }

    public static boolean needsAuth(long dialogId) {
        if (!isLocked(dialogId)) {
            return false;
        }
        final Long until = unlockedUntil.get(dialogId);
        return until == null || until < System.currentTimeMillis();
    }

    /** The chat is on screen and unlocked. */
    public static void markOpen(long dialogId) {
        if (isLocked(dialogId)) {
            unlockedUntil.put(dialogId, Long.MAX_VALUE);
        }
    }

    /** The chat left the screen: keep it open for a moment, then ask again. */
    public static void markClosed(long dialogId) {
        if (unlockedUntil.containsKey(dialogId)) {
            unlockedUntil.put(dialogId, System.currentTimeMillis() + GRACE_MS);
        }
    }

    public static boolean canAuthenticate(Context context) {
        return BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void authenticate(Activity activity, String title, Runnable onSuccess, Runnable onFail) {
        if (!(activity instanceof FragmentActivity) || !canAuthenticate(activity)) {
            // no secure lock screen on the phone: do not lock the user out of their own chat
            AndroidUtilities.runOnUIThread(onSuccess);
            return;
        }
        final BiometricPrompt prompt = new BiometricPrompt((FragmentActivity) activity,
                ContextCompat.getMainExecutor(activity), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                onSuccess.run();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                onFail.run();
            }
        });
        final BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(info);
    }
}

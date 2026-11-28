package org.telegram.messenger;

import android.widget.Toast;

import org.telegram.utils.settings.SharedSettings;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ButterGram: keeps a copy of self-destructing media (timed and view-once photos, videos,
 * voice and round messages) the moment the file has been downloaded for viewing.
 */
public final class ButterGramSecretSaver {
    private static final Set<String> seen = ConcurrentHashMap.newKeySet();
    private static final int MAX_ATTEMPTS = 240; // 2 minutes

    private ButterGramSecretSaver() {
    }

    public static void watch(int account, MessageObject message) {
        if (message == null || message.messageOwner == null || !SharedSettings.buttergramSaveSecretMedia.get()) {
            return;
        }
        final String key = account + "_" + message.getDialogId() + "_" + message.getId();
        if (!seen.add(key)) {
            return;
        }
        poll(account, message, key, 0);
    }

    private static void poll(int account, MessageObject message, String key, int attempt) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final File file = findFile(account, message);
                if (file != null) {
                    save(file, message);
                    return;
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            if (attempt >= MAX_ATTEMPTS) {
                seen.remove(key);
                return;
            }
            poll(account, message, key, attempt + 1);
        }, 500);
    }

    private static File findFile(int account, MessageObject message) {
        File file = null;
        final String attach = message.messageOwner.attachPath;
        if (attach != null && attach.length() > 0) {
            final File f = new File(attach);
            if (f.exists() && f.length() > 0) {
                file = f;
            }
        }
        if (file == null) {
            final File f = FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
            if (f != null && f.exists() && f.length() > 0) {
                file = f;
            }
        }
        return file;
    }

    private static void save(File file, MessageObject message) {
        final int type;
        final String mime;
        final String ext;
        if (message.isVoice()) {
            type = 2;
            mime = "audio/ogg";
            ext = ".ogg";
        } else if (message.isVideo() || message.isRoundVideo() || message.getDocument() != null) {
            type = 1;
            mime = "video/mp4";
            ext = ".mp4";
        } else {
            type = 0;
            mime = "image/jpeg";
            ext = ".jpg";
        }
        final String name = "ButterGram_secret_" + Math.abs(message.getDialogId()) + "_" + message.getId() + ext;
        AndroidUtilities.runOnUIThread(() -> {
            MediaController.saveFile(file.getAbsolutePath(), ApplicationLoader.applicationContext, type, name, mime, uri -> {
                try {
                    Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString(R.string.ButterGramSecretSaved), Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
            }, false);
        });
    }
}

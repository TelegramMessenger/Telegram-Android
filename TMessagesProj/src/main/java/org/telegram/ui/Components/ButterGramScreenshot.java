package org.telegram.ui.Components;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ButterGramScreenshotActivity;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.utils.settings.SharedSettings;

import java.util.ArrayList;
import java.util.Collections;

/** Detects a screenshot taken inside a private chat and offers to open the screenshot editor (Android 14+). */
public class ButterGramScreenshot {
    private Object callback;
    private Activity activity;

    public void start(ChatActivity fragment) {
        stop();
        if (Build.VERSION.SDK_INT < 34 || !SharedSettings.buttergramScreenshotEditor.get()) {
            return;
        }
        if (fragment == null || fragment.getCurrentUser() == null || fragment.isSecretChat() || fragment.getParentActivity() == null) {
            return;
        }
        try {
            activity = fragment.getParentActivity();
            Activity.ScreenCaptureCallback c = () -> AndroidUtilities.runOnUIThread(() -> onCaptured(fragment));
            activity.registerScreenCaptureCallback(activity.getMainExecutor(), c);
            callback = c;
        } catch (Throwable ignore) {
            callback = null;
        }
    }

    public void stop() {
        if (callback != null && activity != null && Build.VERSION.SDK_INT >= 34) {
            try {
                activity.unregisterScreenCaptureCallback((Activity.ScreenCaptureCallback) callback);
            } catch (Throwable ignore) {
            }
        }
        callback = null;
        activity = null;
    }

    private void onCaptured(ChatActivity fragment) {
        if (fragment.getParentActivity() == null || fragment.getCurrentUser() == null) {
            return;
        }
        RecyclerListView list = fragment.getChatListView();
        if (list == null) {
            return;
        }
        final ArrayList<View> cells = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getMessageObject() != null && child.getBottom() > 0 && child.getTop() < list.getHeight()) {
                cells.add(child);
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        Collections.sort(cells, (a, b) -> Integer.compare(a.getTop(), b.getTop()));
        final ArrayList<MessageObject> messages = new ArrayList<>();
        for (View cell : cells) {
            messages.add(((ChatMessageCell) cell).getMessageObject());
            if (messages.size() >= 15) {
                break;
            }
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip,
            LocaleController.getString(R.string.ButterGramShotTaken),
            LocaleController.getString(R.string.ButterGramShotCustomize),
            () -> fragment.presentFragment(new ButterGramScreenshotActivity(messages))).show();
    }
}

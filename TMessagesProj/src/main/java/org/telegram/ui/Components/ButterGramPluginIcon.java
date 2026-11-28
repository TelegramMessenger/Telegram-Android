package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

/**
 * Add-on icon. manifest.json "icon" is "packShortName/index" (a sticker or emoji pack, like "emoji/0"),
 * a single emoji character, or empty (first letter of the name on a gradient).
 */
public class ButterGramPluginIcon extends FrameLayout {
    private final BackupImageView image;
    private final TextView text;
    private String loadedKey;

    public ButterGramPluginIcon(Context context) {
        super(context);
        image = new BackupImageView(context);
        text = new TextView(context);
        text.setGravity(Gravity.CENTER);
        text.setTextColor(Color.WHITE);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(image, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 6, 6, 6, 6));
    }

    private void showLetter(String name) {
        image.setVisibility(GONE);
        text.setVisibility(VISIBLE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        text.setText(TextUtils.isEmpty(name) ? "?" : name.substring(0, 1).toUpperCase());
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF5EEAD4, 0xFF38BDF8});
        bg.setCornerRadius(dp(14));
        setBackground(bg);
    }

    public void bind(String icon, String name) {
        final String key = icon + "|" + name;
        if (key.equals(loadedKey)) {
            return;
        }
        loadedKey = key;
        setBackground(null);
        if (TextUtils.isEmpty(icon)) {
            showLetter(name);
            return;
        }
        int slash = icon.lastIndexOf('/');
        if (slash <= 0 || slash == icon.length() - 1) {
            // a plain emoji
            image.setVisibility(GONE);
            text.setVisibility(VISIBLE);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
            text.setText(icon);
            return;
        }
        final String pack = icon.substring(0, slash);
        int parsed = 0;
        try {
            parsed = Integer.parseInt(icon.substring(slash + 1).trim());
        } catch (NumberFormatException ignore) {
        }
        final int index = parsed;
        showLetter(name); // placeholder until the pack arrives
        final int account = UserConfig.selectedAccount;
        TLRPC.TL_inputStickerSetShortName input = new TLRPC.TL_inputStickerSetShortName();
        input.short_name = pack;
        try {
            MediaDataController.getInstance(account).getStickerSet(input, null, false, set -> {
                if (set == null || set.documents == null || index < 0 || index >= set.documents.size() || !key.equals(loadedKey)) {
                    return;
                }
                TLRPC.Document document = set.documents.get(index);
                setBackground(null);
                text.setVisibility(GONE);
                image.setVisibility(VISIBLE);
                image.setImage(ImageLocation.getForDocument(document), "64_64", (android.graphics.drawable.Drawable) null, set);
            });
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}

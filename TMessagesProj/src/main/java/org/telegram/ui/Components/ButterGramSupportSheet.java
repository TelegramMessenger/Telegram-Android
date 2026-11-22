package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import org.telegram.messenger.UserConfig;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ButterGramSupporters;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/** The menu shown when a ButterGram supporter's badge is tapped, and the "how to support" page. */
public final class ButterGramSupportSheet {
    public static final String ADDRESS = "UQAGm5csfTiGB7_xPOCjB0_3xraNJq0z0mceniuGBN8CsrDE";
    private static final String OWNER = "ButterGramOwner";
    private static final int BUTTER = 0xFFCFA33A;

    private ButterGramSupportSheet() {
    }

    /** Shows the menu if the user is a supporter. Returns false (and does nothing) otherwise. */
    public static boolean showIfSupporter(BaseFragment fragment, TLRPC.User user) {
        if (fragment == null || user == null) {
            return false;
        }
        ButterGramSupporters.Entry entry = ButterGramSupporters.get(user.id);
        if (entry == null) {
            return false;
        }
        show(fragment, user, entry);
        return true;
    }

    public static void openDonation(Context context) {
        String text = "ButterGram";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("ton://transfer/" + ADDRESS + "?text=" + text));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable e) {
            Browser.openUrl(context, "https://app.tonkeeper.com/transfer/" + ADDRESS + "?text=" + text);
        }
    }

    private static TextView text(Context context, CharSequence s, float size, int color, boolean bold, int gravity) {
        TextView tv = new TextView(context);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        tv.setTextColor(color);
        tv.setGravity(gravity);
        if (bold) {
            tv.setTypeface(AndroidUtilities.bold());
        }
        return tv;
    }

    private static View feature(Context context, int icon, int title, int description) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        ImageView image = new ImageView(context);
        image.setImageResource(icon);
        image.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN));
        row.addView(image, LayoutHelper.createLinear(28, 28, Gravity.TOP, 0, 2, 16, 0));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(text(context, LocaleController.getString(title), 16, Theme.getColor(Theme.key_dialogTextBlack), true, Gravity.LEFT));
        column.addView(text(context, LocaleController.getString(description), 14, Theme.getColor(Theme.key_dialogTextGray2), false, Gravity.LEFT),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(column, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        return row;
    }

    /** user and entry may be null: then it is just the "how to support" page. */
    public static void show(BaseFragment fragment, TLRPC.User user, ButterGramSupporters.Entry entry) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final int black = Theme.getColor(Theme.key_dialogTextBlack);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(16));

        content.addView(new BadgeView(context, entry != null ? entry.emoji : 0), LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

        CharSequence title;
        if (user != null) {
            String name = UserObject.getUserName(user);
            String full = LocaleController.formatString(R.string.ButterGramSheetTitle, name);
            SpannableStringBuilder sb = new SpannableStringBuilder(full);
            int start = full.indexOf(name);
            if (start >= 0) {
                sb.setSpan(new ForegroundColorSpan(BUTTER), start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            title = sb;
        } else {
            title = LocaleController.getString(R.string.ButterGramSheetTitleGeneric);
        }
        content.addView(text(context, title, 22, black, true, Gravity.CENTER),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        if (entry != null && !entry.text.isEmpty()) {
            content.addView(text(context, "“" + entry.text + "”", 15, Theme.getColor(Theme.key_dialogTextGray2), false, Gravity.CENTER),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
        }
        content.addView(text(context, LocaleController.getString(R.string.ButterGramSheetGet), 15, black, false, Gravity.CENTER),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 14));

        content.addView(feature(context, R.drawable.msg_premium_badge, R.string.ButterGramFeature1Title, R.string.ButterGramFeature1Text),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
        content.addView(feature(context, R.drawable.msg_emoji_smiles, R.string.ButterGramFeature2Title, R.string.ButterGramFeature2Text),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
        content.addView(feature(context, R.drawable.msg_payment_card, R.string.ButterGramFeature3Title, R.string.ButterGramFeature3Text),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        final BottomSheet[] sheet = new BottomSheet[1];

        TextView how = text(context, LocaleController.getString(R.string.ButterGramSheetHow), 16, black, true, Gravity.CENTER);
        how.setPadding(0, dp(12), 0, dp(12));
        how.setOnClickListener(v -> showHowDialog(fragment));
        content.addView(how, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView donate = text(context, LocaleController.getString(R.string.ButterGramSheetDonate), 16, Color.WHITE, true, Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BUTTER);
        bg.setCornerRadius(dp(24));
        donate.setBackground(bg);
        donate.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            openDonation(context);
        });
        content.addView(donate, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 4, 0, 0));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setCustomView(scroll);
        sheet[0] = builder.create();
        fragment.showDialog(sheet[0]);
    }

    private static void showHowDialog(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ButterGramSheetHow));
        builder.setMessage(LocaleController.formatString(R.string.ButterGramHowText, OWNER, ADDRESS));
        builder.setPositiveButton(LocaleController.getString(R.string.ButterGramCopyAddress), (d, w) -> {
            AndroidUtilities.addToClipboard(ADDRESS);
            BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        fragment.showDialog(builder.create());
    }

    /** Big supporter badge, or the supporter's custom emoji when one is set. */
    private static class BadgeView extends View {
        private final Drawable drawable;
        private final AnimatedEmojiDrawable emojiDrawable;

        BadgeView(Context context, long documentId) {
            super(context);
            if (documentId != 0) {
                emojiDrawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140, documentId);
                if (emojiDrawable.getImageReceiver() != null) {
                    emojiDrawable.getImageReceiver().setRoundRadius(0);
                }
                drawable = emojiDrawable;
            } else {
                emojiDrawable = null;
                drawable = context.getResources().getDrawable(R.drawable.buttergram_supporter).mutate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (emojiDrawable != null) {
                emojiDrawable.addView(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (emojiDrawable != null) {
                emojiDrawable.removeView(this);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawable.setBounds(0, 0, getWidth(), getHeight());
            drawable.draw(canvas);
        }
    }
}

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.ButterGramPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;

/** Install sheet for a .baddon: icon, name, version, author, source badge, description, install button. */
public final class ButterGramPluginInstallSheet {

    private ButterGramPluginInstallSheet() {
    }

    private static TextView text(Context context, CharSequence value, int sp, int color, boolean bold, int gravity) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        t.setTextColor(color);
        t.setGravity(gravity);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    public static void show(BaseFragment fragment, File file, ButterGramPlugins.Info info, boolean trusted) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        final int blue = Theme.getColor(Theme.key_featuredStickers_addButton);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(16), dp(24), dp(16), dp(16));

        // icon: rounded square with the first letter
        String letter = TextUtils.isEmpty(info.name) ? "?" : info.name.substring(0, 1).toUpperCase();
        TextView icon = text(context, letter, 34, Color.WHITE, true, Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF5EEAD4, 0xFF38BDF8});
        iconBg.setCornerRadius(dp(22));
        icon.setBackground(iconBg);
        content.addView(icon, LayoutHelper.createLinear(84, 84, Gravity.CENTER_HORIZONTAL));

        content.addView(text(context, info.name, 22, black, true, Gravity.CENTER),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        String sub = LocaleController.formatString(R.string.ButterGramPluginsVersion, TextUtils.isEmpty(info.version) ? "-" : info.version);
        if (!TextUtils.isEmpty(info.author)) {
            sub += " • " + info.author;
        }
        content.addView(text(context, sub, 15, gray, false, Gravity.CENTER),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        // source badge
        final int accent = trusted ? 0xFF3FB950 : 0xFFE5534B;
        TextView badge = text(context, (trusted ? "✓  " : "⚠  ") + LocaleController.getString(trusted ? R.string.ButterGramPluginsTrustedSource : R.string.ButterGramPluginsUnknownSource),
            15, accent, true, Gravity.CENTER);
        badge.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor((accent & 0x00FFFFFF) | 0x26000000);
        badgeBg.setCornerRadius(dp(20));
        badge.setBackground(badgeBg);
        content.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        // description card
        String description = info.description;
        if (!trusted) {
            description = (TextUtils.isEmpty(description) ? "" : description + "\n\n") + LocaleController.getString(R.string.ButterGramPluginsWarning);
        }
        if (!TextUtils.isEmpty(description)) {
            TextView card = text(context, description, 16, black, false, Gravity.START);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            cardBg.setCornerRadius(dp(16));
            card.setBackground(cardBg);
            content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        }
        if (!ButterGramPlugins.isEngineAvailable()) {
            content.addView(text(context, LocaleController.getString(R.string.ButterGramPluginsNoEngine), 13, 0xFFE5534B, false, Gravity.CENTER),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        }

        final BottomSheet[] sheet = new BottomSheet[1];

        TextView install = text(context, LocaleController.getString(R.string.ButterGramPluginsInstallButton), 16, Color.WHITE, true, Gravity.CENTER);
        GradientDrawable installBg = new GradientDrawable();
        installBg.setColor(blue);
        installBg.setCornerRadius(dp(24));
        install.setBackground(installBg);
        content.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 20, 0, 0));

        // "enable after install" check
        final boolean[] enableAfter = {false};
        LinearLayout checkRow = new LinearLayout(context);
        checkRow.setOrientation(LinearLayout.HORIZONTAL);
        checkRow.setGravity(Gravity.CENTER);
        checkRow.setPadding(0, dp(12), 0, dp(4));
        final TextView circle = text(context, "", 14, Color.WHITE, true, Gravity.CENTER);
        final GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setStroke(dp(2), gray);
        circle.setBackground(circleBg);
        checkRow.addView(circle, LayoutHelper.createLinear(24, 24));
        checkRow.addView(text(context, LocaleController.getString(R.string.ButterGramPluginsEnableAfter), 16, black, false, Gravity.CENTER),
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 10, 0, 0, 0));
        checkRow.setOnClickListener(v -> {
            enableAfter[0] = !enableAfter[0];
            circle.setText(enableAfter[0] ? "✓" : "");
            circleBg.setColor(enableAfter[0] ? blue : Color.TRANSPARENT);
            circleBg.setStroke(dp(2), enableAfter[0] ? blue : gray);
        });
        content.addView(checkRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        install.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            ButterGramPlugins.installAndReport(fragment, file, enableAfter[0]);
        });

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setCustomView(scroll);
        sheet[0] = builder.create();
        fragment.showDialog(sheet[0]);
    }
}

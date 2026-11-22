package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButterGramPreviewView;
import org.telegram.ui.Components.ButterGramSupportSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.settings.BooleanSetting;
import org.telegram.utils.settings.IntSetting;
import org.telegram.utils.settings.SharedSettings;

import java.util.ArrayList;

/**
 * ButterGram settings: a hub with categories (like exteraGram) and one sub screen per category.
 * One class serves all pages, the page is chosen in the constructor.
 */
public class ButterGramSettingsActivity extends BaseFragment {
    public static final int PAGE_MAIN = 0;
    public static final int PAGE_GENERAL = 1;
    public static final int PAGE_APPEARANCE = 2;
    public static final int PAGE_CHATS = 3;
    public static final int PAGE_OTHER = 4;
    public static final int PAGE_MENU = 5;

    private static final String USERNAME_CHANNEL = "ButterGramClient";
    private static final String USERNAME_CHAT = "ButterGramChat";
    private static final String URL_LICENSE = "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html";

    private static final int TYPE_LOGO = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_CHECK = 2;
    private static final int TYPE_ICON = 3;
    private static final int TYPE_VALUE = 4;
    private static final int TYPE_INFO = 5;
    private static final int TYPE_SHADOW = 6;
    private static final int TYPE_PREVIEW = 7;
    private static final int TYPE_SLIDER = 8;
    private static final int PREVIEW_BASE = 20;

    private static final int ACTION_NONE = 0;
    private static final int ACTION_OPEN_PAGE = 1;
    private static final int ACTION_TOGGLE = 2;
    private static final int ACTION_OPEN_URL = 3;
    private static final int ACTION_OPEN_THEMES = 4;
    private static final int ACTION_OPEN_USERNAME = 5;
    private static final int ACTION_PICK_COLOR = 6;
    private static final int ACTION_CHOICE = 7;
    private static final int ACTION_EDIT_TITLE = 8;
    private static final int ACTION_TONKEEPER = 9;
    private static final int ACTION_HOW_TO = 10;
    private static final int ACTION_RESET = 11;

    private static final int[] DELETED_COLORS = {
        0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFF9E9E9E, 0xFFFFFFFF
    };
    private static final int[] DELETED_COLOR_NAMES = {
        R.string.ButterGramColorRed, R.string.ButterGramColorOrange, R.string.ButterGramColorYellow, R.string.ButterGramColorGreen,
        R.string.ButterGramColorBlue, R.string.ButterGramColorPurple, R.string.ButterGramColorGray, R.string.ButterGramColorWhite
    };

    private static final class Row {
        final int type;
        final int title;       // string resource, 0 if none
        final int icon;        // drawable resource for TYPE_ICON, 0 if none
        final String value;    // text for TYPE_VALUE
        final int action;
        final int page;
        final String url;
        final BooleanSetting setting;
        IntSetting intSetting;
        int[] choiceValues;
        String[] choiceLabels;
        int previewKind;

        Row(int type, int title, int icon, String value, int action, int page, String url, BooleanSetting setting) {
            this.type = type;
            this.title = title;
            this.icon = icon;
            this.value = value;
            this.action = action;
            this.page = page;
            this.url = url;
            this.setting = setting;
        }

        static Row logo() {
            return new Row(TYPE_LOGO, 0, 0, null, ACTION_NONE, 0, null, null);
        }

        static Row header(int title) {
            return new Row(TYPE_HEADER, title, 0, null, ACTION_NONE, 0, null, null);
        }

        static Row info(int title) {
            return new Row(TYPE_INFO, title, 0, null, ACTION_NONE, 0, null, null);
        }

        static Row shadow() {
            return new Row(TYPE_SHADOW, 0, 0, null, ACTION_NONE, 0, null, null);
        }

        static Row check(int title, BooleanSetting setting) {
            return new Row(TYPE_CHECK, title, 0, null, ACTION_TOGGLE, 0, null, setting);
        }

        static Row category(int title, int icon, int page) {
            return new Row(TYPE_ICON, title, icon, null, ACTION_OPEN_PAGE, page, null, null);
        }

        static Row themes(int title, int icon) {
            return new Row(TYPE_ICON, title, icon, null, ACTION_OPEN_THEMES, 0, null, null);
        }

        static Row link(int title, String value, String url) {
            return new Row(TYPE_VALUE, title, 0, value, ACTION_OPEN_URL, 0, url, null);
        }

        static Row username(int title, String username) {
            return new Row(TYPE_VALUE, title, 0, "@" + username, ACTION_OPEN_USERNAME, 0, username, null);
        }

        static Row colorPicker(int title, String value) {
            return new Row(TYPE_VALUE, title, 0, value, ACTION_PICK_COLOR, 0, null, null);
        }

        static Row choice(int title, IntSetting setting, int[] values, String[] labels) {
            Row r = new Row(TYPE_VALUE, title, 0, null, ACTION_CHOICE, 0, null, null);
            r.intSetting = setting;
            r.choiceValues = values;
            r.choiceLabels = labels;
            return r;
        }

        static Row preview(int kind) {
            Row r = new Row(TYPE_PREVIEW, 0, 0, null, ACTION_NONE, 0, null, null);
            r.previewKind = kind;
            return r;
        }

        static Row slider(int title, IntSetting setting, int[] values, String[] labels) {
            Row r = new Row(TYPE_SLIDER, title, 0, null, ACTION_NONE, 0, null, null);
            r.intSetting = setting;
            r.choiceValues = values;
            r.choiceLabels = labels;
            return r;
        }

        static Row action(int title, int icon, int action) {
            return new Row(TYPE_ICON, title, icon, null, action, 0, null, null);
        }

        static Row editTitle(int title, String value) {
            return new Row(TYPE_VALUE, title, 0, value, ACTION_EDIT_TITLE, 0, null, null);
        }

        static Row plainValue(int title, String value) {
            return new Row(TYPE_VALUE, title, 0, value, ACTION_NONE, 0, null, null);
        }
    }

    private final int page;
    private final ArrayList<Row> rows = new ArrayList<>();
    private RecyclerListView listView;
    private ListAdapter adapter;

    public ButterGramSettingsActivity() {
        this(PAGE_MAIN);
    }

    public ButterGramSettingsActivity(int page) {
        super();
        this.page = page;
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    private int titleForPage() {
        switch (page) {
            case PAGE_GENERAL:
                return R.string.ButterGramGeneral;
            case PAGE_APPEARANCE:
                return R.string.ButterGramAppearanceCategory;
            case PAGE_CHATS:
                return R.string.ButterGramChats;
            case PAGE_OTHER:
                return R.string.ButterGramOther;
            case PAGE_MENU:
                return R.string.ButterGramMenuTitle;
            default:
                return R.string.ButterGramSettings;
        }
    }

    private String currentDeletedColorName() {
        int current = SharedSettings.buttergramDeletedColor.get();
        for (int i = 0; i < DELETED_COLORS.length; i++) {
            if (DELETED_COLORS[i] == current) {
                return LocaleController.getString(DELETED_COLOR_NAMES[i]);
            }
        }
        return String.format("#%06X", current & 0xFFFFFF);
    }

    private static final int[] PERCENT_AVATAR = {0, 25, 50, 75, 100};
    private static final int[] PERCENT_STICKER = {70, 85, 100, 120, 140};

    private String[] percentLabels(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i] + "%";
        }
        return out;
    }

    private String[] avatarLabels() {
        return new String[]{
            LocaleController.getString(R.string.ButterGramShapeSquare), "25%", "50%", "75%", LocaleController.getString(R.string.ButterGramShapeCircle)
        };
    }

    private String[] swipeLabels() {
        return new String[]{
            LocaleController.getString(R.string.ButterGramSwipeReply), LocaleController.getString(R.string.ButterGramSwipeForward),
            LocaleController.getString(R.string.ButterGramSwipeCopy), LocaleController.getString(R.string.ButterGramSwipeNothing)
        };
    }

    /** Redraws every visible live preview after a setting changed. */
    private void invalidatePreviews() {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof ButterGramPreviewView) {
                child.invalidate();
            }
        }
    }

    /** A titled slider with discrete steps; moving it updates the setting and the previews right away. */
    private class SliderCell extends LinearLayout {
        private final TextView titleView;
        private final TextView valueView;
        private final SeekBarView seekBar;
        private Row row;
        private int index;

        SliderCell(Context context) {
            super(context);
            setOrientation(VERTICAL);

            FrameLayout top = new FrameLayout(context);
            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            top.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            top.addView(valueView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
            addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 8, 0, 0));

            seekBar = new SeekBarView(context);
            seekBar.setReportChanges(true);
            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    if (row == null) {
                        return;
                    }
                    int count = row.choiceValues.length;
                    int idx = Math.max(0, Math.min(count - 1, Math.round(progress * (count - 1))));
                    if (idx != index) {
                        index = idx;
                        row.intSetting.set(row.choiceValues[idx]);
                        valueView.setText(row.choiceLabels[idx]);
                        invalidatePreviews();
                    }
                    if (stop) {
                        seekBar.setProgress(idx / (float) (count - 1));
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }

                @Override
                public int getStepsCount() {
                    return row == null ? 0 : row.choiceValues.length;
                }
            });
            addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 6, 0, 6, 8));
        }

        void bind(Row row) {
            this.row = null;
            int cur = row.intSetting.get();
            int best = 0;
            for (int i = 0; i < row.choiceValues.length; i++) {
                if (row.choiceValues[i] == cur) {
                    best = i;
                }
            }
            index = best;
            titleView.setText(LocaleController.getString(row.title));
            valueView.setText(row.choiceLabels[best]);
            seekBar.setProgress(best / (float) Math.max(1, row.choiceValues.length - 1));
            this.row = row;
        }
    }

    private void showChoiceDialog(Row row) {
        if (getParentActivity() == null || row.intSetting == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(row.title));
        CharSequence[] labels = new CharSequence[row.choiceLabels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = row.choiceLabels[i];
        }
        builder.setItems(labels, (dialog, which) -> {
            row.intSetting.set(row.choiceValues[which]);
            buildRows();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showResetDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ButterGramResetSettings));
        builder.setMessage(LocaleController.getString(R.string.ButterGramResetConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.ButterGramResetSettings), (dialog, which) -> {
            org.telegram.messenger.ButterGram.resetAll();
            buildRows();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.ButterGramResetDone)).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showTitleDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ButterGramTitleText));
        final EditText input = new EditText(getParentActivity());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("ButterGram");
        input.setText(SharedSettings.buttergramCustomTitle.get());
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            SharedSettings.buttergramCustomTitle.set(input.getText().toString().trim());
            buildRows();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private String currentChoiceLabel(Row r) {
        int cur = r.intSetting.get();
        for (int i = 0; i < r.choiceValues.length; i++) {
            if (r.choiceValues[i] == cur) {
                return r.choiceLabels[i];
            }
        }
        return String.valueOf(cur);
    }

    private void buildRows() {
        rows.clear();
        switch (page) {
            case PAGE_GENERAL:
                rows.add(Row.header(R.string.ButterGramGhostMode));
                rows.add(Row.check(R.string.ButterGramGhostRead, SharedSettings.buttergramGhostRead));
                rows.add(Row.check(R.string.ButterGramGhostTyping, SharedSettings.buttergramGhostTyping));
                rows.add(Row.check(R.string.ButterGramGhostOnline, SharedSettings.buttergramGhostOnline));
                rows.add(Row.info(R.string.ButterGramGhostInfo));
                rows.add(Row.header(R.string.ButterGramConfirmHeader));
                rows.add(Row.check(R.string.ButterGramSettingOffensive, SharedSettings.buttergramWarnOffensive));
                rows.add(Row.check(R.string.ButterGramSettingVoice, SharedSettings.buttergramConfirmVoice));
                rows.add(Row.check(R.string.ButterGramSettingRound, SharedSettings.buttergramConfirmRound));
                rows.add(Row.check(R.string.ButterGramSettingSticker, SharedSettings.buttergramConfirmSticker));
                rows.add(Row.info(R.string.ButterGramConfirmInfo));
                rows.add(Row.header(R.string.ButterGramPrivacyHeader));
                rows.add(Row.check(R.string.ButterGramHidePhone, SharedSettings.buttergramHidePhone));
                rows.add(Row.info(R.string.ButterGramHidePhoneInfo));
                rows.add(Row.header(R.string.ButterGramPremiumHeader));
                rows.add(Row.check(R.string.ButterGramLocalPremium, SharedSettings.buttergramLocalPremium));
                rows.add(Row.info(R.string.ButterGramLocalPremiumInfo));
                break;
            case PAGE_APPEARANCE:
                rows.add(Row.preview(ButterGramPreviewView.KIND_LIST));
                rows.add(Row.info(R.string.ButterGramPreviewHint));
                rows.add(Row.header(R.string.ButterGramInterface));
                rows.add(Row.check(R.string.ButterGramHidePremiumRows, SharedSettings.buttergramHidePremiumRows));
                rows.add(Row.check(R.string.ButterGramHideStories, SharedSettings.buttergramHideStories));
                rows.add(Row.check(R.string.ButterGramBigFont, SharedSettings.buttergramBigFont));
                rows.add(Row.themes(R.string.ButterGramThemeSettings, R.drawable.msg_theme));
                rows.add(Row.check(R.string.ButterGramForceSnow, SharedSettings.buttergramForceSnow));
                rows.add(Row.info(R.string.ButterGramAppearanceInfo));
                rows.add(Row.header(R.string.ButterGramListHeader));
                rows.add(Row.editTitle(R.string.ButterGramTitleText, SharedSettings.buttergramCustomTitle.get() == null || SharedSettings.buttergramCustomTitle.get().isEmpty() ? "ButterGram" : SharedSettings.buttergramCustomTitle.get()));
                rows.add(Row.check(R.string.ButterGramCenterTitle, SharedSettings.buttergramCenterTitle));
                rows.add(Row.check(R.string.ButterGramHideFab, SharedSettings.buttergramHideFab));
                rows.add(Row.check(R.string.ButterGramHideSearch, SharedSettings.buttergramHideSearch));
                rows.add(Row.info(R.string.ButterGramListInfo));
                rows.add(Row.header(R.string.ButterGramShapesHeader));
                rows.add(Row.preview(ButterGramPreviewView.KIND_LIST));
                rows.add(Row.slider(R.string.ButterGramAvatarRadius, SharedSettings.buttergramAvatarRadius, PERCENT_AVATAR, avatarLabels()));
                rows.add(Row.preview(ButterGramPreviewView.KIND_STICKER));
                rows.add(Row.slider(R.string.ButterGramStickerSize, SharedSettings.buttergramStickerScale, PERCENT_STICKER, percentLabels(PERCENT_STICKER)));
                rows.add(Row.info(R.string.ButterGramShapesInfo));
                break;
            case PAGE_CHATS:
                rows.add(Row.preview(ButterGramPreviewView.KIND_BUBBLE));
                rows.add(Row.info(R.string.ButterGramPreviewHint));
                rows.add(Row.header(R.string.ButterGramMessages));
                rows.add(Row.check(R.string.ButterGramSaveDeleted, SharedSettings.buttergramSaveDeleted));
                rows.add(Row.check(R.string.ButterGramMarkDeleted, SharedSettings.buttergramMarkDeleted));
                rows.add(Row.colorPicker(R.string.ButterGramDeletedColor, currentDeletedColorName()));
                rows.add(Row.info(R.string.ButterGramSaveDeletedInfo));
                rows.add(Row.header(R.string.ButterGramDetailsHeader));
                rows.add(Row.check(R.string.ButterGramExactTime, SharedSettings.buttergramExactTime));
                rows.add(Row.check(R.string.ButterGramShowIds, SharedSettings.buttergramShowIds));
                rows.add(Row.check(R.string.ButterGramEditHistorySetting, SharedSettings.buttergramEditHistory));
                rows.add(Row.info(R.string.ButterGramEditHistoryInfo));
                rows.add(Row.header(R.string.ButterGramBubblesHeader));
                rows.add(Row.preview(ButterGramPreviewView.KIND_BUBBLE));
                rows.add(Row.check(R.string.ButterGramNoTail, SharedSettings.buttergramNoTail));
                rows.add(Row.check(R.string.ButterGramEditedIcon, SharedSettings.buttergramEditedIcon));
                rows.add(Row.check(R.string.ButterGramShowForwards, SharedSettings.buttergramShowForwards));
                rows.add(Row.check(R.string.ButterGramHideSideShare, SharedSettings.buttergramHideSideShare));
                rows.add(Row.check(R.string.ButterGramHideReactionsIn, SharedSettings.buttergramHideReactionsIn));
                rows.add(Row.check(R.string.ButterGramHideReactionsOut, SharedSettings.buttergramHideReactionsOut));
                rows.add(Row.info(R.string.ButterGramBubblesInfo));
                rows.add(Row.header(R.string.ButterGramGesturesHeader));
                rows.add(Row.choice(R.string.ButterGramSwipeAction, SharedSettings.buttergramSwipeAction, new int[]{0, 1, 2, 3}, swipeLabels()));
                rows.add(Row.category(R.string.ButterGramMenuTitle, R.drawable.msg_list, PAGE_MENU));
                rows.add(Row.check(R.string.ButterGramEndlessRecents, SharedSettings.buttergramEndlessRecents));
                rows.add(Row.info(R.string.ButterGramGesturesInfo));
                break;
            case PAGE_MENU:
                rows.add(Row.header(R.string.ButterGramMenuHeader));
                rows.add(Row.check(R.string.ButterGramMenuReply, SharedSettings.buttergramHideMenuReply));
                rows.add(Row.check(R.string.ButterGramMenuForward, SharedSettings.buttergramHideMenuForward));
                rows.add(Row.check(R.string.ButterGramMenuCopy, SharedSettings.buttergramHideMenuCopy));
                rows.add(Row.check(R.string.ButterGramMenuCopyLink, SharedSettings.buttergramHideMenuCopyLink));
                rows.add(Row.check(R.string.ButterGramMenuShare, SharedSettings.buttergramHideMenuShare));
                rows.add(Row.check(R.string.ButterGramMenuSave, SharedSettings.buttergramHideMenuSave));
                rows.add(Row.check(R.string.ButterGramMenuTranslate, SharedSettings.buttergramHideMenuTranslate));
                rows.add(Row.check(R.string.ButterGramMenuPin, SharedSettings.buttergramHideMenuPin));
                rows.add(Row.check(R.string.ButterGramMenuReport, SharedSettings.buttergramHideMenuReport));
                rows.add(Row.info(R.string.ButterGramMenuInfo));
                break;
            case PAGE_OTHER:
                rows.add(Row.header(R.string.ButterGramSupportHeader));
                rows.add(Row.action(R.string.ButterGramTonkeeper, R.drawable.buttergram_tonkeeper, ACTION_TONKEEPER));
                rows.add(Row.action(R.string.ButterGramHowToGet, R.drawable.msg_premium_badge, ACTION_HOW_TO));
                rows.add(Row.info(R.string.ButterGramSupportInfo));
                rows.add(Row.header(R.string.ButterGramDataHeader));
                rows.add(Row.action(R.string.ButterGramResetSettings, R.drawable.msg_reset, ACTION_RESET));
                rows.add(Row.shadow());
                rows.add(Row.header(R.string.ButterGramAbout));
                rows.add(Row.plainValue(R.string.ButterGramVersion, BuildVars.BUILD_VERSION_STRING));
                rows.add(Row.link(R.string.ButterGramLicense, "GPLv2", URL_LICENSE));
                rows.add(Row.info(R.string.ButterGramAboutInfo));
                break;
            default:
                rows.add(Row.logo());
                rows.add(Row.header(R.string.ButterGramCategories));
                rows.add(Row.category(R.string.ButterGramGeneral, R.drawable.msg_settings, PAGE_GENERAL));
                rows.add(Row.category(R.string.ButterGramAppearanceCategory, R.drawable.msg_theme, PAGE_APPEARANCE));
                rows.add(Row.category(R.string.ButterGramChats, R.drawable.msg_msgbubble, PAGE_CHATS));
                rows.add(Row.category(R.string.ButterGramOther, R.drawable.msg_info, PAGE_OTHER));
                rows.add(Row.shadow());
                rows.add(Row.header(R.string.ButterGramLinks));
                rows.add(Row.username(R.string.ButterGramChannel, USERNAME_CHANNEL));
                rows.add(Row.username(R.string.ButterGramChatLink, USERNAME_CHAT));
                break;
        }
    }

    private void showColorDialog() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] names = new CharSequence[DELETED_COLORS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = LocaleController.getString(DELETED_COLOR_NAMES[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ButterGramColorTitle));
        builder.setItems(names, (dialog, which) -> {
            SharedSettings.buttergramDeletedColor.set(DELETED_COLORS[which]);
            buildRows();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    public View createView(Context context) {
        buildRows();

        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(page == PAGE_MAIN ? "" : LocaleController.getString(titleForPage()));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> onRowClicked(position));
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    private void onRowClicked(int position) {
        if (position < 0 || position >= rows.size()) {
            return;
        }
        Row row = rows.get(position);
        switch (row.action) {
            case ACTION_TOGGLE:
                if (row.setting != null) {
                    row.setting.toggle();
                    adapter.notifyItemChanged(position);
                    invalidatePreviews();
                }
                break;
            case ACTION_OPEN_PAGE:
                presentFragment(new ButterGramSettingsActivity(row.page));
                break;
            case ACTION_OPEN_URL:
                if (row.url != null && getParentActivity() != null) {
                    Browser.openUrl(getParentActivity(), row.url);
                }
                break;
            case ACTION_OPEN_USERNAME:
                if (row.url != null) {
                    getMessagesController().openByUserName(row.url, this, 1);
                }
                break;
            case ACTION_PICK_COLOR:
                showColorDialog();
                break;
            case ACTION_CHOICE:
                showChoiceDialog(row);
                break;
            case ACTION_EDIT_TITLE:
                showTitleDialog();
                break;
            case ACTION_TONKEEPER:
                if (getParentActivity() != null) {
                    ButterGramSupportSheet.openDonation(getParentActivity());
                }
                break;
            case ACTION_HOW_TO:
                ButterGramSupportSheet.show(this, null, null);
                break;
            case ACTION_RESET:
                showResetDialog();
                break;
            case ACTION_OPEN_THEMES:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                break;
            default:
                break;
        }
    }

    /** Big logo, name and version at the top of the main page. */
    private static class LogoCell extends LinearLayout {
        LogoCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(24));
            setTag(RecyclerListView.TAG_NOT_SECTION);

            ImageView logo = new ImageView(context);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImageResource(R.drawable.buttergram_logo);
            addView(logo, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL));

            TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setGravity(Gravity.CENTER);
            title.setText("ButterGram");
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

            TextView version = new TextView(context);
            version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            version.setGravity(Gravity.CENTER);
            version.setText(BuildVars.BUILD_VERSION_STRING);
            addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position < 0 || position >= rows.size()) {
                return false;
            }
            return rows.get(position).action != ACTION_NONE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType >= PREVIEW_BASE) {
                view = new ButterGramPreviewView(context, viewType - PREVIEW_BASE);
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }
            switch (viewType) {
                case TYPE_LOGO:
                    view = new LogoCell(context);
                    break;
                case TYPE_HEADER:
                    view = new HeaderCell(context);
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(context);
                    break;
                case TYPE_ICON:
                case TYPE_VALUE:
                    view = new TextCell(context);
                    break;
                case TYPE_SHADOW:
                    view = new ShadowSectionCell(context);
                    break;
                case TYPE_SLIDER:
                    view = new SliderCell(context);
                    break;
                default:
                    view = new TextInfoPrivacyCell(context);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            boolean divider = position + 1 < rows.size() && isSameSection(row, rows.get(position + 1));
            switch (row.type) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString(row.title));
                    break;
                case TYPE_CHECK: {
                    boolean checked = row.setting != null && row.setting.get();
                    ((TextCheckCell) holder.itemView).setTextAndCheck(LocaleController.getString(row.title), checked, divider);
                    break;
                }
                case TYPE_ICON:
                    ((TextCell) holder.itemView).setTextAndIcon(LocaleController.getString(row.title), row.icon, divider);
                    break;
                case TYPE_VALUE:
                    ((TextCell) holder.itemView).setTextAndValue(LocaleController.getString(row.title), row.intSetting != null ? currentChoiceLabel(row) : row.value, divider);
                    break;
                case TYPE_INFO:
                    ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString(row.title));
                    break;
                case TYPE_SLIDER:
                    ((SliderCell) holder.itemView).bind(row);
                    break;
                default:
                    break;
            }
        }

        /** A divider is drawn only between two rows that are both content rows. */
        private boolean isSameSection(Row current, Row next) {
            return next.type == TYPE_CHECK || next.type == TYPE_ICON || next.type == TYPE_VALUE;
        }

        @Override
        public int getItemViewType(int position) {
            Row row = rows.get(position);
            return row.type == TYPE_PREVIEW ? PREVIEW_BASE + row.previewKind : row.type;
        }
    }
}

package org.telegram.ui;

import android.content.Context;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.settings.BooleanSetting;
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

    private static final int ACTION_NONE = 0;
    private static final int ACTION_OPEN_PAGE = 1;
    private static final int ACTION_TOGGLE = 2;
    private static final int ACTION_OPEN_URL = 3;
    private static final int ACTION_OPEN_THEMES = 4;
    private static final int ACTION_OPEN_USERNAME = 5;

    private static final class Row {
        final int type;
        final int title;       // string resource, 0 if none
        final int icon;        // drawable resource for TYPE_ICON, 0 if none
        final String value;    // text for TYPE_VALUE
        final int action;
        final int page;
        final String url;
        final BooleanSetting setting;

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
            default:
                return R.string.ButterGramSettings;
        }
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
                break;
            case PAGE_APPEARANCE:
                rows.add(Row.header(R.string.ButterGramInterface));
                rows.add(Row.check(R.string.ButterGramHidePremiumRows, SharedSettings.buttergramHidePremiumRows));
                rows.add(Row.themes(R.string.ButterGramThemeSettings, R.drawable.msg_theme));
                rows.add(Row.info(R.string.ButterGramHidePremiumInfo));
                break;
            case PAGE_CHATS:
                rows.add(Row.header(R.string.ButterGramMessages));
                rows.add(Row.check(R.string.ButterGramSaveDeleted, SharedSettings.buttergramSaveDeleted));
                rows.add(Row.info(R.string.ButterGramSaveDeletedInfo));
                break;
            case PAGE_OTHER:
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
                    ((TextCell) holder.itemView).setTextAndValue(LocaleController.getString(row.title), row.value, divider);
                    break;
                case TYPE_INFO:
                    ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString(row.title));
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
            return rows.get(position).type;
        }
    }
}

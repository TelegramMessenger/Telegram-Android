package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.ImageView;
import org.telegram.ui.Components.ButterGramPluginIcon;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import static org.telegram.messenger.AndroidUtilities.dp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ButterGramPlugins;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/** Settings > ButterGram > Plugins: installed .baddon add-ons, enable / disable / settings / delete. */
public class ButterGramPluginsActivity extends BaseFragment {
    private static final int REQUEST_PICK = 7771;

    private RecyclerListView listView;
    private Adapter adapter;
    private final ArrayList<ButterGramPlugins.Info> items = new ArrayList<>();

    @Override
    protected boolean useSimpleActionBar() {
        return true;
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

    private void reload() {
        items.clear();
        items.addAll(ButterGramPlugins.list());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.ButterGramPlugins));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position == 0) {
                pickFile();
            }
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        reload();
        return fragmentView;
    }

    private void pickFile() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            activity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.ButterGramPluginsInstall)), REQUEST_PICK);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            File tmp = new File(ApplicationLoader.applicationContext.getCacheDir(), "install" + ButterGramPlugins.EXTENSION);
            try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while (in != null && (n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            ButterGramPlugins.showInstallDialog(this, tmp, null);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() == 0;
        }

        @Override
        public int getItemCount() {
            return items.size() + 2;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                TextView button = new TextView(context);
                button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                button.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
                button.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                button.setText(LocaleController.getString(R.string.ButterGramPluginsInstall));
                view = button;
            } else if (viewType == 1) {
                TextView hint = new TextView(context);
                hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                hint.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
                view = hint;
            } else {
                view = new ItemView(context);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : position == 1 ? 1 : 2;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 1) {
                String text = LocaleController.getString(R.string.ButterGramPluginsInfo);
                if (!ButterGramPlugins.isEngineAvailable()) {
                    text += "\n\n" + LocaleController.getString(R.string.ButterGramPluginsNoEngine);
                }
                ((TextView) holder.itemView).setText(text);
            } else if (position > 1) {
                ((ItemView) holder.itemView).bind(items.get(position - 2));
            }
        }
    }

    private class ItemView extends FrameLayout {
        private final ButterGramPluginIcon icon;
        private final TextView title;
        private final TextView meta;
        private final TextView description;
        private final org.telegram.ui.Components.Switch toggle;
        private final ImageView settingsButton;
        private final ImageView deleteButton;
        private ButterGramPlugins.Info info;

        ItemView(Context context) {
            super(context);
            setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            bg.setCornerRadius(dp(18));
            card.setBackground(bg);
            card.setPadding(dp(14), dp(14), dp(14), dp(8));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            FrameLayout top = new FrameLayout(context);
            card.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

            icon = new ButterGramPluginIcon(context);
            top.addView(icon, LayoutHelper.createFrame(56, 56, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            LinearLayout names = new LinearLayout(context);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(names, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 68, 0, 64, 0));
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            names.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            meta = new TextView(context);
            meta.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            meta.setSingleLine();
            meta.setEllipsize(TextUtils.TruncateAt.END);
            names.addView(meta, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            toggle = new org.telegram.ui.Components.Switch(context);
            toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            top.addView(toggle, LayoutHelper.createFrame(37, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            top.setOnClickListener(v -> {
                if (info != null) {
                    ButterGramPlugins.setEnabled(info.id, !info.enabled);
                    reload();
                }
            });

            description = new TextView(context);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            card.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

            View divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            card.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 12, 0, 0));

            FrameLayout actions = new FrameLayout(context);
            card.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 4, 0, 0));

            settingsButton = new ImageView(context);
            settingsButton.setImageResource(R.drawable.msg_settings);
            settingsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            settingsButton.setScaleType(ImageView.ScaleType.CENTER);
            settingsButton.setOnClickListener(v -> {
                if (info != null) {
                    presentFragment(new ButterGramPluginSettingsActivity(info.id));
                }
            });
            actions.addView(settingsButton, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            deleteButton = new ImageView(context);
            deleteButton.setImageResource(R.drawable.msg_delete);
            deleteButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedRegular), PorterDuff.Mode.SRC_IN));
            deleteButton.setScaleType(ImageView.ScaleType.CENTER);
            deleteButton.setOnClickListener(v -> confirmDelete(info));
            actions.addView(deleteButton, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        }

        void bind(ButterGramPlugins.Info info) {
            this.info = info;
            icon.bind(info.icon, info.name);
            title.setText(info.name);
            SpannableStringBuilder line = new SpannableStringBuilder();
            if (!TextUtils.isEmpty(info.version)) {
                line.append(info.version.startsWith("v") ? info.version : "v" + info.version);
            }
            if (!TextUtils.isEmpty(info.author)) {
                if (line.length() > 0) {
                    line.append(" \u2022 ");
                }
                int start = line.length();
                line.append(info.author);
                if (info.author.startsWith("@")) {
                    line.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)), start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            meta.setText(line);
            meta.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            description.setText(info.description);
            description.setVisibility(TextUtils.isEmpty(info.description) ? GONE : VISIBLE);
            toggle.setChecked(info.enabled, false);
            settingsButton.setVisibility(info.enabled && ButterGramPlugins.isLoaded(info.id) ? VISIBLE : INVISIBLE);
        }
    }

    private void confirmDelete(ButterGramPlugins.Info info) {
        if (info == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(info.name);
        builder.setMessage(LocaleController.getString(R.string.ButterGramPluginsDeleteConfirm));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
            ButterGramPlugins.remove(info.id);
            reload();
        });
        showDialog(builder.create());
    }
}

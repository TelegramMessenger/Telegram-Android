package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

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
            } else if (position > 1) {
                showActions(items.get(position - 2));
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
            ButterGramPlugins.showInstallDialog(this, tmp, tmp.getName());
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void showActions(ButterGramPlugins.Info info) {
        if (getParentActivity() == null) {
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        labels.add(LocaleController.getString(info.enabled ? R.string.ButterGramPluginsDisable : R.string.ButterGramPluginsEnable));
        actions.add(() -> {
            ButterGramPlugins.setEnabled(info.id, !info.enabled);
            reload();
        });
        if (info.enabled && ButterGramPlugins.isLoaded(info.id)) {
            labels.add(LocaleController.getString(R.string.Settings));
            actions.add(() -> presentFragment(new ButterGramPluginSettingsActivity(info.id)));
        }
        labels.add(LocaleController.getString(R.string.Delete));
        actions.add(() -> {
            ButterGramPlugins.remove(info.id);
            reload();
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(info.name);
        builder.setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run());
        showDialog(builder.create());
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() != 1;
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

    private static class ItemView extends LinearLayout {
        private final TextView title;
        private final TextView subtitle;

        ItemView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        }

        void bind(ButterGramPlugins.Info info) {
            String name = info.name + (TextUtils.isEmpty(info.version) ? "" : " " + info.version);
            title.setText(name);
            String status = LocaleController.getString(!info.enabled ? R.string.ButterGramPluginsOff
                : ButterGramPlugins.isLoaded(info.id) ? R.string.ButterGramPluginsOn : R.string.ButterGramPluginsNotRunning);
            String line = status + (TextUtils.isEmpty(info.author) ? "" : " · " + info.author);
            if (!TextUtils.isEmpty(info.description)) {
                line += "\n" + info.description;
            }
            subtitle.setText(line);
        }
    }
}

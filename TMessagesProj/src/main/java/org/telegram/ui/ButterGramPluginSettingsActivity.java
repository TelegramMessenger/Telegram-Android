package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ButterGramPluginApi;
import org.telegram.messenger.ButterGramPlugins;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Settings page drawn from the JSON an add-on returns from create_settings():
 * [{"type":"header|switch|text|button","key":"...","title":"...","default":...}]
 */
public class ButterGramPluginSettingsActivity extends BaseFragment {
    private final String pluginId;
    private final ArrayList<JSONObject> rows = new ArrayList<>();
    private RecyclerListView listView;
    private Adapter adapter;

    public ButterGramPluginSettingsActivity(String pluginId) {
        this.pluginId = pluginId;
    }

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

    private void load() {
        rows.clear();
        try {
            JSONArray array = new JSONArray(ButterGramPlugins.settingsJson(pluginId));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) {
                    rows.add(o);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        ButterGramPlugins.Info info = ButterGramPlugins.get(pluginId);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(info != null ? info.name : pluginId);
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
        listView.setOnItemClickListener((view, position) -> onClick(rows.get(position), position));
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        load();
        return fragmentView;
    }

    private Object value(JSONObject row) {
        Object v = ButterGramPluginApi.decode(ButterGramPluginApi.getSetting(pluginId, row.optString("key")));
        return v != null ? v : row.opt("default");
    }

    private void onClick(JSONObject row, int position) {
        String type = row.optString("type");
        String key = row.optString("key");
        if ("switch".equals(type)) {
            Object v = value(row);
            boolean now = !(v instanceof Boolean && (Boolean) v);
            ButterGramPluginApi.setSetting(pluginId, key, String.valueOf(now));
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView instanceof TextCheckCell) {
                ((TextCheckCell) holder.itemView).setChecked(now);
            } else {
                adapter.notifyItemChanged(position);
            }
            ButterGramPlugins.action(pluginId, key);
        } else if ("text".equals(type)) {
            if (getParentActivity() == null) {
                return;
            }
            EditText edit = new EditText(getParentActivity());
            edit.setInputType(InputType.TYPE_CLASS_TEXT);
            Object v = value(row);
            edit.setText(v == null ? "" : String.valueOf(v));
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(row.optString("title"));
            builder.setView(edit);
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
                ButterGramPluginApi.setSetting(pluginId, key, JSONObject.quote(edit.getText().toString()));
                adapter.notifyItemChanged(position);
                ButterGramPlugins.action(pluginId, key);
            });
            showDialog(builder.create());
        } else if ("button".equals(type)) {
            ButterGramPlugins.action(pluginId, key);
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 0;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            String type = rows.get(position).optString("type");
            if ("header".equals(type)) {
                return 0;
            } else if ("switch".equals(type)) {
                return 1;
            }
            return "button".equals(type) ? 3 : 2; // 2 = text value, 3 = button
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = new HeaderCell(context);
            } else if (viewType == 1) {
                view = new TextCheckCell(context);
            } else {
                view = new TextCell(context); // text value (2) and button (3)
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            JSONObject row = rows.get(position);
            String type = row.optString("type");
            String title = row.optString("title");
            boolean divider = position + 1 < rows.size() && !"header".equals(rows.get(position + 1).optString("type"));
            Object v = value(row);
            if ("header".equals(type)) {
                ((HeaderCell) holder.itemView).setText(title);
            } else if ("switch".equals(type)) {
                ((TextCheckCell) holder.itemView).setTextAndCheck(title, v instanceof Boolean && (Boolean) v, divider);
            } else if ("text".equals(type)) {
                ((TextCell) holder.itemView).setTextAndValue(title, v == null ? "" : String.valueOf(v), divider);
            } else {
                TextCell cell = (TextCell) holder.itemView;
                cell.setText(title, divider);
                cell.setColors(-1, Theme.key_windowBackgroundWhiteBlueText4);
            }
        }
    }
}

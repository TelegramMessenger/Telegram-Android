package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
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
import org.telegram.messenger.ButterGramChangeLog;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

/** "Change center": every message that was edited or deleted, newest first. Tap to open the chat. */
public class ButterGramChangesActivity extends BaseFragment {
    private static final int FILTER_ALL = 0;
    private static final int FILTER_EDITED = 1;
    private static final int FILTER_DELETED = 2;

    private RecyclerListView listView;
    private Adapter adapter;
    private TextView emptyView;
    private final List<ButterGramChangeLog.Item> items = new ArrayList<>();
    private int filter = FILTER_ALL;

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
        for (ButterGramChangeLog.Item item : ButterGramChangeLog.all()) {
            if (filter == FILTER_EDITED && item.kind != ButterGramChangeLog.KIND_EDITED) {
                continue;
            }
            if (filter == FILTER_DELETED && item.kind != ButterGramChangeLog.KIND_DELETED) {
                continue;
            }
            items.add(item);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.ButterGramChanges));
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
                return;
            }
            open(items.get(position - 1));
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position == 0) {
                return false;
            }
            AndroidUtilities.addToClipboard(items.get(position - 1).text);
            return true;
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText(LocaleController.getString(R.string.ButterGramChangesEmpty));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        frame.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        reload();
        return fragmentView;
    }

    private void open(ButterGramChangeLog.Item item) {
        if (item.dialogId == 0) {
            return;
        }
        Bundle args = new Bundle();
        if (DialogObject.isUserDialog(item.dialogId)) {
            args.putLong("user_id", item.dialogId);
        } else {
            args.putLong("chat_id", -item.dialogId);
        }
        if (item.kind == ButterGramChangeLog.KIND_EDITED) {
            args.putInt("message_id", item.messageId);
        }
        presentFragment(new ChatActivity(args));
    }

    private void showFilterDialog() {
        if (getParentActivity() == null) {
            return;
        }
        String[] labels = {
            LocaleController.getString(R.string.ButterGramChangesAll),
            LocaleController.getString(R.string.ButterGramChangesEdited),
            LocaleController.getString(R.string.ButterGramChangesDeleted),
            LocaleController.getString(R.string.ButterGramChangesClear)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ButterGramChanges));
        builder.setItems(labels, (dialog, which) -> {
            if (which == 3) {
                ButterGramChangeLog.clear();
                reload();
            } else {
                filter = which;
                reload();
            }
        });
        showDialog(builder.create());
    }

    private String chatName(ButterGramChangeLog.Item item) {
        MessagesController controller = MessagesController.getInstance(item.account);
        if (DialogObject.isUserDialog(item.dialogId)) {
            TLRPC.User user = controller.getUser(item.dialogId);
            if (user != null) {
                return org.telegram.messenger.UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = controller.getChat(-item.dialogId);
            if (chat != null) {
                return chat.title;
            }
        }
        return String.valueOf(item.dialogId);
    }

    private class ItemView extends LinearLayout {
        private final TextView head;
        private final TextView body;
        private final TextView extra;

        ItemView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
            head = new TextView(context);
            head.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            body = new TextView(context);
            body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            body.setMaxLines(4);
            body.setEllipsize(TextUtils.TruncateAt.END);
            body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            extra = new TextView(context);
            extra.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            extra.setMaxLines(3);
            extra.setEllipsize(TextUtils.TruncateAt.END);
            extra.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            addView(extra, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        void bind(ButterGramChangeLog.Item item) {
            boolean deleted = item.kind == ButterGramChangeLog.KIND_DELETED;
            head.setTextColor(deleted ? 0xFFE5534B : Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            head.setText(LocaleController.getString(deleted ? R.string.ButterGramChangesDeletedIn : R.string.ButterGramChangesEditedIn) + " " + chatName(item) + " · " + LocaleController.formatShortDateTime(item.time));
            String text = TextUtils.isEmpty(item.text) ? LocaleController.getString(R.string.ButterGramChangesNoText) : item.text;
            body.setText(text);
            if (!deleted && !TextUtils.isEmpty(item.newText)) {
                extra.setVisibility(VISIBLE);
                extra.setText("→ " + item.newText);
            } else {
                extra.setVisibility(GONE);
            }
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() != 0;
        }

        @Override
        public int getItemCount() {
            return items.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                TextView filterButton = new TextView(context);
                filterButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                filterButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                filterButton.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
                filterButton.setOnClickListener(v -> showFilterDialog());
                view = filterButton;
            } else {
                view = new ItemView(context);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                String name = filter == FILTER_EDITED ? LocaleController.getString(R.string.ButterGramChangesEdited)
                    : filter == FILTER_DELETED ? LocaleController.getString(R.string.ButterGramChangesDeleted)
                    : LocaleController.getString(R.string.ButterGramChangesAll);
                ((TextView) holder.itemView).setText(LocaleController.getString(R.string.ButterGramChangesFilter) + ": " + name);
            } else {
                ((ItemView) holder.itemView).bind(items.get(position - 1));
            }
        }
    }
}

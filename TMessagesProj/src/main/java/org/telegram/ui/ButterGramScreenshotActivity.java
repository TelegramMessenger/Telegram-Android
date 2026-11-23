package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Screenshot editor: shows the messages that were on screen, lets you hide whole messages or parts of the text
 * (the parts become a spoiler), pick a background and send clean images. It works on copies, the chat is not touched.
 */
public class ButterGramScreenshotActivity extends BaseFragment {
    private static final int BG_WALLPAPER = 0;
    private static final int BG_DARK = 1;
    private static final int BG_LIGHT = 2;

    private final int account = UserConfig.selectedAccount;
    private final ArrayList<TLRPC.Message> copies = new ArrayList<>();
    private ShotView shot;
    private RecyclerListView listView;
    private int background = BG_WALLPAPER;
    private final ArrayList<TextView> chips = new ArrayList<>();

    public ButterGramScreenshotActivity(ArrayList<MessageObject> source) {
        for (MessageObject object : source) {
            TLRPC.Message copy = copy(object.messageOwner);
            if (copy != null) {
                copies.add(copy);
            }
        }
    }

    private static TLRPC.Message copy(TLRPC.Message message) {
        try {
            NativeByteBuffer buffer = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(buffer);
            buffer.position(0);
            TLRPC.Message result = TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(false), false);
            buffer.reuse();
            if (result != null) {
                result.dialog_id = message.dialog_id;
            }
            return result;
        } catch (Throwable e) {
            return null;
        }
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

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.ButterGramShotEditor));
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

        shot = new ShotView(context);
        rebuild();

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter(context));
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    // ---------------------------------------------------------------------------------------------

    private void rebuild() {
        shot.removeAllViews();
        for (int i = 0; i < copies.size(); i++) {
            final int index = i;
            MessageObject object = new MessageObject(account, copies.get(i), true, false);
            ChatMessageCell cell = new ChatMessageCell(shot.getContext(), account, false, null, null);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            });
            cell.isChat = false;
            cell.setFullyDraw(true);
            cell.setMessageObject(object, null, false, false, false);
            TapFrame holder = new TapFrame(shot.getContext());
            holder.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            holder.setOnClickListener(v -> showActions(index));
            shot.addView(holder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        shot.requestLayout();
        shot.invalidate();
    }

    private void showActions(int index) {
        if (getParentActivity() == null || index >= copies.size()) {
            return;
        }
        final TLRPC.Message message = copies.get(index);
        boolean hasText = !TextUtils.isEmpty(message.message);
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        labels.add(LocaleController.getString(R.string.ButterGramShotHideMessage));
        actions.add(0);
        if (hasText) {
            labels.add(LocaleController.getString(R.string.ButterGramShotHidePart));
            actions.add(1);
            labels.add(LocaleController.getString(R.string.ButterGramShotHideAll));
            actions.add(2);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
            int action = actions.get(which);
            if (action == 0) {
                copies.remove(index);
                rebuild();
            } else if (action == 1) {
                showSelectDialog(message);
            } else {
                addSpoiler(message, 0, message.message.length());
            }
        });
        showDialog(builder.create());
    }

    private void showSelectDialog(TLRPC.Message message) {
        final EditText edit = new EditText(getParentActivity());
        edit.setText(message.message);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        edit.setBackground(null);
        edit.setTextIsSelectable(true);
        edit.setShowSoftInputOnFocus(false);
        edit.setKeyListener(null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ButterGramShotSelectTitle));
        builder.setMessage(LocaleController.getString(R.string.ButterGramShotSelectHint));
        builder.setView(edit);
        builder.setPositiveButton(LocaleController.getString(R.string.ButterGramShotHide), (d, w) -> {
            int start = Math.min(edit.getSelectionStart(), edit.getSelectionEnd());
            int end = Math.max(edit.getSelectionStart(), edit.getSelectionEnd());
            if (start >= 0 && end > start) {
                addSpoiler(message, start, end - start);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void addSpoiler(TLRPC.Message message, int offset, int length) {
        TLRPC.TL_messageEntitySpoiler entity = new TLRPC.TL_messageEntitySpoiler();
        entity.offset = offset;
        entity.length = length;
        message.entities.add(entity);
        rebuild();
    }

    private void setBackground(int mode) {
        background = mode;
        shot.invalidate();
        for (int i = 0; i < chips.size(); i++) {
            styleChip(chips.get(i), i == mode);
        }
    }

    private void styleChip(TextView chip, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(18));
        d.setColor(selected ? Theme.getColor(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_windowBackgroundWhite));
        chip.setBackground(d);
        chip.setTextColor(selected ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
    }

    // ---------------------------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------------------------

    private void send() {
        if (getParentActivity() == null || shot.getChildCount() == 0) {
            return;
        }
        try {
            int width = shot.getWidth();
            int total = shot.getHeight();
            int maxHeight = width * 3;
            File dir = new File(AndroidUtilities.getCacheDir(), "buttergram_shots");
            dir.mkdirs();
            ArrayList<Uri> uris = new ArrayList<>();
            int count = shot.getChildCount();
            int i = 0;
            int page = 0;
            while (i < count) {
                int first = i;
                int start = Math.max(0, shot.getChildAt(first).getTop() - AndroidUtilities.dp(10));
                int end = shot.getChildAt(first).getBottom();
                i++;
                while (i < count && shot.getChildAt(i).getBottom() + AndroidUtilities.dp(10) - start <= maxHeight) {
                    end = shot.getChildAt(i).getBottom();
                    i++;
                }
                end = Math.min(total, end + AndroidUtilities.dp(10));
                Bitmap bitmap = Bitmap.createBitmap(width, Math.max(1, end - start), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.translate(0, -start);
                shot.draw(canvas);
                File file = new File(dir, "shot_" + System.currentTimeMillis() + "_" + (page++) + ".png");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                bitmap.recycle();
                uris.add(FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file));
            }
            Intent intent = uris.size() == 1 ? new Intent(Intent.ACTION_SEND) : new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("image/png");
            if (uris.size() == 1) {
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setPackage(getParentActivity().getPackageName());
            getParentActivity().startActivity(intent);
        } catch (Throwable e) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ButterGramShotFailed)).show();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------------

    /** A wrapper that turns every touch into a click, so the message cell inside cannot react to it. */
    private static class TapFrame extends FrameLayout {
        TapFrame(Context context) {
            super(context);
            setClickable(true);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }

    /** The picture that is exported: background plus the messages. */
    private class ShotView extends LinearLayout {
        ShotView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getMeasuredWidth();
            int h = getMeasuredHeight();
            if (background == BG_DARK) {
                canvas.drawColor(0xFF0E1621);
                return;
            }
            if (background == BG_LIGHT) {
                canvas.drawColor(0xFFE6EBEF);
                return;
            }
            Drawable d = Theme.getCachedWallpaperNonBlocking();
            if (Theme.wallpaperLoadTask != null) {
                invalidate();
            }
            if (d == null) {
                canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
            } else if (d instanceof ColorDrawable || d instanceof GradientDrawable || d instanceof MotionBackgroundDrawable) {
                d.setBounds(0, 0, w, h);
                d.draw(canvas);
            } else if (d instanceof BitmapDrawable) {
                BitmapDrawable bd = (BitmapDrawable) d;
                bd.setFilterBitmap(true);
                if (bd.getTileModeX() == Shader.TileMode.REPEAT) {
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.save();
                    canvas.scale(scale, scale);
                    d.setBounds(0, 0, (int) Math.ceil(w / scale), (int) Math.ceil(h / scale));
                    d.draw(canvas);
                    canvas.restore();
                } else {
                    float scale = Math.max((float) w / d.getIntrinsicWidth(), (float) h / d.getIntrinsicHeight());
                    int dw = (int) Math.ceil(d.getIntrinsicWidth() * scale);
                    int dh = (int) Math.ceil(d.getIntrinsicHeight() * scale);
                    d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
                    d.draw(canvas);
                }
            } else {
                StoryEntry.drawBackgroundDrawable(canvas, d, w, h);
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
            return false;
        }

        @Override
        public int getItemCount() {
            return 4;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = shot;
                if (shot.getParent() instanceof ViewGroup) {
                    ((ViewGroup) shot.getParent()).removeView(shot);
                }
            } else if (viewType == 1) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
                String[] names = {
                    LocaleController.getString(R.string.ButterGramShotBgWallpaper),
                    LocaleController.getString(R.string.ButterGramShotBgDark),
                    LocaleController.getString(R.string.ButterGramShotBgLight)
                };
                chips.clear();
                for (int i = 0; i < names.length; i++) {
                    final int mode = i;
                    TextView chip = new TextView(context);
                    chip.setText(names[i]);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    chip.setGravity(Gravity.CENTER);
                    chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
                    chip.setOnClickListener(v -> setBackground(mode));
                    styleChip(chip, mode == background);
                    chips.add(chip);
                    row.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
                }
                view = row;
            } else if (viewType == 2) {
                TextView button = new TextView(context);
                button.setText(LocaleController.getString(R.string.ButterGramShotSend));
                button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                button.setTypeface(AndroidUtilities.bold());
                button.setTextColor(0xFFFFFFFF);
                button.setGravity(Gravity.CENTER);
                GradientDrawable d = new GradientDrawable();
                d.setCornerRadius(AndroidUtilities.dp(10));
                d.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                button.setBackground(d);
                button.setOnClickListener(v -> send());
                FrameLayout box = new FrameLayout(context);
                box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
                box.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
                view = box;
            } else {
                TextView hint = new TextView(context);
                hint.setText(LocaleController.getString(R.string.ButterGramShotHint));
                hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                hint.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(32));
                view = hint;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }
    }
}

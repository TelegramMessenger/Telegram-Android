package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.ButterGram;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Stories.recorder.StoryEntry;

/**
 * A preview made of real chat message cells on the real chat wallpaper, so it looks exactly like the app.
 * The fake messages use a negative id; ButterGram.isDeleted treats that id as "show the deleted mark".
 */
public class ButterGramMessagesPreviewCell extends LinearLayout {
    public static final int MODE_BUBBLES = 0;
    public static final int MODE_STICKERS = 1;

    private final int mode;
    private final int account = UserConfig.selectedAccount;
    private final ChatMessageCell[] cells = new ChatMessageCell[2];
    private final MessageObject[] messages = new MessageObject[2];
    private ImageView sticker;
    private FrameLayout stickerBox;
    private final Path clip = new Path();
    private final RectF clipRect = new RectF();

    public ButterGramMessagesPreviewCell(Context context, int mode) {
        super(context);
        this.mode = mode;
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        setPadding(dp(16), dp(16), dp(16), dp(16));

        for (int i = 0; i < cells.length; i++) {
            cells[i] = new ChatMessageCell(context, account, false, null, null);
            cells[i].setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            });
            cells[i].isChat = false;
            cells[i].setFullyDraw(true);
        }
        addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (mode == MODE_STICKERS) {
            stickerBox = new FrameLayout(context);
            sticker = new ImageView(context);
            sticker.setImageResource(R.drawable.buttergram_logo);
            sticker.setScaleType(ImageView.ScaleType.FIT_CENTER);
            stickerBox.addView(sticker, LayoutHelper.createFrame(112, 112, android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
            addView(stickerBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 170));
        }
        addView(cells[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        refresh();
    }

    private MessageObject build(boolean out, String text, boolean full) {
        TLRPC.Message message = new TLRPC.TL_message();
        message.message = text;
        message.date = (int) (System.currentTimeMillis() / 1000) - 120;
        message.dialog_id = 1;
        message.flags = 259;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = UserConfig.getInstance(account).getClientUserId();
        message.id = ButterGram.PREVIEW_MESSAGE_ID;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = out;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = 0;
        if (full) {
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS | TLRPC.MESSAGE_FLAG_EDITED;
            message.views = 1200;
            message.forwards = 12;
            message.edit_date = message.date + 30;
            TLRPC.TL_messageReactions reactions = new TLRPC.TL_messageReactions();
            TLRPC.TL_reactionCount count = new TLRPC.TL_reactionCount();
            TLRPC.TL_reactionEmoji emoji = new TLRPC.TL_reactionEmoji();
            emoji.emoticon = "❤";
            count.reaction = emoji;
            count.count = 3;
            reactions.results.add(count);
            message.reactions = reactions;
        }
        MessageObject object = new MessageObject(account, message, true, false);
        object.resetLayout();
        object.eventId = 1;
        return object;
    }

    /** Rebuilds the messages so that every ButterGram option is applied again. */
    public void refresh() {
        String first = LocaleController.getString(R.string.ButterGramPreviewText);
        messages[0] = build(false, first, mode == MODE_BUBBLES);
        messages[1] = build(true, mode == MODE_BUBBLES ? "ButterGram 🧈" : "OK", false);
        for (int i = 0; i < cells.length; i++) {
            cells[i].setMessageObject(messages[i], null, false, false, false);
        }
        if (sticker != null) {
            float size = 112 * ButterGram.stickerScale();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sticker.getLayoutParams();
            lp.width = dp(size);
            lp.height = dp(size);
            sticker.setLayoutParams(lp);
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipRect.set(dp(16), dp(8), w - dp(16), h - dp(8));
        clip.reset();
        clip.addRoundRect(clipRect, dp(14), dp(14), Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clip);
        Drawable d = Theme.getCachedWallpaperNonBlocking();
        if (Theme.wallpaperLoadTask != null) {
            invalidate();
        }
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        if (d == null) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
        } else if (d instanceof ColorDrawable || d instanceof GradientDrawable || d instanceof MotionBackgroundDrawable) {
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
        } else if (d instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) d;
            bd.setFilterBitmap(true);
            if (bd.getTileModeX() == Shader.TileMode.REPEAT) {
                float scale = 2.0f / org.telegram.messenger.AndroidUtilities.density;
                canvas.scale(scale, scale);
                d.setBounds(0, 0, (int) Math.ceil(w / scale), (int) Math.ceil(h / scale));
            } else {
                float scale = Math.max((float) w / d.getIntrinsicWidth(), (float) h / d.getIntrinsicHeight());
                int dw = (int) Math.ceil(d.getIntrinsicWidth() * scale);
                int dh = (int) Math.ceil(d.getIntrinsicHeight() * scale);
                d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            }
            d.draw(canvas);
        } else {
            StoryEntry.drawBackgroundDrawable(canvas, d, w, h);
        }
        canvas.restore();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clip);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
    }
}

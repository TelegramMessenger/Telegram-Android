package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ButterGram;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.utils.settings.SharedSettings;

/**
 * Small live mock-ups drawn with a canvas, used by the ButterGram settings to show what an option does.
 * Everything is read from the settings on every draw, so calling invalidate() is enough to refresh.
 */
public class ButterGramPreviewView extends View {
    public static final int KIND_LIST = 0;
    public static final int KIND_STICKER = 1;
    public static final int KIND_BUBBLE = 2;

    private static final int BUTTER = 0xFFCFA33A;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public ButterGramPreviewView(Context context, int kind) {
        super(context);
        this.kind = kind;
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h;
        switch (kind) {
            case KIND_LIST:
                h = 372;
                break;
            case KIND_STICKER:
                h = 250;
                break;
            default:
                h = 200;
                break;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(h));
    }

    private int color(int key) {
        return Theme.getColor(key);
    }

    private void drawText(Canvas canvas, String s, float x, float baseline, float sizeDp, int color, boolean bold, Paint.Align align) {
        text.setTextSize(dp(sizeDp));
        text.setColor(color);
        text.setTypeface(bold ? AndroidUtilities.bold() : Typeface.DEFAULT);
        text.setTextAlign(align);
        canvas.drawText(s, x, baseline, text);
    }

    private float textWidth(String s, float sizeDp, boolean bold) {
        text.setTextSize(dp(sizeDp));
        text.setTypeface(bold ? AndroidUtilities.bold() : Typeface.DEFAULT);
        return text.measureText(s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float l = dp(16);
        float t = dp(8);
        float r = w - dp(16);
        float b = h - dp(8);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color(Theme.key_windowBackgroundGray));
        rect.set(l, t, r, b);
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);

        canvas.save();
        path.reset();
        path.addRoundRect(rect, dp(14), dp(14), Path.Direction.CW);
        canvas.clipPath(path);
        switch (kind) {
            case KIND_LIST:
                drawList(canvas, l, t, r, b);
                break;
            case KIND_STICKER:
                drawSticker(canvas, l, t, r, b);
                break;
            default:
                drawBubble(canvas, l, t, r, b);
                break;
        }
        canvas.restore();
    }

    // ---------------------------------------------------------------------------------------------
    // Chat list: title, search, avatars, floating button
    // ---------------------------------------------------------------------------------------------

    private Drawable logoDrawable;

    private void drawAvatar(Canvas canvas, float cx, float cy, float size, int fill, String letter, boolean logo) {
        float radius = ButterGram.avatarRadius(dp(size / 2f));
        rect.set(cx - dp(size / 2f), cy - dp(size / 2f), cx + dp(size / 2f), cy + dp(size / 2f));
        if (logo) {
            if (logoDrawable == null) {
                logoDrawable = ContextCompat.getDrawable(getContext(), R.drawable.buttergram_logo);
            }
            if (logoDrawable != null) {
                canvas.save();
                path.reset();
                path.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.clipPath(path);
                logoDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
                logoDrawable.draw(canvas);
                canvas.restore();
                return;
            }
        }
        paint.setColor(fill);
        canvas.drawRoundRect(rect, radius, radius, paint);
        drawText(canvas, letter, cx, cy + dp(7), 20, 0xFFFFFFFF, true, Paint.Align.CENTER);
    }

    private void drawChecks(Canvas canvas, float right, float cy, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        for (int k = 0; k < 2; k++) {
            float x = right - dp(k * 5);
            canvas.drawLine(x - dp(9), cy, x - dp(6), cy + dp(3), paint);
            canvas.drawLine(x - dp(6), cy + dp(3), x - dp(1), cy - dp(3), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawList(Canvas canvas, float l, float t, float r, float b) {
        // real action bar colours, like the chat list of the app
        paint.setColor(color(Theme.key_actionBarDefault));
        canvas.drawRect(l, t, r, t + dp(56), paint);
        int titleColor = color(Theme.key_actionBarDefaultTitle);
        String title = ButterGram.listTitle();
        float baseline = t + dp(36);
        if (SharedSettings.buttergramCenterTitle.get()) {
            drawText(canvas, title, (l + r) / 2f, baseline, 20, titleColor, true, Paint.Align.CENTER);
        } else {
            drawText(canvas, title, l + dp(20), baseline, 20, titleColor, true, Paint.Align.LEFT);
        }
        if (SharedSettings.buttergramHideSearch.get()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(color(Theme.key_actionBarDefaultIcon));
            float cx = r - dp(30);
            float cy = t + dp(26);
            canvas.drawCircle(cx, cy, dp(7), paint);
            canvas.drawLine(cx + dp(5), cy + dp(5), cx + dp(10), cy + dp(10), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float y = t + dp(56);
        paint.setColor(color(Theme.key_windowBackgroundWhite));
        canvas.drawRect(l, y, r, b, paint);
        if (!SharedSettings.buttergramHideSearch.get()) {
            paint.setColor(color(Theme.key_windowBackgroundGray));
            rect.set(l + dp(12), y + dp(6), r - dp(12), y + dp(42));
            canvas.drawRoundRect(rect, dp(18), dp(18), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));
            canvas.drawCircle(l + dp(30), y + dp(23), dp(5.5f), paint);
            canvas.drawLine(l + dp(34), y + dp(27), l + dp(38), y + dp(31), paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(canvas, LocaleController.getString(R.string.Search), l + dp(48), y + dp(29), 15, color(Theme.key_windowBackgroundWhiteGrayText), false, Paint.Align.LEFT);
            y += dp(48);
        }
        int nameColor = color(Theme.key_chats_name);
        int messageColor = color(Theme.key_chats_message);
        int dateColor = color(Theme.key_chats_date);
        String time = ButterGram.formatTime(System.currentTimeMillis() / 1000).replaceAll(":\\d\\d(?=$| )", "");
        String hello = LocaleController.getString(R.string.ButterGramPreviewText);
        String you = LocaleController.getString(R.string.FromYou) + ": ";
        for (int i = 0; i < 3; i++) {
            float rowTop = y + i * dp(72);
            if (rowTop + dp(72) > b + dp(2)) {
                break;
            }
            drawAvatar(canvas, l + dp(38), rowTop + dp(36), 56, i == 1 ? 0xFF5B8DEF : 0xFF4FA3E3, i == 1 ? "C" : "S", i == 0);
            String name = i == 0 ? "ButterGram" : i == 1 ? LocaleController.getString(R.string.ButterGramPreviewChat) : LocaleController.getString(R.string.SavedMessages);
            drawText(canvas, name, l + dp(76), rowTop + dp(29), 17, nameColor, true, Paint.Align.LEFT);
            if (i == 0) {
                Drawable badge = ButterGram.badge();
                if (badge != null) {
                    int bx = (int) (l + dp(76) + textWidth(name, 17, true) + dp(4));
                    badge.setBounds(bx, (int) (rowTop + dp(13)), bx + dp(18), (int) (rowTop + dp(31)));
                    badge.draw(canvas);
                }
            }
            if (i == 1) {
                drawText(canvas, you, l + dp(76), rowTop + dp(53), 15, color(Theme.key_chats_nameMessage), false, Paint.Align.LEFT);
                drawText(canvas, hello, l + dp(76) + textWidth(you, 15, false), rowTop + dp(53), 15, messageColor, false, Paint.Align.LEFT);
                drawChecks(canvas, r - dp(14) - textWidth(time, 13, false) - dp(5), rowTop + dp(24), color(Theme.key_chats_sentCheck));
            } else {
                drawText(canvas, hello, l + dp(76), rowTop + dp(53), 15, messageColor, false, Paint.Align.LEFT);
            }
            drawText(canvas, time, r - dp(14), rowTop + dp(28), 13, dateColor, false, Paint.Align.RIGHT);
            if (i != 1) {
                String count = i == 0 ? "3" : "12";
                float cw = Math.max(dp(22), textWidth(count, 13, true) + dp(14));
                rect.set(r - dp(14) - cw, rowTop + dp(38), r - dp(14), rowTop + dp(60));
                paint.setColor(i == 0 ? color(Theme.key_chats_unreadCounter) : color(Theme.key_chats_unreadCounterMuted));
                canvas.drawRoundRect(rect, dp(11), dp(11), paint);
                drawText(canvas, count, rect.centerX(), rowTop + dp(54), 13, color(Theme.key_chats_unreadCounterText), true, Paint.Align.CENTER);
            }
            if (i < 2) {
                paint.setColor(color(Theme.key_divider));
                canvas.drawRect(l + dp(76), rowTop + dp(71), r, rowTop + dp(72), paint);
            }
        }
        if (!SharedSettings.buttergramHideFab.get()) {
            float cx = r - dp(44);
            float cy = b - dp(44);
            paint.setColor(color(Theme.key_chats_actionBackground));
            paint.setShadowLayer(dp(4), 0, dp(2), 0x40000000);
            canvas.drawCircle(cx, cy, dp(28), paint);
            paint.clearShadowLayer();
            Drawable pencil = ContextCompat.getDrawable(getContext(), R.drawable.floating_pencil);
            if (pencil != null) {
                pencil = pencil.mutate();
                pencil.setColorFilter(new PorterDuffColorFilter(color(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
                pencil.setBounds((int) (cx - dp(14)), (int) (cy - dp(14)), (int) (cx + dp(14)), (int) (cy + dp(14)));
                pencil.draw(canvas);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stickers
    // ---------------------------------------------------------------------------------------------

    private void drawBubbleShape(Canvas canvas, float x, float y, float bw, float bh, int fill, boolean incoming) {
        paint.setColor(fill);
        rect.set(x, y, x + bw, y + bh);
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);
    }

    private void drawButterBlock(Canvas canvas, float cx, float cy, float s) {
        float u = s / 2f;
        paint.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(cx, cy - u * 0.62f);
        path.lineTo(cx + u * 0.78f, cy - u * 0.22f);
        path.lineTo(cx, cy + u * 0.18f);
        path.lineTo(cx - u * 0.78f, cy - u * 0.22f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xB8FFFFFF);
        path.reset();
        path.moveTo(cx - u * 0.78f, cy - u * 0.08f);
        path.lineTo(cx, cy + u * 0.32f);
        path.lineTo(cx, cy + u * 0.72f);
        path.lineTo(cx - u * 0.78f, cy + u * 0.32f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0x80FFFFFF);
        path.reset();
        path.moveTo(cx, cy + u * 0.32f);
        path.lineTo(cx + u * 0.78f, cy - u * 0.08f);
        path.lineTo(cx + u * 0.78f, cy + u * 0.32f);
        path.lineTo(cx, cy + u * 0.72f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawSticker(Canvas canvas, float l, float t, float r, float b) {
        String hello = LocaleController.getString(R.string.ButterGramPreviewText);
        float bw = Math.min(r - l - dp(80), textWidth(hello, 14, false) + dp(24));
        drawBubbleShape(canvas, l + dp(12), t + dp(12), bw, dp(38), color(Theme.key_chat_inBubble), true);
        drawText(canvas, hello, l + dp(24), t + dp(36), 14, color(Theme.key_chat_messageTextIn), false, Paint.Align.LEFT);

        float size = dp(96) * ButterGram.stickerScale();
        float sx = l + dp(16);
        float sy = t + dp(58);
        paint.setColor(BUTTER);
        rect.set(sx, sy, sx + size, sy + size);
        canvas.drawRoundRect(rect, size * 0.2f, size * 0.2f, paint);
        drawButterBlock(canvas, sx + size / 2f, sy + size / 2f, size * 0.62f);

        String ok = "OK";
        float ow = textWidth(ok, 14, false) + dp(28);
        drawBubbleShape(canvas, r - dp(12) - ow, b - dp(52), ow, dp(38), color(Theme.key_chat_outBubble), false);
        drawText(canvas, ok, r - dp(12) - ow / 2f, b - dp(28), 14, color(Theme.key_chat_messageTextOut), false, Paint.Align.CENTER);
    }

    // ---------------------------------------------------------------------------------------------
    // Message bubble
    // ---------------------------------------------------------------------------------------------

    private void drawBubble(Canvas canvas, float l, float t, float r, float b) {
        float x = l + dp(18);
        float y = t + dp(14);
        float bw = Math.min(r - l - dp(100), dp(240));
        float bh = dp(86);
        int fill = color(Theme.key_chat_inBubble);

        paint.setColor(fill);
        rect.set(x, y, x + bw, y + bh);
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);
        if (!SharedSettings.buttergramNoTail.get()) {
            path.reset();
            path.moveTo(x, y + bh - dp(14));
            path.lineTo(x - dp(7), y + bh);
            path.lineTo(x + dp(14), y + bh);
            path.close();
            canvas.drawPath(path, paint);
        }

        drawText(canvas, "ButterGram", x + dp(12), y + dp(24), 14, color(Theme.key_chat_inReplyNameText), true, Paint.Align.LEFT);
        drawText(canvas, LocaleController.getString(R.string.ButterGramPreviewText), x + dp(12), y + dp(46), 14, color(Theme.key_chat_messageTextIn), false, Paint.Align.LEFT);

        // meta line, built from right to left
        int metaColor = color(Theme.key_chat_inTimeText);
        float mx = x + bw - dp(10);
        float my = y + bh - dp(10);
        String time = ButterGram.formatTime(System.currentTimeMillis() / 1000);
        String edited = SharedSettings.buttergramEditedIcon.get() ? "✎ " : LocaleController.getString(R.string.EditedMessage) + " ";
        String meta = edited + time;
        drawText(canvas, meta, mx, my, 12, metaColor, false, Paint.Align.RIGHT);
        mx -= textWidth(meta, 12, false) + dp(8);
        String views = "1.2K";
        if (SharedSettings.buttergramShowForwards.get()) {
            views += "  ↗" + "12";
        }
        drawText(canvas, views, mx, my, 12, metaColor, false, Paint.Align.RIGHT);
        mx -= textWidth(views, 12, false) + dp(8);
        if (SharedSettings.buttergramMarkDeleted.get()) {
            Drawable trash = ContextCompat.getDrawable(getContext(), R.drawable.msg_delete);
            if (trash != null) {
                trash = trash.mutate();
                trash.setColorFilter(new PorterDuffColorFilter(SharedSettings.buttergramDeletedColor.get(), PorterDuff.Mode.SRC_IN));
                int s = dp(13);
                trash.setBounds((int) (mx - s), (int) (my - s + dp(1)), (int) mx, (int) (my + dp(1)));
                trash.draw(canvas);
            }
        }

        // reactions under the bubble
        if (!SharedSettings.buttergramHideReactionsIn.get()) {
            float ry = y + bh + dp(8);
            paint.setColor(color(Theme.key_chat_inReactionButtonBackground));
            rect.set(x, ry, x + dp(62), ry + dp(26));
            canvas.drawRoundRect(rect, dp(13), dp(13), paint);
            drawText(canvas, "♥ 3", x + dp(31), ry + dp(18), 13, color(Theme.key_chat_inReplyNameText), true, Paint.Align.CENTER);
        }

        // side share button
        if (!SharedSettings.buttergramHideSideShare.get()) {
            float cx = x + bw + dp(24);
            float cy = y + bh - dp(18);
            paint.setColor(0x66000000);
            canvas.drawCircle(cx, cy, dp(16), paint);
            drawText(canvas, "➜", cx, cy + dp(5), 15, 0xFFFFFFFF, true, Paint.Align.CENTER);
        }
    }
}

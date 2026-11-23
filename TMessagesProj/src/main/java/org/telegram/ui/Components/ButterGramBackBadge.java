package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** Small counter pill shown on the back button of an opened chat: how many other chats are unread. */
public class ButterGramBackBadge extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private String label = "";

    public ButterGramBackBadge(Context context) {
        super(context);
        text.setTypeface(AndroidUtilities.bold());
        text.setTextSize(dp(11));
        text.setTextAlign(Paint.Align.CENTER);
        setClickable(false);
        setVisibility(GONE);
    }

    public void setCount(int count) {
        if (count <= 0) {
            setVisibility(GONE);
            return;
        }
        label = count > 99 ? "99+" : String.valueOf(count);
        setVisibility(VISIBLE);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Math.max(dp(18), (int) Math.ceil(text.measureText(label)) + dp(10));
        setMeasuredDimension(w, dp(18));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        fill.setColor(Theme.getColor(Theme.key_chats_unreadCounter));
        rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        canvas.drawRoundRect(rect, dp(9), dp(9), fill);
        text.setColor(Theme.getColor(Theme.key_chats_unreadCounterText));
        canvas.drawText(label, getMeasuredWidth() / 2f, dp(13), text);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }
}

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/** Toxicgram badge built from the bundled Telegram verification and symbol drawables. */
public final class ToxicVerificationDrawable extends Drawable {
    private static final long BASE=920000000000000000L;
    private static final int[] COLORS={0xffe0a52f,0xff1c93e3,0xff9563df,0xff36b77a,0xffe95b61,0xff909ba9};
    private static final int[] ICONS={R.drawable.star_premium,R.drawable.verified2_fg,R.drawable.diamond,R.drawable.filled_crown_on,R.drawable.input_ai_star,0};
    public final long id;
    private final Drawable background,foreground;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int symbol;
    private int alpha=255;
    public ToxicVerificationDrawable(long id){
        this.id=id;
        int index=isToxic(id)?(int)(id-BASE):0;
        symbol=index%6;
        background=ApplicationLoader.applicationContext.getDrawable(R.drawable.verified2_bg).mutate();
        background.setTint(COLORS[index/6]);
        foreground=ICONS[symbol]==0?null:ApplicationLoader.applicationContext.getDrawable(ICONS[symbol]).mutate();
        if(foreground!=null)foreground.setTint(0xffffffff);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
    }
    public static boolean isToxic(long id){return id>=BASE && id<BASE+36;}
    @Override public void draw(Canvas canvas){
        if(!isToxic(id))return;
        Rect b=getBounds();
        int side=Math.min(b.width(),b.height());
        if(side<=0)return;
        int left=b.left+(b.width()-side)/2,top=b.top+(b.height()-side)/2;
        background.setBounds(left,top,left+side,top+side);
        background.setAlpha(alpha);
        background.draw(canvas);
        if(foreground!=null){
            // The native check already has the exact inset for verified2_bg.
            float scale=symbol==1?1f:symbol==0?.78f:symbol==4?.46f:.62f;
            int inset=Math.round(side*(1-scale)/2);
            foreground.setBounds(left+inset,top+inset,left+side-inset,top+side-inset);
            foreground.setAlpha(alpha);
            foreground.draw(canvas);
        }else{
            paint.setColor(0xffffffff);
            paint.setAlpha(alpha);
            paint.setTextSize(side*.53f);
            canvas.drawText("M",b.exactCenterX(),b.exactCenterY()-(paint.ascent()+paint.descent())/2,paint);
        }
    }
    @Override public int getIntrinsicWidth(){return AndroidUtilities.dp(17);}
    @Override public int getIntrinsicHeight(){return getIntrinsicWidth();}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

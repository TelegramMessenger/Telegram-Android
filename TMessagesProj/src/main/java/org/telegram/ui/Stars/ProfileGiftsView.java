package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProfileGiftsView extends View implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long dialogId;
    private final View avatarContainer;
    private final ProfileActivity.AvatarImageView avatarImage;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean active = true;
    public void setActive(boolean f) {
        this.active = f;
    }

    public ProfileGiftsView(Context context, int currentAccount, long dialogId, @NonNull View avatarContainer, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;

        this.avatarContainer = avatarContainer;
        this.avatarImage = avatarImage;

        this.resourcesProvider = resourcesProvider;
    }

    public float expandProgress, collapseProgress;
    public boolean isOpening;

    public void setExpandProgress(float progress) {
        if (this.expandProgress != progress) {
            this.expandProgress = progress;
            invalidate();
        }
    }

    public void setCollapseProgress(float progress, boolean isOpening) {
        this.isOpening = isOpening;
        float newProgress = Utilities.clamp01((progress - 0.3f) / 0.7f);
        if (this.collapseProgress != newProgress) {
            this.collapseProgress = newProgress;
            invalidate();
        }
    }

    private float actionBarProgress;

    public void setActionBarActionMode(float progress) {
//        if (Theme.isCurrentThemeDark()) {
//            return;
//        }
        actionBarProgress = progress;
        invalidate();
    }


    private float left, right, cy;
    private float expandY, maxExpandY;
    private final AnimatedFloat rightAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setBounds(float left, float right, float cy, boolean animated, int maxExpandY) {
        boolean changed = Math.abs(left - this.left) > 0.1f || Math.abs(right - this.right) > 0.1f || Math.abs(cy - this.cy) > 0.1f;
        this.left = left;
        this.right = right;
        if (!animated) {
            this.rightAnimated.set(this.right, true);
        }
        this.cy = cy;
        this.maxExpandY = maxExpandY + cy;
        if (changed) {
            invalidate();
        }
    }

    public void setExpandCoords(float y) {
        this.expandY = y;
        invalidate();
    }

    private float progressToInsets = 1f;

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (this.progressToInsets == progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.addView(this);
            if (gift.patternDrawable != null) gift.patternDrawable.addView(this);
        }

        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.removeView(this);
            if (gift.patternDrawable != null) gift.patternDrawable.removeView(this);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                update();
            }
        }
    }

    public final class Gift {

        public final long id;
        public final TLRPC.Document document;
        public final long documentId;
        public final int color;
        public final String slug;
        private StarsReactionsSheet.Particles particles;

        // ButterGram bubbles: pattern symbols, edge colour, cached shaders
        public TLRPC.Document patternDocument;
        public long patternDocId;
        public int patternColor, edgeColor;
        public AnimatedEmojiDrawable patternDrawable;
        private RadialGradient glowShader, bubbleShader;
        private Paint glowPaint, bubbleFill;
        private final Matrix glowMatrix = new Matrix();
        private android.graphics.PorterDuffColorFilter patternFilter;

        public int position = -1;

        // ButterGram live bubbles: offset from the slot, velocity and the slot's last position
        public float ox, oy, vx, vy, ax, ay, st, stv, sc = 1f;
        public final float phase = (float) (Math.random() * Math.PI * 2);

        public Gift(TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            document = gift.getDocument();
            documentId = document == null ? 0 : document.id;
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            color = backdrop.center_color | 0xFF000000;
            edgeColor = backdrop.edge_color | 0xFF000000;
            patternColor = backdrop.pattern_color | 0xFF000000;
            final TL_stars.starGiftAttributePattern pat = findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class);
            patternDocument = pat != null ? pat.document : null;
            slug = gift.slug;
            initParticles();
        }

        public Gift(TLRPC.TL_emojiStatusCollectible status) {
            id = status.collectible_id;
            document = null;
            documentId = status.document_id;
            color = status.center_color | 0xFF000000;
            edgeColor = status.edge_color | 0xFF000000;
            patternColor = status.pattern_color | 0xFF000000;
            patternDocId = status.pattern_document_id;
            slug = status.slug;
            initParticles();
        }

        private void initParticles() {
            particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 6);
            final float gsz = dp(36);
            particles.bounds.set(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f);
        }

        public boolean equals(Gift b) {
            return b != null && b.id == id;
        }

        public RadialGradient gradient;
        public final Matrix gradientMatrix = new Matrix();
        public Paint gradientPaint;
        public AnimatedEmojiDrawable emojiDrawable;
        public AnimatedFloat animatedFloat;

        public final RectF bounds = new RectF();
        public final ButtonBounce bounce = new ButtonBounce(ProfileGiftsView.this);

        public void copy(Gift b) {
            gradient = b.gradient;
            emojiDrawable = b.emojiDrawable;
            patternDrawable = b.patternDrawable;
            gradientPaint = b.gradientPaint;
            animatedFloat = b.animatedFloat;
            particles = b.particles;
            position = b.position;
            ox = b.ox;
            oy = b.oy;
            vx = b.vx;
            vy = b.vy;
            st = b.st;
            stv = b.stv;
            sc = b.sc;
        }

        // glass bubble: see-through body that is tinted towards the edge, the gift pattern inside, rim and highlights
        private void drawBubble(Canvas canvas, float r, float alpha) {
            if (bubbleShader == null) {
                bubbleShader = new RadialGradient(0, 0, r,
                        new int[]{Theme.multAlpha(color, 0.10f), Theme.multAlpha(color, 0.20f), Theme.multAlpha(edgeColor, 0.50f)},
                        new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
                bubbleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
                bubbleFill.setShader(bubbleShader);
            }
            bubbleFill.setAlpha((int) (0xFF * alpha));
            canvas.drawCircle(0, 0, r, bubbleFill);

            if (patternDrawable != null) {
                if (patternFilter == null) {
                    patternFilter = new android.graphics.PorterDuffColorFilter(patternColor, android.graphics.PorterDuff.Mode.SRC_IN);
                }
                patternDrawable.setColorFilter(patternFilter);
                patternDrawable.setAlpha((int) (0xFF * 0.42f * alpha));
                final int ps = dp(7);
                for (int k = 0; k < 6; k++) {
                    final double a = Math.toRadians(k * 60 + 18);
                    final int px = (int) (Math.cos(a) * r * 0.64f), py = (int) (Math.sin(a) * r * 0.64f);
                    patternDrawable.setBounds(px - ps / 2, py - ps / 2, px + ps / 2, py + ps / 2);
                    patternDrawable.draw(canvas);
                }
            }

            bubblePaint.setStyle(Paint.Style.STROKE);
            bubblePaint.setStrokeWidth(dp(1f));
            bubblePaint.setColor(Theme.multAlpha(0xFFFFFFFF, 0.38f * alpha));
            canvas.drawCircle(0, 0, r - dp(0.5f), bubblePaint);
            bubblePaint.setStrokeWidth(dp(2f));
            bubblePaint.setColor(Theme.multAlpha(0xFF000000, 0.05f * alpha));
            canvas.save();
            canvas.clipRect(-r, r * 0.1f, r, r);
            canvas.drawCircle(0, 0, r - dp(1.8f), bubblePaint);
            canvas.restore();

            bubblePaint.setStyle(Paint.Style.FILL);
            bubblePaint.setColor(Theme.multAlpha(0xFFFFFFFF, 0.3f * alpha));
            bubbleRect.set(-r * 0.66f, -r * 0.74f, -r * 0.1f, -r * 0.46f);
            canvas.save();
            canvas.rotate(-30, -r * 0.38f, -r * 0.6f);
            canvas.drawOval(bubbleRect, bubblePaint);
            canvas.restore();
            bubblePaint.setColor(Theme.multAlpha(0xFFFFFFFF, 0.28f * alpha));
            canvas.drawCircle(r * 0.5f, r * 0.5f, dp(1.8f), bubblePaint);
        }

        // soft glow of the gift's colour on the rim of the avatar, on the side the bubble is
        private void drawGlow(Canvas canvas, float px, float py, float cx, float cy, float rad, float strength) {
            if (glowShader == null) {
                glowShader = new RadialGradient(0, 0, dp(52),
                        new int[]{Theme.multAlpha(color, 0.95f), Theme.multAlpha(color, 0.38f), 0},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                glowPaint.setShader(glowShader);
            }
            glowMatrix.setTranslate(px, py);
            glowShader.setLocalMatrix(glowMatrix);
            glowPaint.setAlpha((int) (0xFF * Math.min(1f, strength)));
            glowPath.reset();
            glowPath.addCircle(cx, cy, rad + dp(6), Path.Direction.CW);
            glowPath.addCircle(cx, cy, rad - dp(4), Path.Direction.CCW);
            canvas.save();
            canvas.clipPath(glowPath);
            canvas.drawCircle(px, py, dp(52), glowPaint);
            canvas.restore();
        }

        public void draw(
                Canvas canvas,
                float cx, float cy,
                float ascale, float rotate,
                float alpha,
                float gradientAlpha
        ) {
            if (alpha <= 0.0f) return;
            final float gsz = org.telegram.utils.settings.SharedSettings.buttergramLiveBubbles.get() ? dp(40) : dp(45);
            bounds.set(cx - gsz / 2, cy - gsz / 2, cx + gsz / 2, cy + gsz / 2);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotate);
            final float scale = ascale * bounce.getScale(0.1f);
            canvas.scale(scale, scale);
            particles.process();
            particles.draw(canvas, color, alpha);
            if (gradientPaint != null && !org.telegram.utils.settings.SharedSettings.buttergramLiveBubbles.get()) {
                gradientPaint.setAlpha((int) (0xFF * alpha * gradientAlpha));
                canvas.drawRect(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f, gradientPaint);
            }
            if (org.telegram.utils.settings.SharedSettings.buttergramLiveBubbles.get()) {
                drawBubble(canvas, gsz * 0.5f, alpha);
            }
            if (emojiDrawable != null) {
                final int sz = org.telegram.utils.settings.SharedSettings.buttergramLiveBubbles.get() ? dp(25) : dp(28);
                emojiDrawable.setBounds(-sz / 2, -sz / 2, sz / 2, sz / 2);
                emojiDrawable.setAlpha((int) (0xFF * alpha));
                emojiDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubbleRect = new RectF();
    private final Path glowPath = new Path();

    private StarsController.GiftsList list;

    public final ArrayList<Gift> oldGifts = new ArrayList<>();
    public final ArrayList<Gift> gifts = new ArrayList<>();
    public final HashSet<Long> giftIds = new HashSet<>();
    public int maxCount;

    public void update() {
        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) {
            return;
        }

        maxCount = MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit;
        oldGifts.clear();
        oldGifts.addAll(gifts);
        gifts.clear();
        giftIds.clear();

        final TLRPC.EmojiStatus emojiStatus;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            emojiStatus = user == null ? null : user.emoji_status;
        } else {
            final TLRPC.User chat = MessagesController.getInstance(currentAccount).getUser(-dialogId);
            emojiStatus = chat == null ? null : chat.emoji_status;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            giftIds.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }
        list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved && savedGift.pinned_to_top && savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final Gift gift = new Gift((TL_stars.TL_starGiftUnique) savedGift.gift);
                    if (!giftIds.contains(gift.id)) {
                        gifts.add(gift);
                        giftIds.add(gift.id);
                    }
                }
            }
        }

        boolean changed = false;
        if (gifts.size() != oldGifts.size()) {
            changed = true;
        } else for (int i = 0; i < gifts.size(); i++) {
            if (!gifts.get(i).equals(oldGifts.get(i))) {
                changed = true;
                break;
            }
        }

        for (int i = 0; i < gifts.size(); i++) {
            final Gift g = gifts.get(i);
            Gift oldGift = null;
            for (int j = 0; j < oldGifts.size(); ++j) {
                if (oldGifts.get(j).id == g.id) {
                    oldGift = oldGifts.get(j);
                    break;
                }
            }

            if (oldGift != null) {
                g.copy(oldGift);
            } else {
                g.gradient = new RadialGradient(0, 0, dp(22.5f), new int[]{g.color, Theme.multAlpha(g.color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                g.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.gradientPaint.setShader(g.gradient);
                if (g.document != null) {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.document);
                } else {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.documentId);
                }
                if (g.patternDocument != null) {
                    g.patternDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.patternDocument);
                } else if (g.patternDocId != 0) {
                    g.patternDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.patternDocId);
                }
                g.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                g.animatedFloat.force(0.0f);
                if (isAttachedToWindow()) {
                    if (g.patternDrawable != null) g.patternDrawable.addView(this);
                    g.emojiDrawable.addView(this);
                }
            }
        }

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            positions.add(i);
        }

        for (int i = 0; i < oldGifts.size(); i++) {
            final Gift g = oldGifts.get(i);
            Gift newGift = null;
            for (int j = 0; j < gifts.size(); ++j) {
                if (gifts.get(j).id == g.id) {
                    newGift = gifts.get(j);
                    break;
                }
            }
            if (newGift == null) {
                g.emojiDrawable.removeView(this);
                g.emojiDrawable = null;
                if (g.patternDrawable != null) {
                    g.patternDrawable.removeView(this);
                    g.patternDrawable = null;
                }
                g.gradient = null;
            } else {
                positions.remove((Object) g.position);
            }
        }

        if (!positions.isEmpty()) {
            BagRandomizer<Integer> bagRandomizer = new BagRandomizer<>(positions);
            for (int i = 0; i < gifts.size(); ++i) {
                final Gift g = gifts.get(i);
                if (g.position == -1) {
                    g.position = bagRandomizer.next();
                }
            }
        }

        if (changed)
            invalidate();
    }

    private final TimeInterpolator giftCollapseXInterpolator = new DecelerateInterpolator();
    private final TimeInterpolator giftCollapseYInterpolator = new LinearInterpolator();

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (gifts.isEmpty() || expandProgress >= 1.0f || collapseProgress <= 0f) return;

        final float realX = avatarContainer.getX();
        final float realY = avatarContainer.getY();
        final float realW = (avatarContainer.getWidth()) * avatarContainer.getScaleX();
        final float realH = (avatarContainer.getHeight()) * avatarContainer.getScaleY();

        final float sz = dpf2(96);
        final float ax = Math.min(realX, (getWidth() - sz) / 2f);
        final float ay = Math.max(realY, (maxExpandY - sz) / 2f);
        final float aw = Math.max(realW, sz);
        final float ah = Math.max(realH, sz);


        final boolean live = org.telegram.utils.settings.SharedSettings.buttergramLiveBubbles.get();
        final long nowMs = android.os.SystemClock.uptimeMillis();
        final float dt = lastFrameMs == 0 ? 0.016f : Math.min(0.032f, (nowMs - lastFrameMs) / 1000f);
        lastFrameMs = nowMs;
        boolean animating = false;

        canvas.save();
        canvas.clipRect(0, 0, getWidth(), expandY);

        final float acx = ax + aw / 2.0f;
        final float acy = ay + ah / 2.0f;

        final float realCX = realX + realW / 2.0f;
        final float realCY = realY + realH / 2.0f;

        float expandScale = expandY / maxExpandY;
        float closedAlpha = Utilities.clamp01((expandY - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())) / dp(50));

        if (live) buttergramCollide();
        for (int i = 0; i < gifts.size(); ++i) {
            final Gift gift = gifts.get(i);
            final float enter = gift.animatedFloat.set(1.0f);
            float scale = lerp(0.5f, 1.0f, enter);
            final float alpha = enter * (1.0f - expandProgress) * (1.0f - actionBarProgress) * (closedAlpha);

            float gx, gy;
            float delayValue;
            final int bgPos = gift.position;

            switch (gift.position) {
                case 0: // left
                    gx = acx / 2f - (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 1: // top left
                    gx = acx * 2f / 3f - (dp(6) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0;
                    break;
                case 2: // bottom left
                    gx = acx * 2f / 3f - (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0.9f;
                    break;
                case 3: // right
                    gx = acx * 1.5f + (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 4: // top right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0.9f;
                    break;
                default: // bottom right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0;
                    break;
            }

            if (live && enter >= 1f) {
                // the bubbles slowly orbit the avatar
                final double th = (nowMs % 3600000L) / 1000.0 * 0.32;
                final float rx = gx - acx, ry = gy - acy;
                final float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
                gx = acx + rx * cs - ry * sn;
                gy = acy + rx * sn + ry * cs;
            }

            final float collapseProgressWithEnter;
            if (isOpening || enter >= 1f) {
                collapseProgressWithEnter = collapseProgress;
            } else {
                collapseProgressWithEnter = Math.min(enter, collapseProgress);
            }

            final float delayFraction = 0.2f;
            final float maxDelayFraction = 1.6f * delayFraction;
            final float intervalFraction = 1f - maxDelayFraction;

            float delay = delayValue * delayFraction;
            float collapse = collapseProgressWithEnter >= 1f - delay ? 1f
                    : Utilities.clamp01((collapseProgressWithEnter - maxDelayFraction + delay) / intervalFraction);
            if (collapse < 1f) {
                gx = AndroidUtilities.lerp(realCX, gx, giftCollapseXInterpolator.getInterpolation(collapse));
                gy = AndroidUtilities.lerp(realCY, gy, giftCollapseYInterpolator.getInterpolation(collapse));
                scale = AndroidUtilities.lerp(scale / 2f, scale, collapse);
            }

            float stretch = 0f, stretchAngle = 0f;
            if (live && collapse >= 1f && enter >= 1f) {
                gift.ax = gx;
                gift.ay = gy;
                if (gift != dragGift) {
                    final float t = nowMs / 1000f;
                    final float tx = (float) Math.sin(t * 0.9f + gift.phase) * dp(4);
                    final float ty = (float) Math.cos(t * 0.7f + gift.phase * 1.3f) * dp(4);
                    // underdamped spring towards a slowly wandering target: floaty and bouncy
                    final float off = (float) Math.hypot(gift.ox, gift.oy);
                    final float kk = off > dp(24) ? 16f : 50f, cc = off > dp(24) ? 3.4f : 6f;
                    gift.vx += ((tx - gift.ox) * kk - gift.vx * cc) * dt;
                    gift.vy += ((ty - gift.oy) * kk - gift.vy * cc) * dt;
                    gift.ox += gift.vx * dt;
                    gift.oy += gift.vy * dt;
                    final float minX = dp(22), maxX = getWidth() - dp(22);
                    final float minY = dp(22), maxY = Math.max(minY, getHeight() - dp(22));
                    final float py0 = gy + gift.oy;
                    if (py0 < minY) {
                        gift.oy = minY - gy;
                        gift.vy = Math.abs(gift.vy) * 0.6f;
                    } else if (py0 > maxY) {
                        gift.oy = maxY - gy;
                        gift.vy = -Math.abs(gift.vy) * 0.6f;
                    }
                    final float px = gx + gift.ox;
                    if (px < minX) {
                        gift.ox = minX - gx;
                        gift.vx = Math.abs(gift.vx) * 0.6f;
                    } else if (px > maxX) {
                        gift.ox = maxX - gx;
                        gift.vx = -Math.abs(gift.vx) * 0.6f;
                    }
                }
                gx += gift.ox;
                gy += gift.oy;
                animating = true;

                // jelly: the bubble stretches along its motion and while it is pulled
                final float sp = (float) Math.hypot(gift.vx, gift.vy);
                final float offLen = (float) Math.hypot(gift.ox, gift.oy);
                float target = Math.min(0.45f, sp / dp(1800f));
                if (gift == dragGift) {
                    target = Math.max(target, Math.min(0.6f, offLen / dp(220f)));
                }
                gift.stv += ((target - gift.st) * 130f - gift.stv * 7f) * dt;
                gift.st += gift.stv * dt;
                gift.st = Math.max(-0.2f, Math.min(0.65f, gift.st));
                stretch = gift.st;
                gift.sc += ((gift == dragGift ? 1.5f : 1f) - gift.sc) * Math.min(1f, dt * 10f);
                final boolean byVelocity = sp > dp(40f);
                stretchAngle = (float) Math.toDegrees(Math.atan2(byVelocity ? gift.vy : gift.oy, byVelocity ? gift.vx : gift.ox));

                // colour of the background spills onto the avatar rim, stronger the closer the bubble is
                final float rimR = Math.min(realW, realH) / 2f;
                final float ddx = gx - realCX, ddy = gy - realCY;
                final float dist = (float) Math.hypot(ddx, ddy);
                if (dist > 1f) {
                    final float near = Utilities.clamp01(1f - (dist - rimR) / dp(150f));
                    if (near > 0.04f) {
                        gift.drawGlow(canvas, realCX + ddx / dist * rimR, realCY + ddy / dist * rimR, realCX, realCY, rimR, near * alpha);
                    }
                }
            }

            if (Math.abs(stretch) > 0.01f) {
                canvas.save();
                canvas.rotate(stretchAngle, gx, gy);
                canvas.scale(1f + stretch, (float) (1.0 / Math.sqrt(1f + stretch)), gx, gy);
                canvas.rotate(-stretchAngle, gx, gy);
                gift.draw(canvas, gx, gy, scale * gift.sc, 0, alpha, 1f);
                canvas.restore();
            } else {
                gift.draw(canvas, gx, gy, scale * gift.sc, 0, alpha, 1f);
            }
        }

        canvas.restore();
        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    private void buttergramCollide() {
        final float min = dp(40);
        for (int i = 0; i < gifts.size(); i++) {
            final Gift a = gifts.get(i);
            for (int j = i + 1; j < gifts.size(); j++) {
                final Gift b = gifts.get(j);
                float dx = (b.ax + b.ox) - (a.ax + a.ox), dy = (b.ay + b.oy) - (a.ay + a.oy);
                float d = (float) Math.hypot(dx, dy);
                if (d >= min) continue;
                if (d < 0.01f) { dx = 1; dy = 0; d = 1; }
                final float nx = dx / d, ny = dy / d, ov = min - d;
                final boolean ad = a == dragGift, bd = b == dragGift;
                final float wa = ad ? 0 : (bd ? 1 : 0.5f), wb = bd ? 0 : (ad ? 1 : 0.5f);
                a.ox -= nx * ov * wa; a.oy -= ny * ov * wa;
                b.ox += nx * ov * wb; b.oy += ny * ov * wb;
                final float rv = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
                if (rv < 0) {
                    final float imp = -rv * 0.9f;
                    if (!ad) { a.vx -= nx * imp * wa * 2; a.vy -= ny * imp * wa * 2; }
                    if (!bd) { b.vx += nx * imp * wb * 2; b.vy += ny * imp * wb * 2; }
                }
            }
        }
    }

    private long lastFrameMs;
    private Gift dragGift;
    private boolean dragging;
    private float downX, downY;
    private long lastMoveTime;

    /** ButterGram: true when a visible bubble is under the point (lets the profile route the touch here even over other views). */
    public boolean isGiftAt(float x, float y) {
        if (!active || gifts.isEmpty() || getVisibility() != VISIBLE || getAlpha() < 0.5f
                || expandProgress > 0.01f || collapseProgress < 1f || actionBarProgress > 0.01f) {
            return false;
        }
        return getGiftUnder(x, y) != null;
    }

    public Gift getGiftUnder(float x, float y) {
        for (int i = 0; i < gifts.size(); ++i) {
            if (gifts.get(i).bounds.contains(x, y))
                return gifts.get(i);
        }
        return null;
    }

    private Gift pressedGift;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!active) return false;
        final boolean live = org.telegram.utils.settings.SharedSettings.buttergramLiveBubbles.get();
        final int action = event.getAction();
        final Gift hit = getGiftUnder(event.getX(), event.getY());
        boolean handled;
        if (action == MotionEvent.ACTION_DOWN) {
            pressedGift = hit;
            dragging = false;
            downX = event.getX();
            downY = event.getY();
            lastMoveTime = event.getEventTime();
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(true);
                if (live && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            return pressedGift != null;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (live && pressedGift != null) {
                final float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (!dragging && Math.hypot(event.getX() - downX, event.getY() - downY) > slop) {
                    dragging = true;
                    dragGift = pressedGift;
                    pressedGift.bounce.setPressed(false);
                }
                if (dragging) {
                    final Gift g = dragGift;
                    final float fx = Math.max(dp(22), Math.min(getWidth() - dp(22), event.getX()));
                    final float fy = Math.max(dp(22), Math.min(Math.max(dp(22), getHeight() - dp(22)), event.getY()));
                    float nx = fx - g.ax, ny = fy - g.ay;
                    final float d = (event.getEventTime() - lastMoveTime) / 1000f;
                    if (d > 0.001f) {
                        g.vx = 0.5f * g.vx + 0.5f * (nx - g.ox) / d;
                        g.vy = 0.5f * g.vy + 0.5f * (ny - g.oy) / d;
                    }
                    lastMoveTime = event.getEventTime();
                    g.ox = nx;
                    g.oy = ny;
                    invalidate();
                }
                return true;
            }
            if (pressedGift != hit && pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
            return pressedGift != null;
        } else if (action == MotionEvent.ACTION_UP) {
            handled = pressedGift != null;
            if (dragging && dragGift != null) {
                final float maxV = dp(2200);
                dragGift.vx = Math.max(-maxV, Math.min(maxV, dragGift.vx));
                dragGift.vy = Math.max(-maxV, Math.min(maxV, dragGift.vy));
                dragGift = null;
                dragging = false;
                pressedGift = null;
                invalidate();
                return true;
            }
            if (pressedGift != null) {
                onGiftClick(pressedGift);
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
            return handled;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
            dragGift = null;
            dragging = false;
        }
        return pressedGift != null;
    }

    public void onGiftClick(Gift gift) {
        Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug);
    }
}

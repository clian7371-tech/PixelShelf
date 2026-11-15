package com.pixelshelf.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 游戏卡片：自绘圆角封面 + 名称 + 平台标记，带聚焦缩放、辉光、按压反馈 */
public class GameCard extends FrameLayout {

    public final Game game;

    private final TextView title;
    private final TextView meta;
    private final RectF coverRect = new RectF();
    private final RectF cardRect = new RectF();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();

    /** 程序化封面（兜底） */
    private Bitmap fallback;
    /** 网络封面（可能为 null） */
    private Bitmap real;
    /** 网络封面的淡入进度 0..1 */
    private float coverFade = 0f;

    /** 「改版」标记：封面来自原版游戏 */
    private boolean hackBadge = false;
    private final Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final String HACK_LABEL = "改版";

    private float focusP = 0f;
    private float pressP = 0f;
    private ValueAnimator anim;

    private final int accent;
    private final float radius;

    public GameCard(Context c, Game g, int cardW, int cardH) {
        super(c);
        this.game = g;
        this.accent = CoverArt.accentFor(g.platform);
        this.radius = dp(14);

        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);

        int inner = cardW - dp(12);
        int coverH = Math.round(inner * 0.72f);
        int coverPx = Math.max(180, inner);

        fallback = CoverArt.of(g.path, g.platform, coverPx);
        real = CoverFetcher.cached(c, g);
        coverFade = real != null ? 1f : 0f;

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(box, new FrameLayout.LayoutParams(cardW, LayoutParams.MATCH_PARENT));

        // 占位：封面区域由 onDraw 绘制
        View spacer = new View(c);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(inner, coverH);
        slp.topMargin = dp(6);
        spacer.setLayoutParams(slp);
        box.addView(spacer);

        title = new TextView(c);
        title.setText(g.name);
        title.setTextColor(0xFFEAF2FF);
        title.setTextSize(12.5f);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setLineSpacing(0f, 0.95f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(6);
        tlp.rightMargin = dp(6);
        tlp.topMargin = dp(7);
        title.setLayoutParams(tlp);
        box.addView(title);

        meta = new TextView(c);
        String size = g.sizeText();
        meta.setText(size.isEmpty()
                ? CoverArt.label(g.platform)
                : CoverArt.label(g.platform) + " · " + size);
        meta.setTextColor(0xFF6C7C93);
        meta.setTextSize(10f);
        meta.setGravity(Gravity.CENTER);
        box.addView(meta);

        setOnFocusChangeListener((v, has) -> animateTo(has ? 1f : 0f));

        // 角标画笔（在构造时一次性配置，避免 draw 里惰性初始化出错）
        badgeText.setColor(0xFFFFE0B8);
        badgeText.setTextSize(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 9f,
                getResources().getDisplayMetrics()));
        badgeText.setAntiAlias(true);
        badgeText.setFakeBoldText(true);
        badgeText.setTextAlign(Paint.Align.CENTER);
        badgeBg.setColor(0xE6301602);
        badgeBg.setStyle(Paint.Style.FILL);
        badgeBg.setAntiAlias(true);
    }

    /** 网络封面到达：淡入替换 */
    public void applyCover(Bitmap bmp) {
        if (bmp == null) return;
        real = bmp;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(320);
        a.addUpdateListener(v -> {
            coverFade = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    /** 设置是否显示「改版」角标 */
    public void setHackBadge(boolean on) {
        if (hackBadge == on) return;
        hackBadge = on;
        invalidate();
    }

    public boolean hasRealCover() {
        return real != null;
    }

    private void animateTo(float to) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(focusP, to);
        anim.setDuration(to > 0 ? 190 : 220);
        anim.setInterpolator(new DecelerateInterpolator(2.2f));
        anim.addUpdateListener(a -> {
            focusP = (float) a.getAnimatedValue();
            applyFocus();
        });
        anim.start();
    }

    private void applyFocus() {
        float s = 1f + 0.055f * focusP - 0.02f * pressP;
        setScaleX(s);
        setScaleY(s);
        setTranslationZ(dp(16) * focusP);
        title.setTextColor(blend(0xFFEAF2FF, 0xFFFFFFFF, focusP));
        meta.setTextColor(blend(0xFF6C7C93, 0xFF9FE8FF, focusP));
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animatePress(1f);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animatePress(0f);
                break;
        }
        return super.onTouchEvent(e);
    }

    private void animatePress(float to) {
        ValueAnimator a = ValueAnimator.ofFloat(pressP, to);
        a.setDuration(110);
        a.addUpdateListener(v -> {
            pressP = (float) v.getAnimatedValue();
            applyFocus();
            invalidate();
        });
        a.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cardRect.set(0, 0, w, h);
        int inner = w - dp(12);
        float left = (w - inner) / 2f;
        coverRect.set(left, dp(6), left + inner, dp(6) + Math.round(inner * 0.72f));
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        int w = getWidth(), h = getHeight();

        // 卡片底
        p.setStyle(Paint.Style.FILL);
        p.setColor(blend(0xFF111826, 0xFF16202F, focusP));
        c.drawRoundRect(cardRect, radius, radius, p);

        if (!coverRect.isEmpty()) {
            clip.reset();
            clip.addRoundRect(coverRect, dp(9), dp(9), Path.Direction.CW);
            int save = c.save();
            c.clipPath(clip);

            // 先画程序化封面兜底
            if (fallback != null) {
                c.drawBitmap(fallback, null, coverRect, p);
            }
            // 再按淡入进度叠加网络封面
            if (real != null && coverFade > 0f) {
                p.setAlpha((int) (255 * coverFade));
                c.drawBitmap(real, null, coverRect, p);
                p.setAlpha(255);
            }

            // 底部压暗，衔接卡片底色
            p.setShader(new LinearGradient(0, coverRect.top + coverRect.height() * 0.55f,
                    0, coverRect.bottom, 0x00000000, 0xCC0B111C, Shader.TileMode.CLAMP));
            c.drawRect(coverRect, p);
            p.setShader(null);

            // 聚焦时封面泛光
            if (focusP > 0.01f) {
                p.setColor(withAlpha(accent, (int) (26 * focusP)));
                c.drawRect(coverRect, p);
            }
            c.restoreToCount(save);

            // 封面描边
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1f));
            p.setColor(withAlpha(0xFFFFFFFF, 0x1F));
            c.drawRoundRect(coverRect, dp(9), dp(9), p);

            // 「改版」角标（封面来自原版游戏时提示）
            if (hackBadge) drawHackBadge(c);
        }

        super.dispatchDraw(c);

        // 卡片描边（聚焦时用平台色）
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1.5f));
        p.setColor(withAlpha(accent, (int) (40 + 200 * focusP)));
        c.drawRoundRect(new RectF(dp(0.8f), dp(0.8f), w - dp(0.8f), h - dp(0.8f)),
                radius, radius, p);

        // 聚焦底部光条
        if (focusP > 0.01f) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, 0,
                    withAlpha(accent, 0), withAlpha(accent, (int) (255 * focusP)),
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(dp(12), h - dp(3.4f), w - dp(12), h - dp(1.5f)),
                    dp(2), dp(2), p);
            p.setShader(null);
        }
    }

    /** 右上角「改版」小标 */
    private void drawHackBadge(Canvas c) {
        float tw = badgeText.measureText(HACK_LABEL);
        float padH = dp(5f);
        float padV = dp(2.5f);
        Paint.FontMetrics fm = badgeText.getFontMetrics();
        float th = fm.descent - fm.ascent;
        float bw = tw + padH * 2;
        float bh = th + padV * 2;

        float right = coverRect.right - dp(5);
        float top = coverRect.top + dp(5);
        RectF box = new RectF(right - bw, top, right, top + bh);
        c.drawRoundRect(box, dp(4), dp(4), badgeBg);

        // 细边框
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(0.8f));
        p.setColor(0x99FB923C);
        c.drawRoundRect(box, dp(4), dp(4), p);

        c.drawText(HACK_LABEL, box.centerX(),
                box.centerY() - (fm.ascent + fm.descent) / 2f, badgeText);
    }

    private static int blend(int from, int to, float t) {
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int rr = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int gg = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int bb = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, rr, gg, bb);
    }

    private static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

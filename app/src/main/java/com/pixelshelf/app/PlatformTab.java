package com.pixelshelf.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** 顶部平台标签：选中时发光下划线 + 平滑滑动 */
public class PlatformTab extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "";
    private int accent = 0xFF22D3EE;
    private float sel = 0f;
    private ValueAnimator anim;
    private float textW;

    public PlatformTab(Context c) {
        super(c);
    }

    public PlatformTab(Context c, AttributeSet a) {
        super(c, a);
    }

    public void configure(String text, int accent) {
        this.text = text;
        this.accent = accent;
        p.setTextSize(sp(9.5f));
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        textW = p.measureText(text);
        requestLayout();
        invalidate();
    }

    public void setSelectedSmooth(boolean on, boolean animate) {
        float to = on ? 1f : 0f;
        if (!animate) {
            sel = to;
            invalidate();
            return;
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(sel, to);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            sel = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    public float textWidth() {
        return textW;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = (int) Math.ceil(textW + dp(34));
        int h = Math.round(dp(38));
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float cy = h * 0.46f;

        // 文字（未选中灰，选中白）
        p.setStyle(Paint.Style.FILL);
        p.setColor(blend(0xFF8A9AB0, 0xFFEAF2FF, sel));
        c.drawText(text, w / 2f, cy + p.getTextSize() * 0.36f, p);

        // 未选中：淡胶囊底
        if (sel < 0.99f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(withAlpha(0xFFFFFFFF, (int) (14 * (1f - sel))));
            float ph = dp(15);
            c.drawRoundRect(new RectF(dp(3), cy - ph, w - dp(3), cy + ph), ph, ph, p);
        }

        // 选中：发光下划线
        if (sel > 0.01f) {
            float lw = (textW + dp(18)) * sel;
            float x0 = w / 2f - lw / 2f;
            float x1 = w / 2f + lw / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x0, 0, x1, 0,
                    withAlpha(accent, 0), withAlpha(accent, (int) (255 * sel)),
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(x0, h - dp(5.5f), x1, h - dp(3f)), dp(2), dp(2), p);
            p.setShader(null);
        }
    }

    private static int blend(int from, int to, float t) {
        int a = (int) (android.graphics.Color.alpha(from) + (android.graphics.Color.alpha(to) - android.graphics.Color.alpha(from)) * t);
        int rr = (int) (android.graphics.Color.red(from) + (android.graphics.Color.red(to) - android.graphics.Color.red(from)) * t);
        int gg = (int) (android.graphics.Color.green(from) + (android.graphics.Color.green(to) - android.graphics.Color.green(from)) * t);
        int bb = (int) (android.graphics.Color.blue(from) + (android.graphics.Color.blue(to) - android.graphics.Color.blue(from)) * t);
        return android.graphics.Color.argb(a, rr, gg, bb);
    }

    private static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
}

package com.pixelshelf.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.Random;

/**
 * 赛博风格动态背景：透视网格 + 漂浮粒子 + 顶部辉光。
 * 纯 Canvas 绘制，无额外依赖。
 */
public class TechBackground extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();

    private float[] px = new float[0];
    private float[] py = new float[0];
    private float[] pv = new float[0];
    private float[] pr = new float[0];

    private static final int PARTICLES = 46;

    private long startNs;
    private boolean running;
    private float t;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long ns) {
            if (startNs == 0) startNs = ns;
            t = (ns - startNs) / 1_000_000_000f;
            invalidate();
            if (running) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public TechBackground(Context c) {
        super(c);
        init();
    }

    public TechBackground(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        gridPaint.setStrokeWidth(dp(0.7f));
        particlePaint.setStyle(Paint.Style.FILL);

        Random r = new Random(20261115L);
        px = new float[PARTICLES];
        py = new float[PARTICLES];
        pv = new float[PARTICLES];
        pr = new float[PARTICLES];
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = r.nextFloat();
            py[i] = r.nextFloat();
            pv[i] = 0.006f + r.nextFloat() * 0.018f;
            pr[i] = 0.6f + r.nextFloat() * 1.8f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frame);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // 基础竖向渐变
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF070B14, 0xFF060911, 0xFF03050A},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bgPaint);

        // 顶部青色辉光
        glowPaint.setShader(new RadialGradient(w * 0.5f, -h * 0.12f, h * 0.62f,
                new int[]{0x3322D3EE, 0x1422D3EE, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, glowPaint);

        drawGrid(c, w, h);
        drawParticles(c, w, h);

        // 底部暗角
        glowPaint.setShader(new RadialGradient(w * 0.5f, h * 1.08f, h * 0.55f,
                new int[]{0x00000000, 0x66000000},
                new float[]{0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, glowPaint);
    }

    /** 透视网格：地平线以下的横线向上流动，竖线向消失点汇聚 */
    private void drawGrid(Canvas c, int w, int h) {
        float horizon = h * 0.42f;
        float cx = w * 0.5f;

        // 横线：用透视缩放模拟向前推进
        int rows = 14;
        for (int i = 0; i < rows; i++) {
            float k = (i + (t * 0.55f) % 1f) / rows;
            float depth = k * k;
            float y = horizon + depth * (h - horizon) * 1.06f;
            if (y < horizon || y > h + 2) continue;
            int alpha = (int) (10 + 46 * (1f - k));
            gridPaint.setColor((alpha << 24) | 0x22D3EE);
            c.drawLine(0, y, w, y, gridPaint);
        }

        // 竖线：从消失点发散
        int cols = 18;
        for (int i = 0; i <= cols; i++) {
            float fx = (i / (float) cols - 0.5f) * 2f;
            float xb = cx + fx * w * 1.5f;
            int alpha = (int) (10 + 30 * (1f - Math.abs(fx)));
            gridPaint.setColor((alpha << 24) | 0x22D3EE);
            c.drawLine(cx, horizon, xb, h + 4, gridPaint);
        }

        // 地平线光带
        gridPaint.setStrokeWidth(dp(1.2f));
        gridPaint.setColor(0x2E22D3EE);
        c.drawLine(0, horizon, w, horizon, gridPaint);
        gridPaint.setStrokeWidth(dp(0.7f));
    }

    private void drawParticles(Canvas c, int w, int h) {
        for (int i = 0; i < PARTICLES; i++) {
            py[i] -= pv[i] * 0.05f;
            if (py[i] < -0.05f) {
                py[i] = 1.05f;
            }
            float x = px[i] * w + (float) Math.sin(t * 0.35f + i) * dp(7);
            float y = py[i] * h;
            float fade = 1f - Math.abs(py[i] - 0.5f) * 1.1f;
            if (fade <= 0) continue;
            int a = (int) (110 * fade);
            particlePaint.setColor((a << 24) | 0x7FE7FF);
            c.drawCircle(x, y, dp(pr[i] * 0.5f), particlePaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}

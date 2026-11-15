package com.pixelshelf.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.Map;

/**
 * 程序化封面：用游戏名哈希生成配色与图案，无需联网。
 * 同一游戏永远得到同一张封面。
 */
public final class CoverArt {

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private static final int[][] PALETTES = {
            {0xFF2B5876, 0xFF7C3AED},
            {0xFF0F4C5C, 0xFF34D399},
            {0xFF614385, 0xFF9F7AEA},
            {0xFF1E3A8A, 0xFF06B6D4},
            {0xFF1D4ED8, 0xFF22D3EE},
            {0xFF3A1C71, 0xFFD76D77},
            {0xFF00467F, 0xFF22D3EE},
            {0xFF6D28D9, 0xFFDB2777},
            {0xFF0E7490, 0xFF67E8F9},
            {0xFFB91C1C, 0xFFF59E0B},
            {0xFF134E5E, 0xFF34D399},
            {0xFF2C003E, 0xFF8B5CF6},
            {0xFF0B3D91, 0xFF3B82F6},
            {0xFF7C2D12, 0xFFFB923C},
            {0xFF065F46, 0xFF10B981},
    };

    /**
     * @param key   游戏路径/名称，用于稳定取色
     * @param plat  gba / nds / psp
     * @param px   目标像素边长
     */
    public static Bitmap of(String key, String plat, int px) {
        String ck = key + "|" + plat + "|" + px;
        Bitmap hit = CACHE.get(ck);
        if (hit != null) return hit;

        int h = 5381;
        for (int i = 0; i < key.length(); i++) h = (h * 33) ^ key.charAt(i);
        int idx = Math.abs(h) % PALETTES.length;
        int[] pal = PALETTES[idx];

        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 对角渐变底
        p.setShader(new LinearGradient(0, 0, px, px, pal[0], pal[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, px, px, p);
        p.setShader(null);

        int seed = Math.abs(h);
        int variant = seed % 4;

        // 装饰图案
        p.setColor(0x22FFFFFF);
        if (variant == 0) {
            // 同心圆
            for (int i = 1; i <= 4; i++) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(px * 0.012f);
                c.drawCircle(px * 0.72f, px * 0.28f, px * 0.12f * i, p);
            }
        } else if (variant == 1) {
            // 斜条纹
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(px * 0.05f);
            for (int i = -px; i < px * 2; i += (int) (px * 0.16f)) {
                c.drawLine(i, 0, i + px * 0.5f, px, p);
            }
        } else if (variant == 2) {
            // 三角网格
            Path path = new Path();
            path.moveTo(0, px);
            path.lineTo(px * 0.5f, px * 0.25f);
            path.lineTo(px, px);
            path.close();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(px * 0.014f);
            c.drawPath(path, p);
        } else {
            // 光斑
            for (int i = 1; i <= 3; i++) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x18FFFFFF);
                c.drawCircle(px * (0.2f + i * 0.22f), px * (0.7f - i * 0.13f), px * 0.16f * i, p);
            }
        }

        // 底部压暗，便于叠字
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, px * 0.55f, 0, px,
                0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, px * 0.55f, px, px, p);
        p.setShader(null);

        // 顶部提亮，避免整体偏暗
        p.setShader(new LinearGradient(0, 0, 0, px * 0.5f,
                0x33FFFFFF, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, px, px * 0.5f, p);
        p.setShader(null);

        // 平台角标
        float badgeH = px * 0.20f;
        RectF badge = new RectF(px * 0.06f, px * 0.06f, px * 0.06f + badgeH * 2.1f, px * 0.06f + badgeH);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB3000000);
        c.drawRoundRect(badge, badgeH * 0.3f, badgeH * 0.3f, p);
        p.setColor(0xF2FFFFFF);
        p.setTextSize(px * 0.115f);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(label(plat), badge.centerX(), badge.centerY() + px * 0.042f, p);

        // 边框
        p.setFakeBoldText(false);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(px * 0.012f);
        p.setColor(0x33FFFFFF);
        c.drawRoundRect(new RectF(1, 1, px - 1, px - 1), px * 0.09f, px * 0.09f, p);

        CACHE.put(ck, bmp);
        return bmp;
    }

    public static int accentFor(String plat) {
        switch (plat) {
            case Emulators.PLATFORM_GBA:
                return 0xFFA78BFA;   // 紫
            case Emulators.PLATFORM_GBC:
                return 0xFFF472B6;   // 粉
            case Emulators.PLATFORM_NDS:
                return 0xFF34D399;   // 绿
            case Emulators.PLATFORM_SNES:
                return 0xFF60A5FA;   // 蓝
            case Emulators.PLATFORM_NES:
                return 0xFFF87171;   // 红
            case Emulators.PLATFORM_MD:
                return 0xFFFACC15;   // 黄
            case Emulators.PLATFORM_PS1:
                return 0xFF818CF8;   // 靛
            default:
                return 0xFFFB923C;   // PSP 橙
        }
    }

    public static String label(String plat) {
        return Emulators.labelOf(plat);
    }

    private CoverArt() {
    }
}

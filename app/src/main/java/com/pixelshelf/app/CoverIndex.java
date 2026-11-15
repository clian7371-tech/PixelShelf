package com.pixelshelf.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 封面名索引：从 assets 读取，进程内只加载一次 */
public final class CoverIndex {

    private static final Map<String, List<String>> CACHE = new HashMap<>();

    public static synchronized List<String> names(Context ctx, String platform) {
        List<String> hit = CACHE.get(platform);
        if (hit != null) return hit;

        List<String> out = new ArrayList<>();
        String err = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                ctx.getAssets().open("index_" + platform + ".txt"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        } catch (Throwable t) {
            err = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        android.util.Log.d("PixelShelf", "CoverIndex " + platform + " loaded=" + out.size()
                + (err != null ? " err=" + err : ""));
        CACHE.put(platform, out);
        return out;
    }

    private CoverIndex() {
    }
}

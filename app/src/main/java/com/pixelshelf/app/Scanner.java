package com.pixelshelf.app;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 扫描 ROM：遍历用户指定的文件夹；未指定时按各模拟器的常见目录猜。
 * 目录很深时限制扫描量，避免卡死。
 */
public final class Scanner {

    private static final int MAX_DEPTH = 6;
    private static final int MAX_FILES = 20000;
    /** 小于此体积的压缩包不当作 ROM */
    private static final long MIN_ARCHIVE_BYTES = 64 * 1024;

    /** 文件名含这些词的是模拟器系统资源，不是游戏 */
    private static final String[] NOT_GAME_TOKENS = {
            "bios", "firmware", "cheat", "gameshark", "codebreaker", "actionreplay",
    };

    public interface Callback {
        void onProgress(int found);
    }

    public static List<Game> scan(Context ctx, List<String> folders, Callback cb) {
        List<String> roots = new ArrayList<>(folders);
        if (roots.isEmpty()) roots.addAll(defaultRoots(ctx));

        List<Game> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int[] budget = new int[]{MAX_FILES};

        for (String root : roots) {
            File f = new File(root);
            if (!f.isDirectory()) continue;
            walk(f, 0, out, seen, budget, cb);
            if (budget[0] <= 0) break;
        }
        return out;
    }

    /** 各模拟器与常见 ROM 目录的组合 */
    public static List<String> defaultRoots(Context ctx) {
        Set<String> roots = new LinkedHashSet<>();
        String[] bases = externalRoots(ctx);
        String[] relatives = {
                "",
                "Download",
                "Downloads",
                "ROMS", "roms", "Roms",
                "Games", "games",
                "Emulators", "emulators",
                "MyBoy", "myboy", "My Boy",
                "DraStic", "drastic", "DraStic/roms",
                "PPSSPP", "ppsspp", "PSP", "PSP/GAME",
                "RetroArch", "retroarch", "RetroArch/downloads",
                "roms/gba", "roms/nds", "roms/psp",
                "ROMs/GBA", "ROMs/NDS", "ROMs/PSP",
        };
        for (String base : bases) {
            for (String rel : relatives) {
                File f = rel.isEmpty() ? new File(base) : new File(base, rel);
                if (f.isDirectory()) roots.add(f.getAbsolutePath());
            }
        }
        return new ArrayList<>(roots);
    }

    public static String[] externalRoots(Context ctx) {
        List<String> list = new ArrayList<>();
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null) list.add(primary.getAbsolutePath());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    for (StorageVolume v : sm.getStorageVolumes()) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                File d = v.getDirectory();
                                if (d != null) list.add(d.getAbsolutePath());
                            } else {
                                // API 24-29 没有 getDirectory()，用反射拿路径
                                java.lang.reflect.Method m =
                                        StorageVolume.class.getMethod("getPath");
                                Object p = m.invoke(v);
                                if (p instanceof String) list.add((String) p);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return list.toArray(new String[0]);
    }

    private static void walk(File dir, int depth, List<Game> out, Set<String> seen,
                             int[] budget, Callback cb) {
        if (depth > MAX_DEPTH || budget[0] <= 0) return;
        File[] children;
        try {
            children = dir.listFiles();
        } catch (Throwable t) {
            return;
        }
        if (children == null) return;

        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File f : children) {
            if (budget[0] <= 0) return;
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (f.isDirectory()) {
                String lower = name.toLowerCase(Locale.US);
                if (lower.equals("android") || lower.equals("dcim")
                        || lower.equals("pictures") || lower.equals("movies")
                        || lower.equals("music") || lower.equals("ringtones")
                        || lower.equals("alarms") || lower.equals("notifications")
                        || lower.equals("podcasts") || lower.equals("recordings")) {
                    continue;
                }
                walk(f, depth + 1, out, seen, budget, cb);
                continue;
            }

            budget[0]--;
            String lower = name.toLowerCase(Locale.US);
            if (isNotAGame(lower)) continue;
            String platform = Emulators.platformOf(lower);
            if (platform == null) continue;
            // 压缩包太小基本不可能是 ROM（排除 drastic_bios.zip 这类系统资源）
            if (isArchive(lower) && f.length() < MIN_ARCHIVE_BYTES) continue;

            // 用规范路径去重：/storage/emulated/0 与 /storage/self/primary 指向同一文件
            String key;
            try {
                key = f.getCanonicalPath();
            } catch (Throwable t) {
                key = f.getAbsolutePath();
            }
            if (!seen.add(key)) continue;
            out.add(new Game(key, Game.readName(key), platform, f.length(), f.lastModified()));
            if (cb != null && out.size() % 20 == 0) cb.onProgress(out.size());
        }
    }

    /** 模拟器的系统文件不是游戏 */
    private static boolean isNotAGame(String lowerName) {
        for (String bad : NOT_GAME_TOKENS) {
            if (lowerName.contains(bad)) return true;
        }
        return false;
    }

    private static boolean isArchive(String lowerName) {
        return lowerName.endsWith(".zip") || lowerName.endsWith(".7z") || lowerName.endsWith(".rar");
    }

    private Scanner() {
    }
}

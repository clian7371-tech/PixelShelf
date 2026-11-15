package com.pixelshelf.app;

import java.util.ArrayList;
import java.util.List;

/** 模拟器定义：包名候选、平台、ROM 后缀、启动方式 */
public final class Emulators {

    public static final String PLATFORM_GBA = "gba";
    public static final String PLATFORM_NDS = "nds";
    public static final String PLATFORM_PSP = "psp";

    public static final class Def {
        public final String platform;
        public final String label;
        public final String[] packages;
        public final String[] extensions;
        /** true: 用 ACTION_VIEW + data=file Uri；false: 用包名+Activity 显式启动（不支持文件 Uri） */
        public final boolean viewIntent;

        Def(String platform, String label, String[] packages, String[] extensions, boolean viewIntent) {
            this.platform = platform;
            this.label = label;
            this.packages = packages;
            this.extensions = extensions;
            this.viewIntent = viewIntent;
        }
    }

    public static final List<Def> ALL = new ArrayList<>();

    static {
        ALL.add(new Def(PLATFORM_GBA, "GBA",
                new String[]{
                        "com.fastemulator.gba",      // My Boy!
                        "com.fastemulator.gbafree",  // My Boy! Free
                        "com.explusalpha.GbaEmu",    // GBA.emu
                        "com.pizzaboy.gba",          // Pizza Boy GBA
                        "com.pizzaboy.gbapro",
                        "org.mgba.emu",              // mGBA
                        "io.mgba.mgba",
                        "com.nostalgiaemulators.gbagbafull",
                        "com.retroarch",             // RetroArch
                        "com.retroarch.aarch64"
                },
                new String[]{".gba", ".gbc", ".gb", ".zip", ".7z"},
                true));

        ALL.add(new Def(PLATFORM_NDS, "NDS",
                new String[]{
                        "com.dsemu.drastic",         // DraStic
                        "com.dsemu.drasticdemo",
                        "me.magnum.melonds",         // melonDS
                        "me.magnum.melonds.qandroid",// melonDS 旧版
                        "com.explusalpha.NdsEmu",    // NDS.emu
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".nds", ".ds", ".zip", ".7z"},
                true));

        ALL.add(new Def(PLATFORM_PSP, "PSP",
                new String[]{
                        "org.ppsspp.ppsspp",         // PPSSPP Gold
                        "org.ppsspp.ppssppfree",     // PPSSPP
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".iso", ".cso", ".pbp", ".chd", ".elf"},
                true));
    }

    public static Def byPlatform(String platform) {
        for (Def d : ALL) {
            if (d.platform.equals(platform)) return d;
        }
        return null;
    }

    /** 根据文件后缀判断平台，未知返回 null */
    public static String platformOf(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase(java.util.Locale.US);
        for (Def d : ALL) {
            for (String ext : d.extensions) {
                if (lower.endsWith(ext)) return d.platform;
            }
        }
        return null;
    }

    private Emulators() {
    }
}

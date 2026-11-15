package com.pixelshelf.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 模拟器定义：包名候选、平台、ROM 后缀、启动方式 */
public final class Emulators {

    public static final String PLATFORM_GBA = "gba";
    public static final String PLATFORM_GBC = "gbc";
    public static final String PLATFORM_NDS = "nds";
    public static final String PLATFORM_PSP = "psp";
    public static final String PLATFORM_SNES = "snes";
    public static final String PLATFORM_NES = "nes";
    public static final String PLATFORM_MD = "md";
    public static final String PLATFORM_PS1 = "ps1";

    /** 标签栏与排序用的固定顺序 */
    public static final String[] ORDER = {
            PLATFORM_GBA, PLATFORM_GBC, PLATFORM_NDS, PLATFORM_PSP,
            PLATFORM_SNES, PLATFORM_NES, PLATFORM_MD, PLATFORM_PS1
    };

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

    private static final String[] RETROARCH = {"com.retroarch", "com.retroarch.aarch64"};

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
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".gba"},
                true));

        // GB / GBC 共用同一批模拟器（My OldBoy!、GBC.emu 都同时支持两者）
        ALL.add(new Def(PLATFORM_GBC, "GBC",
                new String[]{
                        "com.fastemulator.gbc",       // My OldBoy!
                        "com.fastemulator.gbcfree",
                        "com.fastemulator.gba",       // My Boy! 也支持 GB/GBC
                        "com.fastemulator.gbafree",
                        "com.explusalpha.GbcEmu",     // GBC.emu
                        "com.pizzaboy.gbc",
                        "com.pizzaboy.gbcpro",
                        "com.nostalgiaemulators.gbagbafull",
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".gbc", ".gb"},
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
                new String[]{".nds", ".ds"},
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

        ALL.add(new Def(PLATFORM_SNES, "SNES",
                new String[]{
                        "com.explusalpha.Snes9x",         // Snes9x EX+
                        "com.bubblezapgames.snes",        // SuperRetro16
                        "com.nostalgiaemulators.snesfull",// Nostalgia.SNES
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".sfc", ".smc", ".swc"},
                true));

        ALL.add(new Def(PLATFORM_NES, "NES",
                new String[]{
                        "com.explusalpha.NesEmu",          // NES.emu
                        "com.nostalgiaemulators.nesfull",  // Nostalgia.NES
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".nes", ".fds", ".unf", ".unif"},
                true));

        ALL.add(new Def(PLATFORM_MD, "MD",
                new String[]{
                        "com.explusalpha.MdEmu",   // MD.emu
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".md", ".gen", ".smd"},
                true));

        // PS1 只认 .cue/.ccd/.m3u：.iso/.pbp/.chd 与 PSP 冲突，避免误判
        ALL.add(new Def(PLATFORM_PS1, "PS1",
                new String[]{
                        "com.epsxe.ePSXe",                      // ePSXe
                        "com.github.stenzek.duckstation",       // DuckStation
                        "com.emulator.fpse",                    // FPse
                        "com.retroarch",
                        "com.retroarch.aarch64"
                },
                new String[]{".cue", ".ccd", ".m3u"},
                true));
    }

    public static Def byPlatform(String platform) {
        for (Def d : ALL) {
            if (d.platform.equals(platform)) return d;
        }
        return null;
    }

    /** 标签显示名 */
    public static String labelOf(String platform) {
        Def d = byPlatform(platform);
        return d == null ? platform.toUpperCase(Locale.US) : d.label;
    }

    /**
     * 根据文件后缀判断平台，未知返回 null。
     * 压缩包按文件名关键词判断（.zip 本身不能确定平台）。
     */
    public static String platformOf(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase(Locale.US);

        if (lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".rar")) {
            return platformOfArchive(lower);
        }

        String best = null;
        int bestLen = 0;
        for (Def d : ALL) {
            for (String ext : d.extensions) {
                if (lower.endsWith(ext) && ext.length() > bestLen) {
                    best = d.platform;
                    bestLen = ext.length();
                }
            }
        }
        return best;
    }

    /** 压缩包：按文件名里的平台关键词识别 */
    private static String platformOfArchive(String lower) {
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        // 顺序即优先级，越具体的放前面
        if (name.contains("gba")) return PLATFORM_GBA;
        if (name.contains("gbc") || name.contains("myoldboy")) return PLATFORM_GBC;
        if (name.contains("nds") || name.contains("drastic")) return PLATFORM_NDS;
        if (name.contains("psp")) return PLATFORM_PSP;
        if (name.contains("snes") || name.contains("sfc")) return PLATFORM_SNES;
        if (name.contains("nes")) return PLATFORM_NES;
        if (name.contains("genesis") || name.contains("megadrive") || name.contains("mega drive")) {
            return PLATFORM_MD;
        }
        if (name.contains("psx") || name.contains("ps1") || name.contains("psone")) return PLATFORM_PS1;
        if (name.contains("gb")) return PLATFORM_GBC;
        return null;
    }

    private Emulators() {
    }
}

import com.pixelshelf.app.Emulators;
import com.pixelshelf.app.Game;

/** 纯逻辑自测：文件名清洗 + 平台识别（不依赖 Android） */
public class LogicTest {

    static int pass = 0, fail = 0;

    static void eq(String label, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + label + "\n  got  = " + got + "\n  want = " + want);
        }
    }

    public static void main(String[] args) {
        // --- 文件名清洗 ---
        eq("usa tag", Game.readName("/sdcard/ROMs/Pokemon Emerald (USA).gba"), "Pokemon Emerald");
        eq("no-intro", Game.readName("/x/Metroid Fusion (USA, Europe) [!].gba"), "Metroid Fusion");
        eq("rev", Game.readName("/x/Mario Kart DS (USA) (Rev 1).nds"), "Mario Kart DS");
        eq("underscore", Game.readName("/x/Golden_Sun.gba"), "Golden Sun");
        eq("nested brackets",
                Game.readName("/x/Zelda - Minish Cap (U) (Kiosk) [b1].gba"), "Zelda - Minish Cap");
        eq("cso", Game.readName("/x/Crisis Core (USA).cso"), "Crisis Core");
        eq("no tag", Game.readName("/x/Chrono Trigger.nds"), "Chrono Trigger");
        eq("dots in name", Game.readName("/x/Dr. Mario (USA).gba"), "Dr. Mario");

        // --- 平台识别 ---
        eq("gba", Emulators.platformOf("pokemon.gba"), "gba");
        eq("gbc", Emulators.platformOf("zelda.gbc"), "gbc");
        eq("gb", Emulators.platformOf("tetris.gb"), "gbc");
        eq("nds", Emulators.platformOf("mario.nds"), "nds");
        eq("ds", Emulators.platformOf("game.ds"), "nds");
        eq("iso", Emulators.platformOf("crisis.iso"), "psp");
        eq("cso", Emulators.platformOf("crisis.cso"), "psp");
        eq("pbp", Emulators.platformOf("ff7.pbp"), "psp");
        eq("snes sfc", Emulators.platformOf("mario.sfc"), "snes");
        eq("snes smc", Emulators.platformOf("zelda.smc"), "snes");
        eq("nes", Emulators.platformOf("contra.nes"), "nes");
        eq("md", Emulators.platformOf("sonic.md"), "md");
        eq("ps1 cue", Emulators.platformOf("ff7.cue"), "ps1");
        eq("ps1 m3u", Emulators.platformOf("metal.m3u"), "ps1");
        eq("unknown txt", Emulators.platformOf("readme.txt"), null);
        eq("unknown mp4", Emulators.platformOf("movie.mp4"), null);
        eq("large", Emulators.platformOf("GAME.GBA"), "gba");

        // --- 压缩包按名字识别平台 ---
        eq("zip gba", Emulators.platformOf("/roms/Pokemon GBA.zip"), "gba");
        eq("zip nds", Emulators.platformOf("/roms/Mario NDS.zip"), "nds");
        eq("zip snes", Emulators.platformOf("/roms/Zelda SNES.zip"), "snes");
        eq("zip unknown", Emulators.platformOf("/roms/backup.zip"), null);

        // --- 大小文本 ---
        eq("kb", new Game("/x/a.gba", "a", "gba", 512 * 1024, 0).sizeText(), "512 KB");
        eq("mb", new Game("/x/a.gba", "a", "gba", (long) (1.5 * 1024 * 1024), 0).sizeText(), "1.5 MB");
        eq("gb", new Game("/x/a.iso", "a", "psp", 2L * 1024 * 1024 * 1024, 0).sizeText(), "2.00 GB");
        eq("zero", new Game("/x/a.gba", "a", "gba", 0, 0).sizeText(), "");

        System.out.println("\n--- pass=" + pass + " fail=" + fail + " ---");
        if (fail > 0) System.exit(1);
    }
}

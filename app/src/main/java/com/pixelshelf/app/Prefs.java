package com.pixelshelf.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 设置的持久化 */
public final class Prefs {

    private static final String NAME = "pixelshelf";

    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_EMU = "emu_";           // emu_gba / emu_nds / emu_psp
    private static final String KEY_BOOT = "boot_start";
    private static final String KEY_LAST_TAB = "last_tab";
    private static final String KEY_GRID = "grid";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public List<String> folders() {
        String raw = sp.getString(KEY_FOLDERS, null);
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }

    public void setFolders(List<String> list) {
        Set<String> uniq = new LinkedHashSet<>();
        for (String s : list) {
            String t = s == null ? "" : s.trim();
            if (!t.isEmpty()) uniq.add(t);
        }
        sp.edit().putString(KEY_FOLDERS, join(uniq)).apply();
    }

    public void addFolder(String path) {
        List<String> list = folders();
        list.add(path);
        setFolders(list);
    }

    public void removeFolder(String path) {
        List<String> list = folders();
        list.remove(path);
        setFolders(list);
    }

    /** 用户为平台指定的模拟器包名；空串表示未指定，需自动检测 */
    public String emulator(String platform) {
        return sp.getString(KEY_EMU + platform, "");
    }

    public void setEmulator(String platform, String pkg) {
        sp.edit().putString(KEY_EMU + platform, pkg == null ? "" : pkg.trim()).apply();
    }

    public boolean bootStart() {
        return sp.getBoolean(KEY_BOOT, false);
    }

    public void setBootStart(boolean on) {
        sp.edit().putBoolean(KEY_BOOT, on).apply();
    }

    public int lastTab() {
        return sp.getInt(KEY_LAST_TAB, 0);
    }

    public void setLastTab(int i) {
        sp.edit().putInt(KEY_LAST_TAB, i).apply();
    }

    public boolean listMode() {
        return sp.getBoolean(KEY_GRID, false);
    }

    public void setListMode(boolean list) {
        sp.edit().putBoolean(KEY_GRID, list).apply();
    }

    /** 是否自动从网络抓取封面 */
    public boolean autoCover() {
        return sp.getBoolean("auto_cover", true);
    }

    public void setAutoCover(boolean on) {
        sp.edit().putBoolean("auto_cover", on).apply();
    }

    /** 自定义封面图源地址；空串表示使用内置默认图源 */
    public String coverBase() {
        return sp.getString("cover_base", "");
    }

    public void setCoverBase(String url) {
        sp.edit().putString("cover_base", url == null ? "" : url.trim()).apply();
    }

    /** 用户是否尝试过把本应用设为默认桌面 */
    public boolean homeAttempted() {
        return sp.getBoolean("home_attempted", false);
    }

    public void setHomeAttempted(boolean v) {
        sp.edit().putBoolean("home_attempted", v).apply();
    }

    /** 尝试过但没成功 → 说明本机系统不允许第三方桌面 */
    public boolean homeBlocked() {
        return sp.getBoolean("home_blocked", false);
    }

    public void setHomeBlocked(boolean v) {
        sp.edit().putBoolean("home_blocked", v).apply();
    }

    private static String join(Set<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        return sb.toString();
    }
}

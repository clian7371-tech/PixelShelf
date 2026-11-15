package com.pixelshelf.app;

import java.io.File;
import java.util.Locale;

/** 一个游戏条目 */
public final class Game {
    public final String path;
    public final String name;
    public final String platform;
    public final long size;
    public final long modified;

    public Game(String path, String name, String platform, long size, long modified) {
        this.path = path;
        this.name = name;
        this.platform = platform;
        this.size = size;
        this.modified = modified;
    }

    public File file() {
        return new File(path);
    }

    public String readName() {
        return readName(path);
    }

    /**
     * 从文件名生成显示名：去掉扩展名、去掉 (USA)/(Europe) 等区域标签、
     * 去掉 [!] (Rev 1) 等 no-intro 标记，下划线换空格。
     */
    public static String readName(String path) {
        String base = new File(path).getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        StringBuilder out = new StringBuilder(base.length());
        int depth = 0;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') {
                if (depth > 0) depth--;
            } else if (depth == 0) {
                out.append(c == '_' ? ' ' : c);
            }
        }
        String s = out.toString().replaceAll("\\s{2,}", " ").trim();
        if (s.isEmpty()) s = base;
        return s;
    }

    public String sizeText() {
        if (size <= 0) return "";
        double kb = size / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}

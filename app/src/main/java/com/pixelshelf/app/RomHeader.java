package com.pixelshelf.app;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

/**
 * 从 ROM 文件内部读出官方标题与游戏编号。
 * GBA/NDS 头部有 12 字节标题；PSP 要读 ISO9660 里的 PSP_GAME/PARAM.SFO。
 */
public final class RomHeader {

    public static final class Info {
        /** 官方英文标题，可能为 null */
        public String title;
        /** 游戏编号，如 BPEE / IPKJ / ULUS10001 */
        public String code;
    }

    private static final Charset ASCII = Charset.forName("US-ASCII");
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static Info read(String path, String platform) {
        Info info = new Info();
        try {
            if (Emulators.PLATFORM_GBA.equals(platform)) {
                readGba(new File(path), info);
            } else if (Emulators.PLATFORM_NDS.equals(platform)) {
                readNds(new File(path), info);
            } else if (Emulators.PLATFORM_PSP.equals(platform)) {
                readPsp(new File(path), info);
            }
        } catch (Throwable ignored) {
            // 读不出来就让调用方回退到文件名
        }
        return info;
    }

    private static void readGba(File f, Info info) throws IOException {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[0xB0];
            r.seek(0xA0);
            if (r.read(b) < 0xA0 - 0x9F) return;
            info.title = clean(new String(b, 0, 12, ASCII));
            info.code = clean(new String(b, 12, 4, ASCII));
        } finally {
            r.close();
        }
    }

    private static void readNds(File f, Info info) throws IOException {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[0x20];
            r.seek(0);
            if (r.read(b) < 0x20) return;
            info.title = clean(new String(b, 0, 12, ASCII));
            info.code = clean(new String(b, 12, 4, ASCII));
        } finally {
            r.close();
        }
    }

    // ---- PSP: 走 ISO9660 找 PSP_GAME/PARAM.SFO ----

    private static final int SECTOR = 2048;

    private static void readPsp(File f, Info info) {
        RandomAccessFile r = null;
        try {
            r = new RandomAccessFile(f, "r");
            long pvd = 16L * SECTOR;
            byte[] rec = new byte[2048];
            r.seek(pvd);
            if (r.read(rec) != 2048) return;
            if (rec[0] != 1 || rec[1] != 'C' || rec[2] != 'D' || rec[3] != '0' || rec[4] != '0' || rec[5] != '1') {
                return;
            }
            // PVD 里的 root directory record 在偏移 156
            long rootLba = u32le(rec, 156 + 2);
            long rootLen = u32le(rec, 156 + 10);

            long sfoLba = findPath(r, rootLba, rootLen, new String[]{"PSP_GAME", "PARAM.SFO"}, 0);
            if (sfoLba <= 0) return;

            r.seek(sfoLba * SECTOR);
            byte[] sfo = new byte[SECTOR * 2];
            int n = r.read(sfo);
            if (n <= 0) return;
            parseSfo(sfo, n, info);
        } catch (Throwable ignored) {
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 在目录里按路径逐级查找，返回目标文件起始扇区 */
    private static long findPath(RandomAccessFile r, long dirLba, long dirLen,
                                 String[] path, int depth) throws IOException {
        if (depth >= path.length || depth > 6) return -1;
        String want = path[depth];

        long offset = 0;
        byte[] buf = new byte[SECTOR];
        while (offset < dirLen) {
            r.seek(dirLba * SECTOR + offset);
            int n = r.read(buf);
            if (n <= 0) break;
            int p = 0;
            while (p < n) {
                int recLen = buf[p] & 0xFF;
                if (recLen == 0) break;
                int nameLen = buf[p + 32] & 0xFF;
                long lba = u32le(buf, p + 2);
                long dataLen = u32le(buf, p + 10);
                boolean isDir = (buf[p + 25] & 0x02) != 0;

                String name = new String(buf, p + 33, nameLen, ASCII);
                int semi = name.indexOf(';');
                if (semi >= 0) name = name.substring(0, semi);

                if (name.equalsIgnoreCase(want)) {
                    if (depth == path.length - 1) return lba;
                    if (isDir) return findPath(r, lba, dataLen, path, depth + 1);
                    return -1;
                }
                p += recLen;
            }
            offset += SECTOR;
        }
        return -1;
    }

    /** PARAM.SFO 里取 TITLE */
    private static void parseSfo(byte[] d, int len, Info info) {
        if (len < 20) return;
        if (!(d[0] == 0 && d[1] == 'P' && d[2] == 'S' && d[3] == 'F')) return;
        long keyTable = u32le(d, 8);
        long dataTable = u32le(d, 12);
        long count = u32le(d, 16);
        if (count <= 0 || count > 64) return;

        for (int i = 0; i < count; i++) {
            int e = 20 + i * 16;
            if (e + 16 > len) return;
            int keyOff = u16le(d, e);
            int dataFmt = u16le(d, e + 2);
            long dataLen = u32le(d, e + 4);
            long dataOff = u32le(d, e + 12);

            String key = cstr(d, (int) (keyTable + keyOff), len);
            if (!"TITLE".equals(key)) continue;

            long abs = dataTable + dataOff;
            if (abs < 0 || abs + dataLen > len) return;
            String val = dataFmt == 0x0004
                    ? new String(d, (int) abs, (int) Math.min(dataLen, 512), UTF8)
                    : new String(d, (int) abs, (int) Math.min(dataLen, 512), ASCII);
            info.title = clean(val);
            return;
        }
    }

    private static String cstr(byte[] d, int off, int len) {
        if (off < 0 || off >= len) return "";
        int end = off;
        while (end < len && d[end] != 0) end++;
        return new String(d, off, end - off, ASCII);
    }

    private static long u32le(byte[] d, int o) {
        if (o + 4 > d.length) return -1;
        return ((long) (d[o] & 0xFF))
                | ((long) (d[o + 1] & 0xFF) << 8)
                | ((long) (d[o + 2] & 0xFF) << 16)
                | ((long) (d[o + 3] & 0xFF) << 24);
    }

    private static int u16le(byte[] d, int o) {
        if (o + 2 > d.length) return 0;
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8);
    }

    /** 去掉尾部的 0 与乱码，只保留可打印 ASCII */
    private static String clean(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0) break;
            if (c >= 0x20 && c < 0x7F) sb.append(c);
        }
        String t = sb.toString().trim();
        if (t.isEmpty()) return null;
        // 全是问号说明不是有效 ASCII
        int letters = 0;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetterOrDigit(t.charAt(i))) letters++;
        }
        if (letters < 2) return null;
        return t;
    }

    private RomHeader() {
    }
}

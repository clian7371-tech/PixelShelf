package com.pixelshelf.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 封面抓取：用打包进 APK 的封面名索引做离线模糊匹配，
 * 命中后只下载那一张图并永久缓存到本地。
 *
 * 匹配优先级：ROM 内部官方标题 > 中文别名展开 > 文件名
 *
 * 对图源服务的友好策略：
 * - 请求串行，两次请求之间至少间隔 1.2 秒，避免给公共图库造成压力
 * - 已被缓存过的游戏不会重复下载
 * - 收到 429 / 503 时按 Retry-After 退避，本轮不再纠缠
 * - 图源可在设置中替换或关闭自动获取
 */
public final class CoverFetcher {

    /** 默认图源（公开缩略图库）。用户可在设置里换成自建镜像。 */
    public static final String DEFAULT_BASE = "https://thumbnails.libretro.com/";

    /** 两次网络请求之间的最小间隔 */
    private static final long MIN_GAP_MS = 1200L;
    private static long lastRequestAt = 0L;

    private static final Map<String, String> SYSTEM_DIR = new HashMap<>();

    static {
        SYSTEM_DIR.put(Emulators.PLATFORM_GBA, "Nintendo - Game Boy Advance");
        SYSTEM_DIR.put(Emulators.PLATFORM_NDS, "Nintendo - Nintendo DS");
        SYSTEM_DIR.put(Emulators.PLATFORM_PSP, "Sony - PlayStation Portable");
    }

    /** 当前生效的图源地址 */
    public static String base(Context ctx) {
        String b = null;
        try {
            b = new Prefs(ctx).coverBase();
        } catch (Throwable ignored) {
        }
        if (b == null || b.trim().isEmpty()) return DEFAULT_BASE;
        b = b.trim();
        if (!b.endsWith("/")) b += "/";
        return b;
    }

    public interface Listener {
        void onCover(Game game, Bitmap bmp);

        void onProgress(int done, int total);
    }

    /** 缓存目录：优先外部可见的应用目录，方便用户手动放图 */
    public static File cacheDir(Context ctx) {
        File dir = null;
        try {
            File ext = ctx.getExternalFilesDir(null);
            if (ext != null) dir = new File(ext, "covers");
        } catch (Throwable ignored) {
        }
        if (dir == null) dir = new File(ctx.getFilesDir(), "covers");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 本地缓存路径 */
    public static File cacheFile(Context ctx, Game g) {
        return new File(cacheDir(ctx), cacheKey(g) + ".png");
    }

    /**
     * 用户自己放的封面：ROM 同目录下的同名图片，
     * 或缓存目录里以 ROM 主名（去标签）命名的图片。优先级最高。
     */
    public static Bitmap userCover(Context ctx, Game g) {
        File rom = new File(g.path);
        File dir = rom.getParentFile();
        String base = rom.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        if (dir != null) {
            for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp"}) {
                File f = new File(dir, base + ext);
                if (f.exists() && f.length() > 512) {
                    Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (b != null) return b;
                }
            }
            // 也支持 <名称>.jpg 放在 covers 子目录
            File covDir = new File(dir, "covers");
            for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp"}) {
                File f = new File(covDir, base + ext);
                if (f.exists() && f.length() > 512) {
                    Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (b != null) return b;
                }
            }
        }

        // 缓存目录里按显示名查找
        File cd = cacheDir(ctx);
        String safe = g.name.replaceAll("[\\\\/:*?\"<>|]", "_");
        for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
            File f = new File(cd, safe + ext);
            if (f.exists() && f.length() > 512) {
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (b != null) return b;
            }
        }
        return null;
    }

    public static Bitmap cached(Context ctx, Game g) {
        Bitmap user = userCover(ctx, g);
        if (user != null) return user;

        File f = cacheFile(ctx, g);
        if (!f.exists() || f.length() < 512) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String cacheKey(Game g) {
        // 路径 + 修改时间，重打 ROM 后自动失效
        String s = g.path + "|" + g.modified + "|" + g.size;
        int h = 5381;
        for (int i = 0; i < s.length(); i++) h = (h * 33) ^ s.charAt(i);
        return Integer.toHexString(h);
    }

    // ---- 封面来源记录（用于显示「改版」标记）----

    private static File sourceFile(Context ctx, Game g) {
        return new File(cacheDir(ctx), cacheKey(g) + ".src");
    }

    /** 这张封面来自图库里的哪一项；未知返回 null */
    public static String sourceName(Context ctx, Game g) {
        File f = sourceFile(ctx, g);
        if (!f.exists()) return null;
        try {
            java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r");
            byte[] b = new byte[(int) Math.min(f.length(), 512)];
            r.readFully(b);
            r.close();
            String s = new String(b, "UTF-8").trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeSource(Context ctx, Game g, String name) {
        try (OutputStream out = new FileOutputStream(sourceFile(ctx, g))) {
            out.write(name.getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    /** 构造匹配用的查询串：ROM 内部标题 > 中文别名 > 文件名 */
    private static List<String> queriesOf(Context ctx, Game g) {
        List<String> q = new ArrayList<>();
        RomHeader.Info info = RomHeader.read(g.path, g.platform);
        if (info.title != null) {
            q.add(info.title.replace(':', ' ').replace('/', ' '));
        }
        q.addAll(CoverMatch.expandAliases(g.name));
        q.addAll(CoverMatch.expandAliases(Game.readName(g.path)));
        q.add(g.name);
        q.add(Game.readName(g.path));
        return q;
    }

    /**
     * 疑似「改版」：封面来源和这个 ROM 的名字完全对不上。
     * 比如「西班牙火箭队」拿到了「Pokemon Emerald」的封面。
     */
    /**
     * 疑似「改版」：这个 ROM 的名字和封面来源对不上。
     * 只看文件名与中文别名——不看 ROM 头部，因为魔改版头部往往就是原版的，
     * 用头部判断会把「西班牙火箭队（绿宝石改版）」误认成原版绿宝石。
     */
    public static boolean looksLikeHack(Context ctx, Game g) {
        String src = sourceName(ctx, g);
        if (src == null) return false;
        String srcNorm = CoverMatch.norm(src);
        if (srcNorm.isEmpty()) return false;

        List<String> queries = new ArrayList<>();
        queries.addAll(CoverMatch.expandAliases(g.name));
        queries.addAll(CoverMatch.expandAliases(Game.readName(g.path)));
        queries.add(g.name);
        queries.add(Game.readName(g.path));

        for (String q : queries) {
            if (q == null) continue;
            String qn = CoverMatch.norm(q);
            if (qn.length() < 3) continue;
            // 整体包含关系
            if (srcNorm.contains(qn) || qn.contains(srcNorm)) {
                return false;
            }
            // 关键词出现在来源里（如 persona）
            for (String tok : CoverMatch.tokens(q)) {
                if (tok.length() >= 4 && srcNorm.contains(tok)) {
                    return false;
                }
            }
            if (CoverMatch.score(q, src) >= 0.8f) {
                return false;
            }
        }
        return true;
    }

    /** 为一个游戏抓封面；命中返回 Bitmap 并写缓存，否则 null */
    public static Bitmap fetch(Context ctx, Game g) {
        Bitmap hit = cached(ctx, g);
        if (hit != null) return hit;

        String platform = g.platform;
        if (!SYSTEM_DIR.containsKey(platform)) return null;

        String matched = CoverMatch.best(ctx, platform, queriesOf(ctx, g));
        if (matched == null) return null;

        String sysDir = SYSTEM_DIR.get(platform);
        String url = base(ctx) + enc(sysDir) + "/Named_Boxarts/" + enc(matched) + ".png";
        Bitmap bmp = download(url);
        if (bmp == null) return null;

        // 限制尺寸，省内存
        if (bmp.getWidth() > 640) {
            int w = 640;
            int h = Math.round(bmp.getHeight() * (640f / bmp.getWidth()));
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }

        try (OutputStream out = new FileOutputStream(cacheFile(ctx, g))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
        } catch (Throwable ignored) {
        }
        writeSource(ctx, g, matched);
        return bmp;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Throwable t) {
            return s.replace(" ", "%20");
        }
    }

    /** 请求节流：保证两次网络请求之间至少间隔 MIN_GAP_MS */
    private static synchronized void throttle() {
        long wait = lastRequestAt + MIN_GAP_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ignored) {
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private static Bitmap download(String urlStr) {
        HttpURLConnection c = null;
        try {
            throttle();
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(45000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "PixelShelf/" + versionName() + " (Android)");
            c.setRequestProperty("Accept", "image/png,image/*;q=0.8");

            int code = c.getResponseCode();
            if (code == 429 || code == 503) {
                // 图源限流：按 Retry-After 退避，本轮放弃，下次刷新再试
                long wait = 8000L;
                String ra = c.getHeaderField("Retry-After");
                if (ra != null) {
                    try {
                        wait = Math.min(60000L, Math.max(2000L, Long.parseLong(ra.trim()) * 1000L));
                    } catch (Throwable ignored) {
                    }
                }
                android.util.Log.w("PixelShelf", "图源限流 " + code + "，退避 " + wait + "ms");
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ignored) {
                }
                return null;
            }
            if (code != 200) {
                android.util.Log.w("PixelShelf", "图源返回 " + code + " " + urlStr);
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 版本号由 App 启动时注入，用于 User-Agent 标识 */
    private static String APP_VERSION = "dev";

    public static void setAppVersion(String v) {
        if (v != null && !v.isEmpty()) APP_VERSION = v;
    }

    private static String versionName() {
        return APP_VERSION;
    }

    /** 手动刷新：删缓存后重抓 */
    public static Bitmap refetch(Context ctx, Game g) {
        File f = cacheFile(ctx, g);
        if (f.exists()) f.delete();
        File s = sourceFile(ctx, g);
        if (s.exists()) s.delete();
        return fetch(ctx, g);
    }

    /** 清除封面（图片 + 来源记录） */
    public static void clear(Context ctx, Game g) {
        File f = cacheFile(ctx, g);
        if (f.exists()) f.delete();
        File s = sourceFile(ctx, g);
        if (s.exists()) s.delete();
    }

    /** 用用户指定的封面名（索引里的原始名或搜索词）抓取 */
    public static Bitmap fetchByName(Context ctx, Game g, String query) {
        String platform = g.platform;
        if (!SYSTEM_DIR.containsKey(platform)) return null;

        String matched = CoverMatch.best(ctx, platform, java.util.Collections.singletonList(query));
        if (matched == null) {
            // 用户可能直接给了精确名
            matched = query;
        }
        String url = base(ctx) + enc(SYSTEM_DIR.get(platform)) + "/Named_Boxarts/" + enc(matched) + ".png";
        Bitmap bmp = download(url);
        if (bmp == null) return null;

        if (bmp.getWidth() > 640) {
            int w = 640;
            int h = Math.round(bmp.getHeight() * (640f / bmp.getWidth()));
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }
        try (OutputStream out = new FileOutputStream(cacheFile(ctx, g))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
        } catch (Throwable ignored) {
        }
        writeSource(ctx, g, matched);
        return bmp;
    }

    /** 在索引里搜候选，供手动选择：返回最多 limit 条 */
    public static List<String> search(Context ctx, String platform, String keyword, int limit) {
        List<String> out = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return out;

        String q = CoverMatch.norm(keyword);
        if (q.isEmpty()) return out;

        for (String n : CoverIndex.names(ctx, platform)) {
            if (CoverMatch.norm(n).contains(q)) {
                out.add(n);
                if (out.size() >= limit) break;
            }
        }
        if (!out.isEmpty()) return out;

        // 退一步用相似度排序
        List<String> all = CoverIndex.names(ctx, platform);
        List<float[]> scored = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            float s = CoverMatch.score(keyword, all.get(i));
            if (s >= 0.4f) scored.add(new float[]{s, i});
        }
        scored.sort((a, b) -> Float.compare(b[0], a[0]));
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            out.add(all.get((int) scored.get(i)[1]));
        }
        return out;
    }

    /** 批量补全：只处理还没有缓存的游戏，回调逐个更新 UI */
    public static void fetchAll(Context ctx, List<Game> games, Listener l) {
        List<Game> todo = new ArrayList<>();
        for (Game g : games) {
            if (cached(ctx, g) == null) todo.add(g);
        }
        int total = todo.size();
        int done = 0;
        for (Game g : todo) {
            Bitmap b = fetch(ctx, g);
            done++;
            if (b != null && l != null) l.onCover(g, b);
            if (l != null) l.onProgress(done, total);
        }
        if (l != null) l.onProgress(total, total);
    }

    private CoverFetcher() {
    }
}

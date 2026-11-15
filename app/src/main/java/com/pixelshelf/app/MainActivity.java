package com.pixelshelf.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 游戏墙：掌机前端风格 */
public class MainActivity extends Activity {

    private Prefs prefs;
    private List<Game> all = new ArrayList<>();
    private String filter = "all";
    private String query = "";

    private LinearLayout tabsBar;
    private PlatformTab[] tabs;
    private GridLayout grid;
    private TextView countView;
    private TextView headline;
    private EditText searchBox;
    private ProgressBar progress;
    private LinearLayout emptyBox;
    private View searchWrap;

    private final List<GameCard> cards = new ArrayList<>();
    private final List<String> tabKeys = new ArrayList<>();
    private int cols = 3;
    /** 卡片高宽比：竖屏偏竖直，横屏偏扁，保证横屏能看到两行 */
    private float cardRatio = 1.24f;
    private boolean landscape = false;

    private LinearLayout topBar;
    private HorizontalScrollView tabsScroll;
    private ScrollView gridScroll;

    private static final java.util.concurrent.ExecutorService COVER_POOL =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> coverJob;
    private java.util.concurrent.atomic.AtomicBoolean currentCancel;

    private static final int REQ_LEGACY = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        setContentView(R.layout.activity_main);
        bind();
        buildTabs();
        startHeadlinePulse();
        askPermissionsThenScan();
    }

    /** 标题辉光呼吸 */
    private void startHeadlinePulse() {
        ObjectAnimator glow = ObjectAnimator.ofFloat(headline, "alpha", 1f, 0.72f, 1f);
        glow.setDuration(3200);
        glow.setRepeatCount(ObjectAnimator.INFINITE);
        glow.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        glow.start();
    }

    private void bind() {
        tabsBar = findViewById(R.id.tabs);
        grid = findViewById(R.id.grid);
        countView = findViewById(R.id.count);
        headline = findViewById(R.id.headline);
        searchBox = findViewById(R.id.search);
        progress = findViewById(R.id.progress);
        emptyBox = findViewById(R.id.empty);
        searchWrap = findViewById(R.id.search_wrap);
        topBar = findViewById(R.id.top_bar);
        tabsScroll = findViewById(R.id.tabs_scroll);
        gridScroll = findViewById(R.id.grid_scroll);

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_refresh).setOnClickListener(v -> rescan());

        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim().toLowerCase();
                render(false);
            }

            public void afterTextChanged(Editable s) {
            }
        });

        cols = getResources().getConfiguration().screenWidthDp >= 600 ? 4 : 3;
        applyResponsiveLayout();
    }

    /**
     * 按当前屏幕方向调整列数与间距。
     * 竖屏：卡片偏竖直，3~4 列；横屏：压缩顶部栏、卡片变扁、增加列数，保证能看到两行。
     */
    private void applyResponsiveLayout() {
        android.content.res.Configuration cfg = getResources().getConfiguration();
        landscape = cfg.screenWidthDp > cfg.screenHeightDp;
        int wDp = Math.max(320, cfg.screenWidthDp);

        if (landscape) {
            // 横屏每列约 130dp
            cols = Math.max(4, Math.min(8, Math.round(wDp / 130f)));
            cardRatio = 1.16f;

            headline.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
            countView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f);
            topBar.setPadding(dp(14), dp(1), dp(10), 0);
            tabsScroll.setPadding(dp(14), dp(2), dp(14), 0);
            gridScroll.setPadding(dp(8), dp(4), dp(8), dp(10));
            setSearchHeight(dp(32));
        } else {
            cols = wDp >= 600 ? 4 : 3;
            cardRatio = 1.24f;

            headline.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f);
            countView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
            topBar.setPadding(dp(18), dp(8), dp(12), dp(2));
            tabsScroll.setPadding(dp(16), dp(10), dp(16), 0);
            gridScroll.setPadding(dp(10), dp(8), dp(10), dp(26));
            setSearchHeight(dp(36));
        }
        // 减少列数时必须先移除子 View，否则旧的行列索引会越界并抛 IllegalArgumentException
        grid.removeAllViews();
        cards.clear();
        grid.setColumnCount(cols);
    }

    private void setSearchHeight(int px) {
        ViewGroup.LayoutParams lp = searchBox.getLayoutParams();
        lp.height = px;
        searchBox.setLayoutParams(lp);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // 清单里声明了 configChanges，旋转时不会重建 Activity，这里手动重新布局
        applyResponsiveLayout();
        render(false);
    }

    private String tabsSignature = "";

    /** 只有在库里的平台才显示标签，顺序固定 */
    private void ensureTabs() {
        StringBuilder sb = new StringBuilder();
        for (String p : Emulators.ORDER) {
            for (Game g : all) {
                if (g.platform.equals(p)) {
                    sb.append(p).append(',');
                    break;
                }
            }
        }
        String sig = sb.toString();
        if (!sig.equals(tabsSignature)) {
            tabsSignature = sig;
            buildTabs();
        }
    }

    private void buildTabs() {
        tabsBar.removeAllViews();
        tabKeys.clear();
        tabKeys.add("all");
        for (String p : Emulators.ORDER) {
            for (Game g : all) {
                if (g.platform.equals(p)) {
                    tabKeys.add(p);
                    break;
                }
            }
        }
        // 选中的平台不在库里了就回到「全部」
        if (!"all".equals(filter) && !tabKeys.contains(filter)) {
            filter = "all";
        }

        tabs = new PlatformTab[tabKeys.size()];
        for (int i = 0; i < tabKeys.size(); i++) {
            final String key = tabKeys.get(i);
            PlatformTab t = new PlatformTab(this);
            String label = "all".equals(key) ? "全部" : Emulators.labelOf(key);
            int accent = "all".equals(key) ? 0xFF22D3EE : CoverArt.accentFor(key);
            t.configure(label, accent);
            t.setSelectedSmooth(key.equals(filter), false);
            t.setClickable(true);
            t.setFocusable(true);
            t.setOnClickListener(v -> selectTab(key));
            t.setOnFocusChangeListener((v, has) -> {
                float s = has ? 1.12f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(140).start();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            t.setLayoutParams(lp);
            tabsBar.addView(t);
            tabs[i] = t;
        }
    }

    private void selectTab(String key) {
        if (key.equals(filter)) return;
        filter = key;
        for (int i = 0; i < tabKeys.size() && i < tabs.length; i++) {
            tabs[i].setSelectedSmooth(tabKeys.get(i).equals(filter), true);
        }
        render(true);
    }

    // ---- 权限 ----

    private void askPermissionsThenScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                findViewById(R.id.perm_box).setVisibility(View.VISIBLE);
                findViewById(R.id.btn_grant).setOnClickListener(v -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Throwable t) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                });
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_LEGACY);
                return;
            }
        }
        findViewById(R.id.perm_box).setVisibility(View.GONE);
        rescan();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_LEGACY) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
                findViewById(R.id.perm_box).setVisibility(View.GONE);
                rescan();
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---- 扫描 ----

    private void rescan() {
        progress.setVisibility(View.VISIBLE);
        countView.setText("扫描中…");
        grid.removeAllViews();
        cards.clear();
        emptyBox.setVisibility(View.GONE);

        final List<String> folders = prefs.folders();
        new Thread(() -> {
            final List<Game> found = Scanner.scan(this, folders, null);
            // 诊断：确认封面索引可用（只在有对应平台游戏时读取）
            java.util.Set<String> plats = new java.util.HashSet<>();
            for (Game g : found) plats.add(g.platform);
            for (String p : plats) CoverIndex.names(this, p);

            runOnUiThread(() -> {
                all = found;
                progress.setVisibility(View.GONE);
                render(false);
            });
        }, "scan").start();
    }

    /** 平台在「全部」里的排序位置，未收录的排最后 */
    private static int platformRank(String platform) {
        for (int i = 0; i < Emulators.ORDER.length; i++) {
            if (Emulators.ORDER[i].equals(platform)) return i;
        }
        return Emulators.ORDER.length;
    }

    private void render(boolean animate) {
        ensureTabs();

        List<Game> shown = new ArrayList<>();
        for (Game g : all) {
            if (!"all".equals(filter) && !g.platform.equals(filter)) continue;
            if (!query.isEmpty() && !g.name.toLowerCase().contains(query)) continue;
            shown.add(g);
        }
        Collections.sort(shown, (a, b) -> {
            int pa = platformRank(a.platform);
            int pb = platformRank(b.platform);
            if (pa != pb) return pa - pb;
            return a.name.compareToIgnoreCase(b.name);
        });

        countView.setText(shown.size() + " 个游戏");
        grid.removeAllViews();
        cards.clear();

        if (shown.isEmpty()) {
            emptyBox.setVisibility(View.VISIBLE);
            TextView t = findViewById(R.id.empty_text);
            t.setText(all.isEmpty() ? R.string.empty_hint : R.string.no_match_hint);
            return;
        }
        emptyBox.setVisibility(View.GONE);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int gap = dp(10);
        int cardW = (screenW - dp(20) - gap * cols) / cols;
        int cardH = Math.round(cardW * cardRatio);

        for (int i = 0; i < shown.size(); i++) {
            final Game g = shown.get(i);
            GameCard card = new GameCard(this, g, cardW, cardH);
            card.setHackBadge(CoverFetcher.looksLikeHack(this, g));
            card.setOnClickListener(v -> play(g));
            card.setOnLongClickListener(v -> {
                showGameMenu(g);
                return true;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW;
            lp.height = cardH;
            lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
            card.setLayoutParams(lp);
            grid.addView(card);
            cards.add(card);

            if (animate) {
                // 掌机式入场：从下方淡入 + 轻微缩放
                card.setAlpha(0f);
                card.setTranslationY(dp(26));
                card.setScaleX(0.92f);
                card.setScaleY(0.92f);
                card.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay(i * 28L)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator(2.4f))
                        .start();
            }
        }

        if (animate) {
            // 卡片区轻微下沉后再浮现，强化切换反馈
            grid.setAlpha(0.3f);
            grid.setTranslationY(dp(10));
            grid.animate().alpha(1f).translationY(0f)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }

        // 后台补全网络封面
        startCoverFetch(shown);
    }

    /** 后台抓封面，逐张淡入；没有网络时静默跳过 */
    private void startCoverFetch(List<Game> games) {
        if (!prefs.autoCover()) return;
        if (!hasNetwork()) return;
        if (coverJob != null && !coverJob.isDone()) return;

        final List<Game> target = new ArrayList<>();
        for (Game g : games) {
            if (CoverFetcher.cached(this, g) == null) target.add(g);
        }
        if (target.isEmpty()) return;

        final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        currentCancel = cancelled;

        coverJob = COVER_POOL.submit(() -> {
            int done = 0;
            for (Game g : target) {
                if (cancelled.get()) return;
                final Bitmap bmp = CoverFetcher.fetch(this, g);
                done++;
                final int d = done;
                final int total = target.size();
                runOnUiThread(() -> {
                    if (bmp != null) {
                        for (GameCard c : cards) {
                            if (c.game == g) {
                                c.applyCover(bmp);
                                break;
                            }
                        }
                        refreshHackBadge(g);
                    }
                    if (d == total || d % 3 == 0) {
                        countView.setText(getString(R.string.games_found, cards.size())
                                + "　封面 " + d + "/" + total);
                    }
                });
            }
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    countView.setText(getString(R.string.games_found, cards.size()));
                }
            });
        });
    }

    private boolean hasNetwork() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 长按卡片：手动搜封面 / 重新抓取 / 查看信息 */
    private void showGameMenu(Game g) {
        String src = CoverFetcher.sourceName(this, g);
        boolean hack = CoverFetcher.looksLikeHack(this, g);

        // 自定义标题：游戏名 + 封面来源（AlertDialog 默认标题只有一行）
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), dp(6));

        android.widget.TextView t1 = new android.widget.TextView(this);
        t1.setText(g.name);
        t1.setTextColor(0xFFEAF2FF);
        t1.setTextSize(17f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t1);

        if (src != null) {
            android.widget.TextView t2 = new android.widget.TextView(this);
            t2.setText("封面来自：" + src + (hack ? "（原版游戏）" : ""));
            t2.setTextColor(0xFF7C8CA8);
            t2.setTextSize(12f);
            t2.setPadding(0, dp(6), 0, 0);
            box.addView(t2);
        }

        String[] items = {"搜索封面…", "重新自动获取封面", "清除封面", "用其他模拟器打开"};
        new android.app.AlertDialog.Builder(this)
                .setCustomTitle(box)
                .setItems(items, (d, which) -> {
                    if (which == 0) showCoverSearch(g);
                    else if (which == 1) refetchCover(g);
                    else if (which == 2) {
                        CoverFetcher.clear(this, g);
                        Toast.makeText(this, "已清除封面", Toast.LENGTH_SHORT).show();
                        render(false);
                    } else {
                        play(g);
                    }
                })
                .show();
    }

    /** 封面更新后，同步卡片上的「改版」角标 */
    private void refreshHackBadge(Game g) {
        boolean hack = CoverFetcher.looksLikeHack(this, g);
        for (GameCard c : cards) {
            if (c.game == g) c.setHackBadge(hack);
        }
    }

    private void refetchCover(Game g) {
        if (!hasNetwork()) {
            Toast.makeText(this, "当前没有网络", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在获取封面…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final Bitmap b = CoverFetcher.refetch(this, g);
            runOnUiThread(() -> {
                if (b == null) {
                    Toast.makeText(this, "没找到匹配的封面，试试「搜索封面」", Toast.LENGTH_LONG).show();
                } else {
                    for (GameCard c : cards) {
                        if (c.game == g) c.applyCover(b);
                    }
                    refreshHackBadge(g);
                }
            });
        }, "refetch").start();
    }

    /** 手动搜索封面：输入关键词 → 列表挑选 → 下载 */
    private void showCoverSearch(Game g) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入游戏英文名，如 Pokemon Emerald");
        input.setText(suggestQuery(g));
        input.setSelectAllOnFocus(true);
        int pad = dp(20);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams ilp =
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(pad, dp(8), pad, dp(8));
        input.setLayoutParams(ilp);
        wrap.addView(input);

        new android.app.AlertDialog.Builder(this)
                .setTitle("搜索封面")
                .setView(wrap)
                .setPositiveButton("搜索", (d, w) -> {
                    String kw = input.getText().toString().trim();
                    if (kw.isEmpty()) return;
                    // 查本地索引不需要联网；只有下载那一步才需要
                    List<String> hits = CoverFetcher.search(this, g.platform, kw, 40);
                    if (hits.isEmpty()) {
                        Toast.makeText(this, "索引里没有匹配项（该游戏可能不在图库中）",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    String[] arr = hits.toArray(new String[0]);
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("选择封面（共 " + arr.length + " 个）")
                            .setItems(arr, (dd, idx) -> downloadChosen(g, arr[idx]))
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 给搜索框一个更可能命中的初始值：ROM 内部标题 > 英文别名 > 文件名 */
    private String suggestQuery(Game g) {
        RomHeader.Info info = RomHeader.read(g.path, g.platform);
        if (info.title != null && info.title.length() >= 3) {
            return info.title.replace(':', ' ');
        }
        java.util.List<String> alias = CoverMatch.expandAliases(g.name);
        if (!alias.isEmpty()) return alias.get(alias.size() - 1);
        java.util.List<String> alias2 = CoverMatch.expandAliases(Game.readName(g.path));
        if (!alias2.isEmpty()) return alias2.get(alias2.size() - 1);
        return Game.readName(g.path);
    }

    private void downloadChosen(Game g, String coverName) {
        if (!hasNetwork()) {
            Toast.makeText(this, "下载封面需要网络；已保存选择，联网后长按可重试",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在下载…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final Bitmap b = CoverFetcher.fetchByName(this, g, coverName);
            runOnUiThread(() -> {
                if (b == null) {
                    Toast.makeText(this, "下载失败，检查网络后重试", Toast.LENGTH_LONG).show();
                } else {
                    for (GameCard c : cards) {
                        if (c.game == g) c.applyCover(b);
                    }
                    refreshHackBadge(g);
                    Toast.makeText(this, "封面已保存", Toast.LENGTH_SHORT).show();
                }
            });
        }, "cover-dl").start();
    }

    private void play(Game g) {
        try {
            Launcher.launch(this, g);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.emu_missing, CoverArt.label(g.platform)),
                    Toast.LENGTH_LONG).show();
            openStoreFor(g.platform);
        } catch (Throwable t) {
            Toast.makeText(this, "启动失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openStoreFor(String platform) {
        String q = platform.equals(Emulators.PLATFORM_PSP) ? "ppsspp"
                : platform.equals(Emulators.PLATFORM_NDS) ? "nds emulator" : "gba emulator";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=" + Uri.encode(q))));
        } catch (Throwable ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(q))));
        }
    }

    // ---- 手柄 / 方向键导航 ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (!down) return super.dispatchKeyEvent(e);

        View focused = getCurrentFocus();

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                if (grid.getChildCount() == 0) break;
                int idx = indexOfCard(focused);
                if (idx < 0) {
                    focusCard(0);
                    return true;
                }
                int next = idx + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                if (next >= 0 && next < grid.getChildCount()) {
                    focusCard(next);
                    return true;
                }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (grid.getChildCount() == 0) break;
                {
                    int idx = indexOfCard(focused);
                    if (idx < 0) {
                        focusCard(0);
                        return true;
                    }
                    int next = idx + (code == KeyEvent.KEYCODE_DPAD_DOWN ? cols : -cols);
                    if (next >= 0 && next < grid.getChildCount()) {
                        focusCard(next);
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_DPAD_UP) {
                        searchBox.requestFocus();
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (focused instanceof GameCard) {
                    play(((GameCard) focused).game);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                if (focused instanceof GameCard) {
                    searchBox.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                cycleTab(-1);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                cycleTab(1);
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void cycleTab(int dir) {
        if (tabKeys.isEmpty()) return;
        int cur = 0;
        for (int i = 0; i < tabKeys.size(); i++) {
            if (tabKeys.get(i).equals(filter)) cur = i;
        }
        int next = (cur + dir + tabKeys.size()) % tabKeys.size();
        selectTab(tabKeys.get(next));
        if (tabsBar.getChildCount() > next) tabsBar.getChildAt(next).requestFocus();
    }

    private int indexOfCard(View v) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i) == v) return i;
        }
        return -1;
    }

    private void focusCard(int idx) {
        if (idx < 0 || idx >= cards.size()) return;
        GameCard card = cards.get(idx);
        card.requestFocus();
        grid.post(() -> {
            ScrollView sv = findViewById(R.id.grid_scroll);
            if (sv == null) return;
            int[] loc = new int[2];
            card.getLocationInWindow(loc);
            int svLocY = sv.getTop();
            int target = sv.getScrollY() + (loc[1] - svLocY) - sv.getHeight() / 2 + card.getHeight() / 2;
            ObjectAnimator.ofInt(sv, "scrollY", Math.max(0, target))
                    .setDuration(200).start();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (all.isEmpty()) rescan();
    }

    @Override
    protected void onDestroy() {
        if (currentCancel != null) currentCancel.set(true);
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}

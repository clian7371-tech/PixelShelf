package com.pixelshelf.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** 设置：游戏文件夹、模拟器绑定、桌面/自启开关（赛博风格） */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private LinearLayout foldersBox;
    private LinearLayout emuBox;
    private CheckBox homeCheck;
    private LinearLayout warnCard;

    private static final int REQ_TREE = 1003;

    private static final int BG = 0xFF05070D;
    private static final int CARD = 0xCC101826;
    private static final int FG = 0xFFEAF2FF;
    private static final int FG2 = 0xFF7C8CA8;
    private static final int CYAN = 0xFF22D3EE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        setContentView(build());
        refreshFolders();
        refreshEmulators();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs == null) return;
        // 从系统设置回来：如果尝试过设为桌面但仍不是默认桌面，说明系统不允许
        if (prefs.homeAttempted() && !HomeHelper.isDefaultHome(this)) {
            prefs.setHomeBlocked(true);
        }
        updateHomeUi();
    }

    /** 刷新桌面相关 UI：勾选状态 + 受限提示的可见性 */
    private void updateHomeUi() {
        boolean isHome = HomeHelper.isDefaultHome(this);
        if (homeCheck != null) {
            homeCheck.setText(isHome ? "已设为默认桌面" : "设为默认桌面");
            homeCheck.setChecked(isHome);
            homeCheck.setEnabled(!isHome);
        }
        if (warnCard != null) {
            boolean warn = !isHome && (prefs.homeBlocked() || !HomeHelper.canBecomeHome(this));
            warnCard.setVisibility(warn ? View.VISIBLE : View.GONE);
        }
    }

    private View build() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(BG);

        TechBackground bg = new TechBackground(this);
        outer.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        outer.addView(sc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        sc.addView(root);

        // 顶栏
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(head);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(CYAN);
        back.setPadding(0, 0, dp(12), 0);
        back.setOnClickListener(v -> finish());
        head.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(22);
        title.setTextColor(FG);
        title.setTypeface(null, Typeface.BOLD);
        title.setShadowLayer(12, 0, 0, 0x8822D3EE);
        head.addView(title);

        // ---- 桌面与启动 ----
        root.addView(section("桌面与启动"));

        boolean isHome = HomeHelper.isDefaultHome(this);

        View homeCard = card();
        LinearLayout homeRow = (LinearLayout) homeCard;
        homeCheck = check(isHome ? "已设为默认桌面" : "设为默认桌面", isHome);
        // 能否设为桌面取决于系统底层是否开放第三方 HOME，静态无法可靠判断，
        // 所以这里总是允许点击，点完回来再验证结果
        homeCheck.setEnabled(!isHome);
        homeCheck.setOnClickListener(v -> {
            prefs.setHomeAttempted(true);
            HomeHelper.openHomeSettings(this);
        });
        homeRow.addView(homeCheck);
        homeRow.addView(small("能否设为默认桌面取决于系统底层是否允许第三方应用注册为桌面（部分定制系统、"
                + "鸿蒙等会限制）。点一下会跳到系统设置，设置完回到这里会自动检测结果。"));
        root.addView(homeCard);

        warnCard = (LinearLayout) card();
        warnCard.setBackground(roundRect(0x1AFB923C, 0x55FB923C, 12));
        TextView t = new TextView(this);
        t.setText("点开系统设置后如果找不到 PixelShelf 或无法选中，说明本机系统不允许第三方应用"
                + "作为桌面（多见于厂商定制 Android 与鸿蒙系统）。\n\n"
                + "替代方案：打开下面的「开机自启动」，再在系统桌面放一个 PixelShelf 快捷方式，"
                + "效果接近，每次同样一步进入。");
        t.setTextColor(0xFFE0A24A);
        t.setTextSize(11.5f);
        t.setLineSpacing(0f, 1.35f);
        warnCard.addView(t);

        TextView auto = link("打开「自启动 / 后台管理」");
        auto.setOnClickListener(v -> {
            if (!HomeHelper.openAutostartSettings(this)) {
                Toast.makeText(this, "请在系统设置里找到本应用，允许自启动 / 后台运行",
                        Toast.LENGTH_LONG).show();
            }
        });
        warnCard.addView(auto);
        TextView retry = link("重新尝试设为桌面");
        retry.setOnClickListener(v -> {
            prefs.setHomeAttempted(true);
            HomeHelper.openHomeSettings(this);
        });
        warnCard.addView(retry);

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(8);
        root.addView(warnCard, wlp);
        updateHomeUi();

        View bootCard = card();
        LinearLayout bootRow = (LinearLayout) bootCard;
        CheckBox boot = check(getString(R.string.boot_start), prefs.bootStart());
        boot.setOnCheckedChangeListener((v, on) -> prefs.setBootStart(on));
        bootRow.addView(boot);

        TextView bootTip = small(getString(R.string.boot_start_desc));
        bootRow.addView(bootTip);
        root.addView(bootCard);

        // ---- 游戏文件夹 ----
        root.addView(section(getString(R.string.folders)));

        View folderCard = card();
        foldersBox = new LinearLayout(this);
        foldersBox.setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) folderCard).addView(foldersBox);

        TextView add = link(getString(R.string.add_folder));
        add.setOnClickListener(v -> pickFolder());
        ((LinearLayout) folderCard).addView(add);
        root.addView(folderCard);

        TextView hint = small("建议把 ROM 放在一个固定文件夹并加进来。留空则自动扫描常见目录。");
        hint.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(hint);

        // ---- 模拟器 ----
        root.addView(section(getString(R.string.emulators)));

        View emuCard = card();
        emuBox = new LinearLayout(this);
        emuBox.setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) emuCard).addView(emuBox);
        root.addView(emuCard);

        // ---- 封面与网络 ----
        root.addView(section("封面与网络"));

        View coverCard = card();
        LinearLayout coverRow = (LinearLayout) coverCard;
        CheckBox autoCover = check("自动从网络获取封面", prefs.autoCover());
        autoCover.setOnCheckedChangeListener((v, on) -> prefs.setAutoCover(on));
        coverRow.addView(autoCover);
        coverRow.addView(small("开启后会按游戏名从公开图库下载封面，只下载图片，不上传任何数据。"
                + "关闭后改用本地缓存和程序生成的占位图，应用完全离线。"));
        root.addView(coverCard);

        View sourceCard = card();
        LinearLayout sourceRow = (LinearLayout) sourceCard;
        TextView srcTitle = new TextView(this);
        srcTitle.setText("封面图源");
        srcTitle.setTextColor(FG);
        srcTitle.setTextSize(13f);
        sourceRow.addView(srcTitle);

        final TextView srcValue = small(currentSourceText());
        srcValue.setTextColor(CYAN);
        sourceRow.addView(srcValue);

        TextView editSrc = link("更换图源…");
        editSrc.setOnClickListener(v -> editCoverSource(srcValue));
        sourceRow.addView(editSrc);

        sourceRow.addView(small("请求会串行发送并限制频率，已被缓存的游戏不会重复下载。"
                + "自定义图源需保持与默认图源相同的目录结构"
                + "（<平台目录>/Named_Boxarts/<游戏名>.png）。"));
        root.addView(sourceCard);

        // ---- 关于与合规 ----
        root.addView(section("关于与声明"));

        View aboutCard = card();
        LinearLayout about = (LinearLayout) aboutCard;

        TextView appLine = new TextView(this);
        appLine.setText("PixelShelf " + versionName());
        appLine.setTextColor(FG);
        appLine.setTextSize(13f);
        appLine.setTypeface(null, Typeface.BOLD);
        about.addView(appLine);

        about.addView(small(
                "本应用是一个游戏库前端（启动器），不包含任何游戏 ROM、模拟器程序或 BIOS 文件。\n"
                        + "需要用户自行安装各平台的模拟器，并自备合法获得的 ROM。\n\n"
                        + "与任天堂、索尼、世嘉等权利人均无关联，相关名称仅用于说明兼容性。\n\n"
                        + "封面图片来自第三方公开图库（libretro thumbnails），版权归各自权利人所有，"
                        + "本应用仅按需下载并显示，不打包分发。如权利人要求，可关闭自动获取。\n\n"
                        + "本应用不含广告、统计埋点、账号系统，不收集、不上传任何个人数据。"));

        TextView lic = link("开源许可：MIT License");
        lic.setOnClickListener(v -> showLicense());
        about.addView(lic);
        root.addView(aboutCard);

        return outer;
    }

    private String currentSourceText() {
        String custom = prefs.coverBase();
        if (custom == null || custom.trim().isEmpty()) {
            return "默认（公开缩略图库）";
        }
        return custom.trim();
    }

    /** 编辑封面图源：只允许 https，避免明文流量与中间人篡改 */
    private void editCoverSource(TextView display) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("https://example.com/thumbnails/");
        String cur = prefs.coverBase();
        input.setText(cur == null ? "" : cur);
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
                .setTitle("封面图源")
                .setMessage("留空使用默认图源。自定义地址必须以 https:// 开头，"
                        + "并保持 <平台目录>/Named_Boxarts/<游戏名>.png 的结构。\n\n"
                        + "提示：默认图源是他人免费提供的公共服务，请勿滥用；"
                        + "批量使用建议自建镜像。")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.isEmpty() && !v.toLowerCase(java.util.Locale.US).startsWith("https://")) {
                        Toast.makeText(this, "只支持 https:// 开头的地址", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.setCoverBase(v);
                    display.setText(currentSourceText());
                })
                .setNeutralButton("恢复默认", (d, w) -> {
                    prefs.setCoverBase("");
                    display.setText(currentSourceText());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    private void showLicense() {
        String text = "MIT License\n\n"
                + "Copyright (c) 2026 clian7371-tech\n\n"
                + "Permission is hereby granted, free of charge, to any person obtaining a copy\n"
                + "of this software and associated documentation files (the \"Software\"), to deal\n"
                + "in the Software without restriction, including without limitation the rights\n"
                + "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n"
                + "copies of the Software, and to permit persons to whom the Software is\n"
                + "furnished to do so, subject to the following conditions:\n\n"
                + "The above copyright notice and this permission notice shall be included in all\n"
                + "copies or substantial portions of the Software.\n\n"
                + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n"
                + "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n"
                + "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n"
                + "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n"
                + "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n"
                + "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n"
                + "SOFTWARE.";
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setTextColor(FG2);
        tv.setPadding(dp(18), dp(12), dp(18), dp(12));
        sv.addView(tv);
        new android.app.AlertDialog.Builder(this)
                .setTitle("MIT License")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void refreshFolders() {
        if (foldersBox == null) return;
        foldersBox.removeAllViews();
        List<String> list = prefs.folders();
        if (list.isEmpty()) {
            foldersBox.addView(small(getString(R.string.no_folder_selected)));
            return;
        }
        for (String f : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));

            TextView t = new TextView(this);
            t.setText(f);
            t.setTextSize(12);
            t.setTextColor(FG2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            t.setLayoutParams(lp);
            row.addView(t);

            TextView rm = new TextView(this);
            rm.setText("移除");
            rm.setTextSize(12);
            rm.setTextColor(0xFFE06C6C);
            rm.setPadding(dp(10), 0, 0, 0);
            rm.setOnClickListener(v -> {
                prefs.removeFolder(f);
                refreshFolders();
            });
            row.addView(rm);
            foldersBox.addView(row);
        }
    }

    private void refreshEmulators() {
        if (emuBox == null) return;
        emuBox.removeAllViews();
        boolean first = true;
        for (Emulators.Def def : Emulators.ALL) {
            if (!first) {
                View line = new View(this);
                line.setBackgroundColor(0x14FFFFFF);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                llp.topMargin = dp(8);
                llp.bottomMargin = dp(8);
                emuBox.addView(line, llp);
            }
            first = false;

            List<String> installed = Launcher.installedFor(this, def.platform);
            String saved = prefs.emulator(def.platform);

            TextView label = new TextView(this);
            label.setText(def.label + "　" + (installed.isEmpty()
                    ? "未检测到模拟器" : "已检测到 " + installed.size() + " 个"));
            label.setTextSize(12);
            label.setTextColor(installed.isEmpty() ? 0xFFE06C6C : FG);
            emuBox.addView(label);

            if (!installed.isEmpty()) {
                for (String pkg : installed) {
                    boolean sel = pkg.equals(saved)
                            || (saved.isEmpty() && pkg.equals(installed.get(0)));
                    TextView opt = new TextView(this);
                    opt.setText((sel ? "● " : "○ ") + Launcher.appLabel(this, pkg));
                    opt.setTextSize(11.5f);
                    opt.setTextColor(sel ? CYAN : FG2);
                    opt.setPadding(dp(10), dp(6), 0, dp(6));
                    opt.setOnClickListener(v -> {
                        prefs.setEmulator(def.platform, pkg);
                        refreshEmulators();
                    });
                    emuBox.addView(opt);
                }
            }
        }
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE || res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        String path = pathFromTreeUri(uri);
        if (path == null) {
            Toast.makeText(this, "无法解析该目录，请换一个文件夹", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.addFolder(path);
        refreshFolders();
    }

    /** content://…/tree/primary%3AROMs → /storage/emulated/0/ROMs */
    private String pathFromTreeUri(Uri uri) {
        try {
            String id = uri.getLastPathSegment();
            if (id == null) return null;
            int colon = id.indexOf(':');
            if (colon < 0) return null;
            String vol = id.substring(0, colon);
            String rel = id.substring(colon + 1);
            String base;
            if (vol.equalsIgnoreCase("primary")) {
                base = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
            } else if (vol.equalsIgnoreCase("home")) {
                base = android.os.Environment.getExternalStoragePublicDirectory("").getAbsolutePath();
            } else {
                base = "/storage/" + vol;
            }
            File f = rel.isEmpty() ? new File(base) : new File(base, rel);
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int c = e.getKeyCode();
            if (c == android.view.KeyEvent.KEYCODE_BUTTON_B) {
                finish();
                return true;
            }
            if (c == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                // 已经在最上面时，再按上直接退出，避免焦点卡在顶部
                View f = getCurrentFocus();
                if (f instanceof CheckBox) {
                    finish();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- 视觉零件 ----

    private View card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundRect(CARD, 0x22FFFFFF, 12));
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        box.setLayoutParams(lp);
        return box;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(1, stroke);
        return d;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(FG);
        cb.setTextSize(13);
        cb.setChecked(checked);
        cb.setPadding(0, dp(4), 0, dp(4));
        int[][] states = {{android.R.attr.state_checked}, {}};
        cb.setButtonTintList(new ColorStateList(states, new int[]{CYAN, 0xFF55637A}));
        return cb;
    }

    private TextView link(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12.5f);
        t.setTextColor(CYAN);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private View section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(CYAN);
        t.setTypeface(null, Typeface.BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(2), dp(26), 0, dp(2));
        return t;
    }

    private TextView small(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(FG2);
        t.setLineSpacing(0f, 1.3f);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}

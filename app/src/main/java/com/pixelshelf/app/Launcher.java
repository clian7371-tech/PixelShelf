package com.pixelshelf.app;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** 启动模拟器打开一个 ROM */
public final class Launcher {

    /** 返回实际使用的模拟器包名；失败时抛异常 */
    public static String launch(Context ctx, Game game) {
        Emulators.Def def = Emulators.byPlatform(game.platform);
        if (def == null) throw new IllegalStateException("unknown platform");

        String preferred = new Prefs(ctx).emulator(game.platform);
        File file = new File(game.path);
        Uri uri = Uri.fromFile(file);

        // PPSSPP 不解析 file:// URI，要用它自己的 Args extra 传路径
        if (Emulators.PLATFORM_PSP.equals(game.platform)) {
            String pkg = preferred.isEmpty() ? firstInstalled(ctx, def) : preferred;
            if (pkg != null && tryPpssppArgs(ctx, pkg, game.path)) return pkg;
        }

        // 1) 显式指定的模拟器
        if (!preferred.isEmpty()) {
            if (tryPackage(ctx, preferred, uri, game.platform)) return preferred;
        }

        // 2) 系统里能打开这个 file:// 的应用（按功能探测，最稳）
        String byIntent = findByIntent(ctx, uri, game.platform);
        if (byIntent != null) return byIntent;

        // 3) 按已知包名逐个尝试
        for (String pkg : def.packages) {
            if (tryPackage(ctx, pkg, uri, game.platform)) return pkg;
        }

        // 4) 平台没有可处理的 App，抛给上层提示安装
        throw new ActivityNotFoundException(def.label);
    }

    private static String firstInstalled(Context ctx, Emulators.Def def) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : def.packages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** PPSSPP：显式 Activity + org.ppsspp.ppsspp.Args extra（它不支持 file:// URI） */
    private static boolean tryPpssppArgs(Context ctx, String pkg, String path) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(pkg, pkg + ".PpssppActivity"));
        i.putExtra("org.ppsspp.ppsspp.Args", path);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (i.resolveActivity(ctx.getPackageManager()) == null) return false;
            ctx.startActivity(i);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryPackage(Context ctx, String pkg, Uri uri, String platform) {
        // PPSSPP 收到 file:// 会报「文件不存在」，只能走 Args extra
        if (pkg != null && pkg.startsWith("org.ppsspp.")) return false;
        Intent i = buildIntent(uri, platform);
        i.setPackage(pkg);
        try {
            if (i.resolveActivity(ctx.getPackageManager()) != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return true;
            }
        } catch (Throwable ignored) {
        }
        // 某些模拟器只暴露 LAUNCHER，没有 VIEW：用显式组件兜底，并传 ROM 路径
        return tryExplicitComponent(ctx, pkg, uri, platform);
    }

    private static boolean tryExplicitComponent(Context ctx, String pkg, Uri uri, String platform) {
        PackageManager pm = ctx.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        ComponentName cn = launch.getComponent();
        if (cn == null) return false;

        Intent i = buildIntent(uri, platform);
        i.setComponent(cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (Throwable ignored) {
        }

        // 仍失败：只打开模拟器本身（用户再从模拟器里选游戏）
        Intent plain = pm.getLaunchIntentForPackage(pkg);
        if (plain == null) return false;
        plain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(plain);
            Toast.makeText(ctx, R.string.opened_emulator_only, Toast.LENGTH_LONG).show();
            return true;
        } catch (Throwable ignored2) {
            return false;
        }
    }

    private static String findByIntent(Context ctx, Uri uri, String platform) {
        Intent i = buildIntent(uri, platform);
        PackageManager pm = ctx.getPackageManager();
        try {
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(ctx.getPackageName())) continue;
                if (pkg.startsWith("org.ppsspp.")) continue;
                Intent go = new Intent(i);
                go.setPackage(pkg);
                go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    ctx.startActivity(go);
                    return pkg;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Intent buildIntent(Uri uri, String platform) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mimeOf(platform));
        return i;
    }

    private static String mimeOf(String platform) {
        switch (platform) {
            case Emulators.PLATFORM_GBA:
                return "application/x-gba-rom";
            case Emulators.PLATFORM_NDS:
                return "application/x-nds-rom";
            case Emulators.PLATFORM_PSP:
                return "application/x-psp-rom";
            default:
                return "*/*";
        }
    }

    /** 设备上某平台可用的模拟器包名（用于设置页下拉） */
    public static java.util.List<String> installedFor(Context ctx, String platform) {
        Emulators.Def def = Emulators.byPlatform(platform);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (def == null) return out;
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : def.packages) {
            try {
                pm.getPackageInfo(pkg, 0);
                out.add(pkg);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    public static String appLabel(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private Launcher() {
    }
}

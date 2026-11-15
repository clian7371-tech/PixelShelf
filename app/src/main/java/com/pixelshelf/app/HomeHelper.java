package com.pixelshelf.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** 桌面能力检测与引导 */
public final class HomeHelper {

    /** 系统是否允许第三方应用当默认桌面（华为/荣耀 EMUI 会拒绝） */
    public static boolean canBecomeHome(Context ctx) {
        String brand = (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND).toLowerCase();
        // 华为 / 荣耀：系统移除了默认桌面选择入口，第三方应用无法成为桌面
        if (brand.contains("huawei") || brand.contains("honor")) return false;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            return ctx.getPackageManager()
                    .queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).size() > 1;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 当前默认桌面是不是我们自己 */
    public static boolean isDefaultHome(Context ctx) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            ComponentName cn = i.resolveActivity(ctx.getPackageManager());
            return cn != null && ctx.getPackageName().equals(cn.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 打开系统的「默认桌面」选择界面，不同 ROM 入口不同 */
    public static void openHomeSettings(Context ctx) {
        String[] actions = {
                Settings.ACTION_HOME_SETTINGS,          // 原生 / 大多数
                "android.settings.MANAGE_HOME_SETTINGS",
                Settings.ACTION_SETTINGS,
                "com.huawei.systemmanager.LAUNCHER_SETTINGS",
        };
        for (String a : actions) {
            try {
                Intent i = new Intent(a);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(ctx.getPackageManager()) != null) {
                    ctx.startActivity(i);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    /** 打开本应用的电池/自启动管理（华为的「应用启动管理」） */
    public static boolean openAutostartSettings(Context ctx) {
        String pkg = ctx.getPackageName();
        String[][] cands = {
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
        };
        for (String[] c : cands) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(c[0], c[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(ctx.getPackageManager()) != null) {
                    ctx.startActivity(i);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private HomeHelper() {
    }
}

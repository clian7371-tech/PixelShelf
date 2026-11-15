package com.pixelshelf.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import java.util.List;

/**
 * 桌面能力检测与引导。
 *
 * 能否设为默认桌面，取决于系统**是否允许第三方应用注册为 HOME**，
 * 而不是厂商或版本号——同一厂商不同 ROM 版本行为都可能不同。
 * 所以这里只做能力探测 + 运行时验证，不按品牌写死。
 */
public final class HomeHelper {

    /** 系统是否有「默认桌面」设置入口 */
    public static boolean homeSettingsAvailable(Context ctx) {
        String[] actions = {
                Settings.ACTION_HOME_SETTINGS,
                "android.settings.MANAGE_HOME_SETTINGS",
        };
        for (String a : actions) {
            try {
                Intent i = new Intent(a);
                if (i.resolveActivity(ctx.getPackageManager()) != null) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** 本应用是否出现在系统的 HOME 候选里 */
    public static boolean isHomeCandidate(Context ctx) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = ctx.getPackageManager()
                    .queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo ri : list) {
                if (ri.activityInfo != null
                        && ctx.getPackageName().equals(ri.activityInfo.packageName)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 系统里可选的桌面数量（含本应用） */
    public static int homeCandidateCount(Context ctx) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            return ctx.getPackageManager()
                    .queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 静态判断是否可能成为默认桌面。
     * 真正的限制只能靠「设置后回来验证」，见 isDefaultHome()。
     */
    public static boolean canBecomeHome(Context ctx) {
        if (isDefaultHome(ctx)) return true;
        if (!isHomeCandidate(ctx)) return false;
        // 没有桌面设置入口，且可选桌面只有一个 → 系统只认自带桌面
        if (!homeSettingsAvailable(ctx) && homeCandidateCount(ctx) <= 1) return false;
        return true;
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
        openAppDetails(ctx);
    }

    /**
     * 打开本应用的「自启动 / 后台管理」页面。
     * 各系统的入口各不相同，下面只是常见候选；都失败时退回到应用详情页，
     * 让用户能在系统里找到「电池优化」「自启动」等相关开关。
     */
    public static boolean openAutostartSettings(Context ctx) {
        String pkg = ctx.getPackageName();
        String[][] cands = {
                // 常见系统的启动管理页
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
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
        return openAppDetails(ctx);
    }

    /** 兜底：打开系统里本应用的详情页 */
    public static boolean openAppDetails(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private HomeHelper() {
    }
}

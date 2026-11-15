package com.pixelshelf.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启：仅在用户开启开关时拉起游戏墙 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        if (!new Prefs(ctx).bootStart()) return;

        Intent go = new Intent(ctx, MainActivity.class);
        go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            ctx.startActivity(go);
        } catch (Throwable ignored) {
            // 后台启动 Activity 在部分 ROM 上被限制时静默失败
        }
    }
}

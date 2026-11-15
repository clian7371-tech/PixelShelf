package com.pixelshelf.app;

import android.app.Application;
import android.os.StrictMode;

/** 关闭 file:// 的 StrictMode 检查：模拟器需要 file 路径才能打开 ROM */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

        // 把版本号交给封面下载器，用于 User-Agent 标识请求来源
        try {
            CoverFetcher.setAppVersion(getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName);
        } catch (Throwable ignored) {
        }
    }
}

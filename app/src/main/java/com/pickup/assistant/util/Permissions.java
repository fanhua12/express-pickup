package com.pickup.assistant.util;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** 权限状态检测与设置页跳转(短信/通知/通知监听/电池白名单/厂商自启动) */
public final class Permissions {

    private Permissions() {}

    public static boolean hasSms(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasNotify(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    /** 通知使用权限(监听通知)是否已授予本应用 */
    public static boolean hasListener(Context ctx) {
        String flat = Settings.Secure.getString(
                ctx.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(ctx.getPackageName());
    }

    public static boolean isIgnoringBattery(Context ctx) {
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    public static Intent appDetail(Context ctx) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + ctx.getPackageName()));
        return i;
    }

    public static Intent listenerSettings() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }

    /** 加入电池优化白名单 */
    public static Intent batteryWhitelist(Context ctx) {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        i.setData(Uri.parse("package:" + ctx.getPackageName()));
        return i;
    }

    /** 厂商自启动管理页, 找不到则回退应用详情页 */
    public static Intent autoStart(Context ctx) {
        List<ComponentName> candidates = new ArrayList<>();
        // 小米/红米
        candidates.add(new ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        // 华为
        candidates.add(new ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
        candidates.add(new ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"));
        // OPPO/realme/一加
        candidates.add(new ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"));
        candidates.add(new ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
        candidates.add(new ComponentName("com.oplus.safecenter",
                "com.oplus.safecenter.startupapp.StartupAppListActivity"));
        // vivo/iQOO
        candidates.add(new ComponentName("com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
        candidates.add(new ComponentName("com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
        // 三星
        candidates.add(new ComponentName("com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"));

        for (ComponentName cn : candidates) {
            Intent i = new Intent();
            i.setComponent(cn);
            if (ctx.getPackageManager().resolveActivity(i, 0) != null) return i;
        }
        return appDetail(ctx);
    }
}

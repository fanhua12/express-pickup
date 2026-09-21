package com.pickup.assistant.service;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

/**
 * 通知监听: 抓取菜鸟/微信/各快递App通知里的取件码
 * 组合 title/text/bigText/subText 多字段扫描, 适配不同ROM落字段差异
 */
public class NotifyListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String pkg = sbn.getPackageName();
        // 跳过本应用通知, 避免循环
        if (getPackageName().equals(pkg)) return;

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        if (extras == null) return;

        String title = str(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(extras.getCharSequence(Notification.EXTRA_TEXT));
        String bigText = str(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String subText = str(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));

        StringBuilder pool = new StringBuilder();
        append(pool, title);
        append(pool, text);
        append(pool, bigText);
        append(pool, subText);
        if (pool.length() == 0) return;

        Ingestor.handle(this, pool.toString(), "notification", appLabel(this, pkg));
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private static void append(StringBuilder sb, String s) {
        if (!TextUtils.isEmpty(s)) sb.append(s).append(' ');
    }

    private static String appLabel(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}

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
 * 盯着通知栏，从菜鸟/微信/各快递 App 的通知里抠取件码
 * 各家 ROM 把文字放的字段不一样，所以 title/text/bigText/subText 都扫一遍
 * 折叠通知（InboxStyle）的正文在 EXTRA_TEXT_LINES，得逐行解析，不然会漏
 */
public class NotifyListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String pkg = sbn.getPackageName();
        // 自己发的通知别再抓，会循环
        if (getPackageName().equals(pkg)) return;

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        if (extras == null) return;

        String title = str(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(extras.getCharSequence(Notification.EXTRA_TEXT));
        String bigText = str(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String subText = str(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));

        String app = appLabel(this, pkg);

        StringBuilder pool = new StringBuilder();
        append(pool, title);
        append(pool, text);
        append(pool, bigText);
        append(pool, subText);
        if (pool.length() > 0) Ingestor.handle(this, pool.toString(), "notification", app, pkg);

        // 折叠通知的正文一行行存在 EXTRA_TEXT_LINES 里，逐行解析，不然好几个码只抓到一个
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                String s = str(line);
                if (!TextUtils.isEmpty(s)) Ingestor.handle(this, s, "notification", app, pkg);
            }
        }
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

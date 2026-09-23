package com.pickup.assistant.notify;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pickup.assistant.App;
import com.pickup.assistant.MainActivity;
import com.pickup.assistant.R;
import com.pickup.assistant.model.PickupItem;
import com.pickup.assistant.service.QueryActivity;

/** 本地通知: 新取件码(大字) + 保活常驻。全部本地, 不联网 */
public final class LocalNotifier {

    private LocalNotifier() {}

    public static void notifyNew(Context ctx, PickupItem it) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) it.id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String where = TextUtils.isEmpty(it.station)
                ? (TextUtils.isEmpty(it.carrier) ? "快递已到" : it.carrier + " 已到")
                : (TextUtils.isEmpty(it.carrier) ? it.station : it.carrier + " · " + it.station);

        Notification n = new NotificationCompat.Builder(ctx, App.CH_NEW)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(0xFF3D7EFF)
                .setContentTitle("取件码 " + it.code)
                .setContentText(where + "，点我查看")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("取件码: " + it.code + "\n" + where))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVibrate(new long[]{0, 200, 100, 200})
                .build();
        try {
            NotificationManagerCompat.from(ctx).notify((int) (it.id % Integer.MAX_VALUE), n);
        } catch (SecurityException ignored) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时静默
        }
    }

    /** 到件但无取件码: 高优横幅, 点按/按钮均走"一键查询" */
    public static void notifyArrival(Context ctx, PickupItem it) {
        Intent query = new Intent(ctx, QueryActivity.class);
        query.putExtra(QueryActivity.EXTRA_PKG, it.sourcePkg);
        query.putExtra(QueryActivity.EXTRA_CARRIER, it.carrier);
        query.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int reqCode = (int) (1_000_000L + it.id);
        PendingIntent pi = PendingIntent.getActivity(ctx, reqCode, query,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String appName = TextUtils.isEmpty(it.sourceApp) ? "快递 App" : it.sourceApp;
        String where = TextUtils.isEmpty(it.station)
                ? (TextUtils.isEmpty(it.carrier) ? "快递已到" : it.carrier + " 已到")
                : (TextUtils.isEmpty(it.carrier) ? it.station : it.carrier + " · " + it.station);

        Notification n = new NotificationCompat.Builder(ctx, App.CH_NEW)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(0xFFFF7A1A)
                .setContentTitle("快递到了（通知里没有取件码）")
                .setContentText(where + "，点我打开" + appName + "一键查询")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(where + "\n通知里没有取件码\n点我打开" + appName + "一键查询，在对方 App 内查看取件码"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(0, "一键查询", pi)
                .setVibrate(new long[]{0, 220, 120, 220})
                .build();
        try {
            NotificationManagerCompat.from(ctx).notify(reqCode, n);
        } catch (SecurityException ignored) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时静默
        }
    }

    public static Notification keepAlive(Context ctx) {
        return new NotificationCompat.Builder(ctx, App.CH_KEEP)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("取件码助手运行中")
                .setContentText("正在监听快递短信与通知")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }
}

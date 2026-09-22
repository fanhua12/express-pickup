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

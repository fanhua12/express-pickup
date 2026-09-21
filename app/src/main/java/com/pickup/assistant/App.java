package com.pickup.assistant;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {

    public static final String CH_NEW = "new_pickup";      // 新取件码(高优先级)
    public static final String CH_KEEP = "keep_alive";     // 常驻保活(低优先级)

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);

        NotificationChannel chNew = new NotificationChannel(
                CH_NEW, "新取件码提醒", NotificationManager.IMPORTANCE_HIGH);
        chNew.setDescription("快递到达并识别到取件码时提醒");
        chNew.enableVibration(true);

        NotificationChannel chKeep = new NotificationChannel(
                CH_KEEP, "后台运行", NotificationManager.IMPORTANCE_MIN);
        chKeep.setDescription("保持取件码监听服务在后台运行");
        chKeep.setShowBadge(false);

        nm.createNotificationChannel(chNew);
        nm.createNotificationChannel(chKeep);
    }
}

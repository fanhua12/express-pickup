package com.pickup.assistant.service;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.pickup.assistant.notify.LocalNotifier;

/** 常驻前台服务: 降低被系统杀掉的概率, 保证短信/通知监听持续 */
public class KeepAliveService extends Service {

    private static final int NOTI_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        return START_STICKY;   // 被杀后系统尽量重建
    }

    @SuppressWarnings("foregroundServiceType")
    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 必须声明前台服务类型
            startForeground(NOTI_ID, LocalNotifier.keepAlive(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTI_ID, LocalNotifier.keepAlive(this));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

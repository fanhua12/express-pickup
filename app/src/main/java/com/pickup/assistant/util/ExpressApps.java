package com.pickup.assistant.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 一键查询: 按通知来源包名拉起对应 App
 * 取件码内部页多为未导出的私有页面, 无法保证直达, 因此只负责拉起 App,
 * 用户进去后自行查看取件码
 */
public final class ExpressApps {

    public static final String PKG_CAINIAO = "com.cainiao.wireless";
    public static final String PKG_TAOBAO = "com.taobao.taobao";
    public static final String PKG_JD = "com.jingdong.app.mall";
    public static final String PKG_PDD = "com.xunmeng.pinduoduo";
    public static final String PKG_SF = "com.sf.activity";

    /** 无来源包名(如短信)时, 按快递公司猜一个最可能查到的 App */
    private static final Map<String, String> CARRIER_PKG = new HashMap<>();

    static {
        CARRIER_PKG.put("菜鸟", PKG_CAINIAO);
        CARRIER_PKG.put("丹鸟", PKG_CAINIAO);
        CARRIER_PKG.put("天猫", PKG_TAOBAO);
        CARRIER_PKG.put("京东", PKG_JD);
        CARRIER_PKG.put("顺丰", PKG_SF);
    }

    private ExpressApps() {}

    /** 可启动: 必须是带启动入口的 App(系统包如 com.android.shell 已安装但无法启动) */
    private static boolean launchable(Context ctx, String pkg) {
        if (pkg == null) return false;
        try {
            return ctx.getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String resolvePkg(Context ctx, String sourcePkg, String carrier) {
        if (launchable(ctx, sourcePkg)) return sourcePkg;
        if (carrier != null) {
            String p = CARRIER_PKG.get(carrier);
            if (launchable(ctx, p)) return p;
        }
        // 驿站/快递柜类通知大多能在菜鸟里查到
        if (launchable(ctx, PKG_CAINIAO)) return PKG_CAINIAO;
        return null;
    }

    /** 拉起查询 App; 返回 false 表示没有可启动的对应 App */
    public static boolean launch(Context ctx, String sourcePkg, String carrier) {
        String pkg = resolvePkg(ctx, sourcePkg, carrier);
        if (pkg == null) return false;
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(i);
        return true;
    }

    public static String appLabel(Context ctx, String pkg) {
        if (pkg == null) return "";
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}

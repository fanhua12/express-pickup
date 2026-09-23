package com.pickup.assistant.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 一键查询就是按通知来源的包名把对应 App 拉起来
 * 取件码那个页面大多是没导出的私有页，没法直达，所以只能把 App 拉到前台，
 * 进去之后用户自己找
 */
public final class ExpressApps {

    public static final String PKG_CAINIAO = "com.cainiao.wireless";
    public static final String PKG_TAOBAO = "com.taobao.taobao";
    public static final String PKG_JD = "com.jingdong.app.mall";
    public static final String PKG_PDD = "com.xunmeng.pinduoduo";
    public static final String PKG_SF = "com.sf.activity";

    /** 短信这种没有来源包名的情况，就按快递公司猜个最可能查到的 App */
    private static final Map<String, String> CARRIER_PKG = new HashMap<>();

    static {
        CARRIER_PKG.put("菜鸟", PKG_CAINIAO);
        CARRIER_PKG.put("丹鸟", PKG_CAINIAO);
        CARRIER_PKG.put("天猫", PKG_TAOBAO);
        CARRIER_PKG.put("京东", PKG_JD);
        CARRIER_PKG.put("顺丰", PKG_SF);
    }

    private ExpressApps() {}

    /** 得能真的启动才行。像 com.android.shell 装了但没启动入口，不算 */
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
        // 驿站、快递柜的通知，基本都能在菜鸟里查到
        if (launchable(ctx, PKG_CAINIAO)) return PKG_CAINIAO;
        return null;
    }

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

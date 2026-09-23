package com.pickup.assistant.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.pickup.assistant.util.ExpressApps;

/**
 * 通知"一键查询"跳板: 无界面, 负责拉起来源快递 App
 */
public class QueryActivity extends Activity {

    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_CARRIER = "carrier";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent data = getIntent();
        String pkg = data == null ? null : data.getStringExtra(EXTRA_PKG);
        String carrier = data == null ? null : data.getStringExtra(EXTRA_CARRIER);
        boolean ok = ExpressApps.launch(getApplicationContext(), pkg, carrier);
        if (!ok) {
            Toast.makeText(this, "没找到对应的快递 App, 可先手动安装或在 App 内补录取件码",
                    Toast.LENGTH_LONG).show();
            try {
                Intent home = new Intent(this, com.pickup.assistant.MainActivity.class);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
            } catch (Exception ignored) {
            }
        }
        finish();
    }
}

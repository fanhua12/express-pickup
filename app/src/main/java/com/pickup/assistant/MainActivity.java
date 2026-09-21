package com.pickup.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pickup.assistant.db.PickupDb;
import com.pickup.assistant.model.PickupItem;
import com.pickup.assistant.service.KeepAliveService;
import com.pickup.assistant.ui.PickupAdapter;
import com.pickup.assistant.util.Permissions;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_SMS = 11;
    private static final int REQ_NOTIFY = 12;

    private RecyclerView list;
    private TextView empty, tabPending, tabDone;
    private View permCard;
    private LinearLayout permContainer;
    private PickupAdapter adapter;

    private int currentStatus = PickupItem.STATUS_PENDING;
    private boolean forceShowPerm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        tabPending = findViewById(R.id.tab_pending);
        tabDone = findViewById(R.id.tab_done);
        permCard = findViewById(R.id.perm_card);
        permContainer = findViewById(R.id.perm_container);

        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PickupAdapter(this::onToggle);
        list.setAdapter(adapter);

        tabPending.setOnClickListener(v -> switchTab(PickupItem.STATUS_PENDING));
        tabDone.setOnClickListener(v -> switchTab(PickupItem.STATUS_DONE));
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            forceShowPerm = true;
            buildPermCard();
        });

        startKeepAlive();
        requestRuntimePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        forceShowPerm = false;
        buildPermCard();
        refresh();
        startKeepAlive();
    }

    private void switchTab(int status) {
        currentStatus = status;
        boolean pending = status == PickupItem.STATUS_PENDING;
        tabPending.setTextColor(ContextCompat.getColor(this,
                pending ? R.color.primary : R.color.text_sub));
        tabPending.setTypeface(null, pending ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tabDone.setTextColor(ContextCompat.getColor(this,
                pending ? R.color.text_sub : R.color.primary));
        tabDone.setTypeface(null, pending ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        refresh();
    }

    private void refresh() {
        List<PickupItem> items = PickupDb.get(this).list(currentStatus);
        adapter.submit(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(currentStatus == PickupItem.STATUS_PENDING
                ? "还没有待取的取件码\n收到快递短信或通知会自动出现在这里"
                : "还没有已取记录");
    }

    private void onToggle(PickupItem it) {
        int next = it.status == PickupItem.STATUS_PENDING
                ? PickupItem.STATUS_DONE : PickupItem.STATUS_PENDING;
        PickupDb.get(this).setStatus(it.id, next);
        Toast.makeText(this,
                next == PickupItem.STATUS_DONE ? "已标记为已取" : "已恢复为待取",
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    // ==================== 保活服务 ====================

    private void startKeepAlive() {
        Intent svc = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    // ==================== 运行时权限 ====================

    private void requestRuntimePermissions() {
        java.util.List<String> need = new java.util.ArrayList<>();
        if (!Permissions.hasSms(this)) {
            need.add(Manifest.permission.RECEIVE_SMS);
            need.add(Manifest.permission.READ_SMS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_SMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        buildPermCard();
    }

    // ==================== 权限引导卡 ====================

    private void buildPermCard() {
        permContainer.removeAllViews();
        addPermRow("① 短信权限", "读取快递到达短信里的取件码",
                Permissions.hasSms(this), v -> requestRuntimePermissions());
        addPermRow("② 通知权限", "有新取件码时在通知栏提醒",
                Permissions.hasNotify(this), v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
                    } else {
                        startActivity(Permissions.appDetail(this));
                    }
                });
        addPermRow("③ 通知使用权限", "抓取菜鸟、微信、快递App的通知(必开)",
                Permissions.hasListener(this), v -> {
                    try { startActivity(Permissions.listenerSettings()); }
                    catch (Exception e) { toast("请手动到 设置-通知使用权 开启"); }
                });
        addPermRow("④ 电池优化白名单", "允许后台运行，防止被系统杀掉",
                Permissions.isIgnoringBattery(this), v -> {
                    try { startActivity(Permissions.batteryWhitelist(this)); }
                    catch (Exception e) { startActivity(Permissions.appDetail(this)); }
                });
        // 自启动无标准API, 始终提示手动允许
        addPermRow("⑤ 自启动管理", "在厂商设置里允许本应用自启动、后台运行",
                false, v -> {
                    try { startActivity(Permissions.autoStart(this)); }
                    catch (Exception e) { startActivity(Permissions.appDetail(this)); }
                });

        boolean allCoreGranted = Permissions.hasSms(this)
                && Permissions.hasNotify(this)
                && Permissions.hasListener(this)
                && Permissions.isIgnoringBattery(this);
        permCard.setVisibility((allCoreGranted && !forceShowPerm) ? View.GONE : View.VISIBLE);
    }

    private void addPermRow(CharSequence title, String desc, boolean granted, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(10);
        row.setLayoutParams(rp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(tp);

        TextView t = new TextView(this);
        t.setText(granted ? title + "  ✓" : title);
        t.setTextColor(ContextCompat.getColor(this,
                granted ? R.color.text_sub : R.color.text_main));
        t.setTextSize(14f);
        t.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(ContextCompat.getColor(this, R.color.text_sub));
        d.setTextSize(12f);

        texts.addView(t);
        texts.addView(d);

        MaterialButton btn = new MaterialButton(this);
        btn.setText(granted ? "已开启" : "去开启");
        btn.setEnabled(!granted);
        btn.setTextSize(12f);
        btn.setMinHeight(dp(36));
        btn.setMinimumHeight(dp(36));
        btn.setPadding(dp(14), 0, dp(14), 0);
        btn.setOnClickListener(onClick);

        row.addView(texts);
        row.addView(btn);
        permContainer.addView(row);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}

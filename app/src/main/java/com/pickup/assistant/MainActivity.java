package com.pickup.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pickup.assistant.db.PickupDb;
import com.pickup.assistant.model.PickupItem;
import com.pickup.assistant.ocr.OcrManager;
import com.pickup.assistant.service.Ingestor;
import com.pickup.assistant.service.KeepAliveService;
import com.pickup.assistant.ui.PickupAdapter;
import com.pickup.assistant.ui.RulesActivity;
import com.pickup.assistant.util.ExpressApps;
import com.pickup.assistant.util.Permissions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_SMS = 11;
    private static final int REQ_NOTIFY = 12;
    private static final String KEY_AUTOSTART = "autostart_confirmed";

    private RecyclerView list;
    private View emptyView, permCard;
    private TextView emptyTitle, emptySub, tabPending, tabDone, tabRecycle, statCount, permTitle;
    private LinearLayout permContainer;
    private PickupAdapter adapter;
    private PickupDb.Listener dbListener;

    private int currentStatus = PickupItem.STATUS_PENDING;
    private boolean forceShowPerm = false;
    private boolean pendingAutostart = false;
    private AlertDialog ocrProgress;

    /** 选截图: 走系统相册/文件选择器, 不需要存储权限, 支持一次多选 */
    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) runOcr(uris);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty);
        emptyTitle = findViewById(R.id.txt_empty_title);
        emptySub = findViewById(R.id.txt_empty_sub);
        tabPending = findViewById(R.id.tab_pending);
        tabDone = findViewById(R.id.tab_done);
        tabRecycle = findViewById(R.id.tab_recycle);
        statCount = findViewById(R.id.txt_stat_count);
        permCard = findViewById(R.id.perm_card);
        permContainer = findViewById(R.id.perm_container);
        permTitle = findViewById(R.id.txt_perm_title);

        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PickupAdapter(new PickupAdapter.OnAction() {
            @Override
            public void onToggle(PickupItem it) {
                MainActivity.this.onToggle(it);
            }

            @Override
            public void onDelete(PickupItem it) {
                MainActivity.this.onDelete(it);
            }

            @Override
            public void onQuery(PickupItem it) {
                MainActivity.this.onQuery(it);
            }
        });
        list.setAdapter(adapter);

        // 数据库有变化(如前台收到新取件码)时实时刷新列表
        dbListener = () -> runOnUiThread(this::refresh);
        PickupDb.get(this).addListener(dbListener);

        tabPending.setOnClickListener(v -> switchTab(PickupItem.STATUS_PENDING));
        tabDone.setOnClickListener(v -> switchTab(PickupItem.STATUS_DONE));
        tabRecycle.setOnClickListener(v -> switchTab(PickupItem.STATUS_DELETED));
        findViewById(R.id.btn_rules).setOnClickListener(v ->
                startActivity(new Intent(this, RulesActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            forceShowPerm = !forceShowPerm;
            buildPermCard();
        });
        findViewById(R.id.btn_ocr).setOnClickListener(v ->
                pickImage.launch("image/*"));

        // 权限卡展开时按返回先收起, 不直接退出应用
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (forceShowPerm) {
                    forceShowPerm = false;
                    buildPermCard();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        startKeepAlive();
        requestRuntimePermissions();
        handleSharedImage(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedImage(intent);
    }

    /** 从相册/其他 App "分享到取件码助手"的截图, 支持单张和多张 */
    private void handleSharedImage(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        if (type == null || !type.startsWith("image/")) return;
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            intent.setAction(null);
            if (uri != null) runOcr(java.util.Collections.singletonList(uri));
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            intent.setAction(null);
            if (uris != null && !uris.isEmpty()) runOcr(uris);
        }
    }

    private void runOcr(List<Uri> uris) {
        if (ocrProgress == null) {
            ocrProgress = new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .create();
        }
        ocrProgress.setMessage(uris.size() > 1
                ? "正在识别 " + uris.size() + " 张截图中的取件码…"
                : "正在识别截图中的取件码…");
        ocrProgress.show();
        OcrManager.recognizeAll(this, uris, (text, error) -> runOnUiThread(() -> {
            if (ocrProgress != null && ocrProgress.isShowing()) ocrProgress.dismiss();
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                return;
            }
            applyOcrText(text == null ? "" : text);
        }));
    }

    /** OCR 文本复用短信/通知同一套解析入库, 按结果给提示 */
    private void applyOcrText(String text) {
        Ingestor.BatchResult br = Ingestor.handleBatch(this, text, "screenshot", "截图识别");
        String msg;
        if (br.newCount > 0 && !br.newCodes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("已识别到 ").append(br.newCount).append(" 个取件码：");
            for (int i = 0; i < br.newCodes.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(br.newCodes.get(i));
            }
            if (br.dupCount > 0) sb.append("（另有 ").append(br.dupCount).append(" 个已在列表中）");
            sb.append("，已加入待取");
            msg = sb.toString();
            switchTab(PickupItem.STATUS_PENDING);
        } else if (br.newCount > 0) {
            msg = "图里没有取件码，但识别到快递已到，已建「到件待查」，可点一键查询";
            switchTab(PickupItem.STATUS_PENDING);
        } else if (br.dupCount > 0) {
            msg = "识别到的 " + br.dupCount + " 个取件码都已在列表中";
        } else {
            String ocr = text.trim();
            msg = ocr.isEmpty()
                    ? "图里没识别到文字，换张清晰的截图试试"
                    : "没识别到取件码。OCR文本: " + (ocr.length() > 120 ? ocr.substring(0, 120) + "…" : ocr);
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbListener != null) {
            PickupDb.get(this).removeListener(dbListener);
            dbListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        forceShowPerm = false;
        buildPermCard();
        PickupDb.get(this).purgeExpired();
        refresh();
        startKeepAlive();
        if (pendingAutostart) {
            pendingAutostart = false;
            if (!autostartConfirmed()) askAutostart();
        }
    }

    // ==================== 自启动确认 ====================

    private SharedPreferences prefs() {
        return getSharedPreferences("pickup_prefs", MODE_PRIVATE);
    }

    private boolean autostartConfirmed() {
        return prefs().getBoolean(KEY_AUTOSTART, false);
    }

    /** 从系统设置页返回后, 让用户确认一次(系统不提供读取自启动白名单的接口) */
    private void askAutostart() {
        new AlertDialog.Builder(this)
                .setTitle("自启动管理")
                .setMessage("已在系统设置里允许「取件码助手」自启动了吗?")
                .setNegativeButton("还没", (d, w) -> toast("记得允许自启动, 否则重启手机后可能收不到取件码"))
                .setPositiveButton("已开启", (d, w) -> {
                    prefs().edit().putBoolean(KEY_AUTOSTART, true).apply();
                    buildPermCard();
                    toast("已记录, 重启手机后也能自动监听");
                })
                .show();
    }

    private void switchTab(int status) {
        currentStatus = status;
        applyTab(tabPending, status == PickupItem.STATUS_PENDING);
        applyTab(tabDone, status == PickupItem.STATUS_DONE);
        applyTab(tabRecycle, status == PickupItem.STATUS_DELETED);
        refresh();
    }

    private void applyTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_selected : R.drawable.bg_tab_normal);
        tab.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.primary_dark : R.color.text_sub));
        tab.setTypeface(null, selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void refresh() {
        List<PickupItem> items;
        if (currentStatus == PickupItem.STATUS_DELETED) {
            items = PickupDb.get(this).listDeleted();
        } else if (currentStatus == PickupItem.STATUS_PENDING) {
            items = PickupDb.get(this).listPending();
        } else {
            items = PickupDb.get(this).list(currentStatus);
        }
        adapter.submit(items);
        statCount.setText(String.valueOf(PickupDb.get(this).pendingCount()));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (currentStatus == PickupItem.STATUS_PENDING) {
            emptyTitle.setText("还没有待取的取件码");
            emptySub.setText("收到快递短信或通知\n取件码会自动出现在这里");
        } else if (currentStatus == PickupItem.STATUS_DONE) {
            emptyTitle.setText("还没有已取的记录");
            emptySub.setText("取完件后点「标记已取」\n就会归档到这里");
        } else {
            emptyTitle.setText("回收仓是空的");
            emptySub.setText("在「已取」里删除的记录\n会在这里保留 1 天后自动清除");
        }
    }

    private void onToggle(PickupItem it) {
        if (it.status == PickupItem.STATUS_DELETED) {
            PickupDb.get(this).restore(it.id);
            Toast.makeText(this, "已恢复到「已取」", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        int next = it.status == PickupItem.STATUS_PENDING
                ? PickupItem.STATUS_DONE : PickupItem.STATUS_PENDING;
        PickupDb.get(this).setStatus(it.id, next);
        Toast.makeText(this,
                next == PickupItem.STATUS_DONE ? "已标记为已取" : "已恢复为待取",
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void onDelete(PickupItem it) {
        boolean arrival = it.status == PickupItem.STATUS_ARRIVAL;
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_delete, null);
        TextView code = content.findViewById(R.id.txt_code);
        code.setText(arrival ? "到件待查" : it.code);

        AlertDialog d = new AlertDialog.Builder(this).setView(content).create();
        content.findViewById(R.id.btn_cancel).setOnClickListener(v -> d.dismiss());
        content.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            PickupDb.get(this).softDelete(it.id);
            Toast.makeText(this, "已移入回收仓", Toast.LENGTH_SHORT).show();
            d.dismiss();
            refresh();
        });
        d.show();
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = getResources().getDisplayMetrics().widthPixels - dp(56);
            w.setAttributes(lp);
        }
    }

    /** 一键查询: 拉起来源 App, 取件码在对方 App 内查看 */
    private void onQuery(PickupItem it) {
        boolean ok = ExpressApps.launch(this, it.sourcePkg, it.carrier);
        if (!ok) {
            Toast.makeText(this, "没找到对应的快递 App，请先安装或在通知原文里查看",
                    Toast.LENGTH_LONG).show();
        }
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
        // 自启动无标准API可读, 跳转厂商设置页后由用户回来确认一次, 状态记在本地
        addPermRow("⑤ 自启动管理", "在厂商设置里允许本应用自启动、后台运行",
                autostartConfirmed(), v -> {
                    pendingAutostart = true;
                    try { startActivity(Permissions.autoStart(this)); }
                    catch (Exception e) { startActivity(Permissions.appDetail(this)); }
                });

        boolean allCoreGranted = Permissions.hasSms(this)
                && Permissions.hasNotify(this)
                && Permissions.hasListener(this)
                && Permissions.isIgnoringBattery(this)
                && autostartConfirmed();

        // 全开时卡片不再隐藏, 换成绿色完成态标题并收起列表; 点设置按钮仍可展开管理
        boolean done = allCoreGranted && !forceShowPerm;
        if (done) {
            permTitle.setText("● 已开启全部权限，实时监听中");
            permTitle.setTextColor(ContextCompat.getColor(this, R.color.success));
            permContainer.setVisibility(View.GONE);
        } else {
            permTitle.setText("还需开启以下权限");
            permTitle.setTextColor(ContextCompat.getColor(this, R.color.text_main));
            permContainer.setVisibility(View.VISIBLE);
        }
        permCard.setVisibility(View.VISIBLE);
    }

    private void addPermRow(CharSequence title, String desc, boolean granted, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_perm_row);
        row.setPadding(dp(12), dp(11), dp(10), dp(11));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(8);
        row.setLayoutParams(rp);

        // 状态圆点: 绿=已开, 橙=待开
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(11f);
        dot.setTextColor(ContextCompat.getColor(this,
                granted ? R.color.success : R.color.badge_orange));
        row.addView(dot);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(9);
        texts.setLayoutParams(tp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        t.setTextSize(13.5f);
        t.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(ContextCompat.getColor(this, R.color.text_sub));
        d.setTextSize(11.5f);

        texts.addView(t);
        texts.addView(d);
        row.addView(texts);

        if (granted) {
            TextView ok = new TextView(this);
            ok.setText("已开启");
            ok.setTextColor(ContextCompat.getColor(this, R.color.success));
            ok.setTextSize(12f);
            row.addView(ok);
        } else {
            MaterialButton btn = new MaterialButton(this);
            btn.setText("去开启");
            btn.setTextSize(12f);
            btn.setMinWidth(0);
            btn.setMinHeight(dp(32));
            btn.setMinimumHeight(dp(32));
            btn.setPadding(dp(14), 0, dp(14), 0);
            btn.setOnClickListener(onClick);
            row.addView(btn);
        }
        // 整行可点: 已开启的权限也能随时跳到系统页管理
        row.setOnClickListener(onClick);
        permContainer.addView(row);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}

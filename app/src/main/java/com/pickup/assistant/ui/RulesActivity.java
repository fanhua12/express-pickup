package com.pickup.assistant.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pickup.assistant.R;
import com.pickup.assistant.parser.ParseResult;
import com.pickup.assistant.parser.PickupParser;
import com.pickup.assistant.parser.Rule;
import com.pickup.assistant.parser.RuleStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** 规则管理页，能开关、调顺序、删除、导入导出，还能拿段文本试跑 */
public class RulesActivity extends AppCompatActivity {

    private static final int REQ_IMPORT = 31;
    private static final int REQ_EXPORT = 32;

    private final List<Rule> rules = new ArrayList<>();
    private RuleAdapter adapter;
    private RecyclerView list;
    private View emptyView;
    private TextView summary, result;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty);
        summary = findViewById(R.id.txt_summary);
        result = findViewById(R.id.txt_result);
        input = findViewById(R.id.input_test);

        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RuleAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_import).setOnClickListener(v -> pickFile(REQ_IMPORT));
        findViewById(R.id.btn_export).setOnClickListener(v -> pickFile(REQ_EXPORT));
        findViewById(R.id.btn_reset).setOnClickListener(v -> confirmReset());
        findViewById(R.id.btn_test).setOnClickListener(v -> tryRun());

        reload();
    }

    private void reload() {
        rules.clear();
        rules.addAll(RuleStore.get(this));
        adapter.notifyDataSetChanged();

        int on = RuleStore.enabledCount(this);
        summary.setText(rules.isEmpty()
                ? "规则为空, 将使用内置兜底规则"
                : "自上而下依次匹配, 命中第一条即采用  ·  共 " + rules.size() + " 条, 已启用 " + on + " 条"
                + (on == 0 ? "\n全部关闭时自动启用内置兜底规则" : ""));
        emptyView.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void persist() {
        RuleStore.save(this, rules);
        reload();
    }

    private void move(int pos, int delta) {
        int to = pos + delta;
        if (to < 0 || to >= rules.size()) return;
        rules.add(to, rules.remove(pos));
        RuleStore.save(this, rules);
        adapter.notifyItemMoved(pos, to);
        reload();
    }

    private void confirmDelete(int pos) {
        Rule r = rules.get(pos);
        new AlertDialog.Builder(this)
                .setTitle("删除规则")
                .setMessage("确定删除「" + r.name + "」?")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    rules.remove(pos);
                    RuleStore.save(this, rules);
                    reload();
                })
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("恢复内置规则")
                .setMessage("将丢弃当前规则, 恢复为默认内置规则?")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (d, w) -> {
                    RuleStore.resetToBuiltin(this);
                    reload();
                    toast("已恢复内置规则");
                })
                .show();
    }

    private void tryRun() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            toast("先粘贴一段短信或通知内容");
            return;
        }
        ParseResult r = PickupParser.parse(this, text);
        if (r.matched) {
            result.setText("识别成功\n取件码: " + r.code
                    + "\n快递公司: " + dash(r.carrier)
                    + "\n驿站/柜: " + dash(r.station));
        } else if (!PickupParser.looksLikeExpress(text)) {
            result.setText("未识别: 内容里没有快递相关词(取件/快递/驿站等), 已被过滤");
        } else {
            result.setText("未识别: 没有规则匹配出取件码, 可调整规则顺序或恢复内置规则");
        }
    }

    private static String dash(String s) {
        return TextUtils.isEmpty(s) ? "未识别" : s;
    }

    // ==================== 导入 / 导出 ====================

    private void pickFile(int req) {
        Intent i = new Intent(req == REQ_EXPORT
                ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        if (req == REQ_EXPORT) i.putExtra(Intent.EXTRA_TITLE, "pickup-rules.json");
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            toast("没有可用的文件管理器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new IllegalStateException("无法写入该位置");
                try {
                    os.write(RuleStore.exportJson(this).getBytes("UTF-8"));
                } finally {
                    os.close();
                }
                toast("已导出 " + rules.size() + " 条规则");
            } else if (requestCode == REQ_IMPORT) {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IllegalStateException("无法读取该文件");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                } finally {
                    in.close();
                }
                int count = RuleStore.importJson(this, new String(bos.toByteArray(), "UTF-8"));
                reload();
                toast("已导入 " + count + " 条规则");
            }
        } catch (Exception e) {
            toast("失败: " + e.getMessage());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ==================== 列表 ====================

    private class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rule, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Rule r = rules.get(pos);
            h.name.setText(r.name);
            h.pattern.setText(r.pattern);

            h.sw.setOnCheckedChangeListener(null);
            h.sw.setChecked(r.enabled);
            h.sw.setOnCheckedChangeListener((b, checked) -> {
                r.enabled = checked;
                RuleStore.save(RulesActivity.this, rules);
                reload();
            });

            h.up.setAlpha(pos == 0 ? 0.25f : 1f);
            h.down.setAlpha(pos == rules.size() - 1 ? 0.25f : 1f);
            h.up.setOnClickListener(v -> move(h.getAdapterPosition(), -1));
            h.down.setOnClickListener(v -> move(h.getAdapterPosition(), 1));
            h.del.setOnClickListener(v -> confirmDelete(h.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return rules.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name, pattern, up, down, del;
            final SwitchCompat sw;

            VH(View v) {
                super(v);
                name = v.findViewById(R.id.txt_name);
                pattern = v.findViewById(R.id.txt_pattern);
                up = v.findViewById(R.id.btn_up);
                down = v.findViewById(R.id.btn_down);
                del = v.findViewById(R.id.btn_del);
                sw = v.findViewById(R.id.sw_enabled);
            }
        }
    }
}
package com.pickup.assistant.parser;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 取件码规则仓库: 首次启动把内置规则(assets/rules.json)落到应用私有目录, 之后以本地文件为准
 * 规则顺序即匹配优先级; 支持导入导出, 全部本地读写, 不联网
 */
public final class RuleStore {

    private static final String TAG = "RuleStore";
    private static final String FILE = "rules.json";
    private static final String ASSET = "rules.json";
    /** 与 assets/rules.json 的 version 保持一致; 升版后内置规则的正则会刷新, 新增的追加到末尾 */
    private static final int VERSION = 3;

    private static List<Rule> cache;

    private RuleStore() {}

    public static synchronized List<Rule> get(Context ctx) {
        if (cache == null) cache = load(ctx);
        return cache;
    }

    public static synchronized void save(Context ctx, List<Rule> rules) {
        // 存副本: 调用方后续 clear()/remove() 不能影响缓存
        cache = new ArrayList<>(rules);
        write(ctx, toJson(cache));
    }

    /** 恢复为内置规则 */
    public static synchronized void resetToBuiltin(Context ctx) {
        try {
            write(ctx, readAsset(ctx));
            cache = null;
        } catch (Exception e) {
            Log.w(TAG, "恢复内置规则失败", e);
        }
    }

    public static synchronized String exportJson(Context ctx) {
        return toJson(get(ctx));
    }

    /** 导入规则: 全部校验通过才写入, 有一条非法则整体拒绝并报出规则名 */
    public static synchronized int importJson(Context ctx, String json) {
        List<Rule> rules;
        try {
            rules = parse(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("不是合法的规则文件");
        }
        if (rules.isEmpty()) throw new IllegalArgumentException("文件里没有规则");
        for (Rule r : rules) {
            String err = Rule.validate(r.name, r.pattern);
            if (!err.isEmpty()) throw new IllegalArgumentException(r.name + " — " + err);
        }
        save(ctx, rules);
        return rules.size();
    }

    public static synchronized int enabledCount(Context ctx) {
        int n = 0;
        for (Rule r : get(ctx)) {
            if (r.enabled && r.regex() != null) n++;
        }
        return n;
    }

    // ==================== 内部 ====================

    private static List<Rule> load(Context ctx) {
        String assetJson = null;
        try {
            assetJson = readAsset(ctx);
        } catch (Exception e) {
            Log.w(TAG, "内置规则读取失败", e);
        }

        String fileJson = "";
        File f = new File(ctx.getFilesDir(), FILE);
        if (f.exists()) {
            try {
                fileJson = new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.w(TAG, "读取规则文件失败", e);
            }
        }

        List<Rule> rules = null;
        if (!fileJson.isEmpty()) {
            try {
                rules = parse(fileJson);
            } catch (Exception e) {
                Log.w(TAG, "规则文件损坏, 回退内置规则", e);
            }
        }

        if (rules == null || rules.isEmpty()) {
            rules = assetJson == null ? new ArrayList<>() : parseQuietly(assetJson);
            write(ctx, toJson(rules));
            return rules;
        }

        // 内置规则集升版: 同名规则刷新正则(保留用户的开关与排序), 新增规则追加到末尾
        if (assetJson != null && versionOf(assetJson) > versionOf(fileJson)) {
            for (Rule b : parseQuietly(assetJson)) {
                Rule exist = findByName(rules, b.name);
                if (exist == null) rules.add(b);
                else exist.pattern = b.pattern;
            }
            write(ctx, toJson(rules));
        }
        return rules;
    }

    private static List<Rule> parseQuietly(String json) {
        try {
            return parse(json);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Rule findByName(List<Rule> rules, String name) {
        for (Rule r : rules) {
            if (name.equals(r.name)) return r;
        }
        return null;
    }

    private static int versionOf(String json) {
        try {
            return new JSONObject(json).optInt("version", 1);
        } catch (Exception e) {
            return 1;
        }
    }

    private static String readAsset(Context ctx) throws Exception {
        InputStream in = ctx.getAssets().open(ASSET);
        try {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void write(Context ctx, String json) {
        try {
            FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), FILE));
            try {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "规则写入失败", e);
        }
    }

    private static List<Rule> parse(String json) throws Exception {
        List<Rule> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.optJSONArray("rules");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String pattern = o.optString("pattern", "");
            if (pattern.isEmpty()) continue;
            out.add(new Rule(o.optString("name", "未命名规则"), pattern, o.optBoolean("enabled", true)));
        }
        return out;
    }

    public static String toJson(List<Rule> rules) {
        try {
            JSONArray arr = new JSONArray();
            for (Rule r : rules) {
                JSONObject o = new JSONObject();
                o.put("name", r.name);
                o.put("pattern", r.pattern);
                o.put("enabled", r.enabled);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("version", VERSION);
            root.put("rules", arr);
            return root.toString(2);
        } catch (Exception e) {
            return "{\n  \"version\": " + VERSION + ",\n  \"rules\": []\n}";
        }
    }
}
package com.pickup.assistant.parser;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从短信/通知文本中提取: 取件码、快递公司、驿站或快递柜名
 * 取件码规则来自 RuleStore(可导入导出/开关/调序), 全部规则都不可用时启用内置兜底
 * 全部为本地正则匹配, 不联网
 */
public final class PickupParser {

    private PickupParser() {}

    /** 出现这些词才认为是快递相关消息, 降低误判 */
    private static final String[] EXPRESS_HINTS = {
        "取件", "取货", "提货", "快递", "速递", "包裹", "驿站", "快递柜", "丰巢",
        "速递易", "菜鸟", "派件", "签收", "代收点", "快递超市", "快件", "送达"
    };

    /** 快递公司: 长词在前, 避免被短词抢先 */
    private static final String[][] CARRIERS = {
        {"京东物流", "京东"}, {"京东快递", "京东"},
        {"顺丰速运", "顺丰"}, {"顺丰", "顺丰"},
        {"中通快递", "中通"}, {"中通", "中通"},
        {"圆通速递", "圆通"}, {"圆通", "圆通"},
        {"韵达速递", "韵达"}, {"韵达快递", "韵达"}, {"韵达", "韵达"},
        {"申通快递", "申通"}, {"申通", "申通"},
        {"百世快递", "百世"}, {"百世", "百世"},
        {"极兔速递", "极兔"}, {"极兔", "极兔"},
        {"中国邮政", "邮政EMS"}, {"EMS", "邮政EMS"}, {"邮政", "邮政EMS"},
        {"德邦快递", "德邦"}, {"德邦", "德邦"},
        {"丹鸟", "丹鸟"}, {"菜鸟裹裹", "菜鸟"}, {"菜鸟", "菜鸟"}, {"丰网", "丰网"},
        {"天猫超市", "天猫"}, {"天猫", "天猫"}
    };

    /** 驿站/快递柜: 长词在前 */
    private static final String[][] STATIONS = {
        {"菜鸟驿站", null}, {"中邮速递易", "速递易"}, {"速递易", null},
        {"丰巢智能柜", "丰巢"}, {"丰巢快递柜", "丰巢"}, {"丰巢", null},
        {"格格货栈", "格格货栈"}, {"快递超市", null}, {"云柜", null},
        {"代收点", null}, {"快递柜", null}, {"驿站", null}, {"智能柜", "快递柜"},
        {"自提点", null}, {"取件点", null}
    };

    /** 内置兜底规则: 仅当可用规则为空(全被关闭/文件损坏)时启用, 保证不会抓不到码 */
    private static final Pattern[] FALLBACK_PATTERNS = {
        Pattern.compile("(?:取件码|取货码|提货码|提取码|凭码|取件口令|取件密码|凭)[是为：:\\s]*([0-9A-Za-z]{1,4}(?:-[0-9A-Za-z]{1,6}){1,3})"),
        Pattern.compile("(?:取件码|取货码|提货码|提取码|验证码|凭码|取件口令|取件密码)[是为：:\\s]*([0-9]{4,12})"),
        Pattern.compile("(?:取件码|取货码|提货码|提取码|凭码|取件口令|取件密码)[是为：:\\s]*"
                + "((?=[0-9A-Za-z-]*[A-Za-z])[0-9A-Za-z]{1,10}(?:-[0-9A-Za-z]{1,6}){0,3})"),
        Pattern.compile("凭[是为：:\\s]*((?=[0-9A-Za-z-]*[A-Za-z])[0-9A-Za-z]{1,10}(?:-[0-9A-Za-z]{1,6}){0,3})"),
        Pattern.compile("凭([0-9]{4,12})"),
        Pattern.compile("([0-9]{8,12})(?=[^0-9]{0,12}(?:丰巢|柜|驿站|取件))"),
        Pattern.compile("(?:取件|取货|提货|包裹|驿站|快件|签收|格口|柜|凭)[^0-9A-Za-z]{0,8}"
                + "([A-Za-z][0-9A-Za-z]{0,3}(?:-[0-9A-Za-z]{1,6}){1,3})"),
        Pattern.compile("([A-Za-z][0-9A-Za-z]{0,3}(?:-[0-9A-Za-z]{1,6}){1,3})"
                + "(?=[^0-9A-Za-z]{0,8}(?:取件|取货|提货|包裹|驿站|快件|签收|格口|柜))"),
        Pattern.compile("(?:柜|驿站|格口|取件|取货|包裹)[^0-9A-Za-z]{0,8}"
                + "([0-9]{1,4}(?:-[0-9]{1,6}){1,3})"),
        Pattern.compile("(?:取件|取货|提货|包裹|驿站|快件|签收|格口)[^0-9A-Za-z]{0,8}"
                + "((?=[0-9A-Za-z]{2,10}?[^0-9A-Za-z])(?=[0-9A-Za-z]*[A-Za-z])[0-9A-Za-z]{4,10})")
    };

    /** 判断文本是否像快递消息 */
    public static boolean looksLikeExpress(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String h : EXPRESS_HINTS) {
            if (text.contains(h)) return true;
        }
        return false;
    }

    /** 到件语义(但通知里没有取件码)的强提示词 */
    private static final String[] ARRIVAL_HINTS = {
        "已到", "到站", "到达", "到了", "待取", "请取", "入柜", "代收", "送达", "可取"
    };

    /** 这些词表示还在路上或已签收, 不算到驿站待取 */
    private static final String[] ARRIVAL_NEGATIVE = {
        "转运中心", "分拨", "集散", "营业部", "揽收", "已发出", "发出", "运输中",
        "派送中", "正在派", "派件中", "已签收", "本人签收", "签收人"
    };

    /** 快递到了但通知未给取件码(如菜鸟"快递已到, 请一键查看") */
    public static boolean looksLikeArrival(String text) {
        if (!looksLikeExpress(text)) return false;
        for (String n : ARRIVAL_NEGATIVE) {
            if (text.contains(n)) return false;
        }
        for (String h : ARRIVAL_HINTS) {
            if (text.contains(h)) return true;
        }
        return false;
    }

    /** 解析入口 */
    public static ParseResult parse(Context ctx, String raw) {
        ParseResult r = ParseResult.empty();
        if (!looksLikeExpress(raw)) return r;

        // 去掉数字之间的空格, 适配 "8765 4321" 这类排版
        String text = raw.replaceAll("(?<=[0-9])[ \\u3000\\t]+(?=[0-9])", "");

        r.carrier = detect(text, CARRIERS);
        r.station = detectStation(text);
        r.code = detectCode(ctx, text);
        r.matched = !r.code.isEmpty();
        return r;
    }

    /** 批量解析: 从一段文本中提取所有不同的取件码(用于截图多码识别) */
    public static List<ParseResult> parseAll(Context ctx, String raw) {
        List<ParseResult> out = ParseResult.emptyList();
        if (!looksLikeExpress(raw)) return out;

        String text = raw.replaceAll("(?<=[0-9])[ \\u3000\\t]+(?=[0-9])", "");
        String carrier = detect(text, CARRIERS);
        String station = detectStation(text);

        List<String> codes = detectAllCodes(ctx, text);
        for (String code : codes) {
            out.add(ParseResult.of(code, carrier, station));
        }
        return out;
    }

    private static String detect(String text, String[][] table) {
        for (String[] kv : table) {
            if (text.contains(kv[0])) return kv[1] == null ? kv[0] : kv[1];
        }
        return "";
    }

    private static String detectStation(String text) {
        for (String[] kv : STATIONS) {
            int idx = text.indexOf(kv[0]);
            if (idx >= 0) {
                String name = kv[1] == null ? kv[0] : kv[1];
                // 尝试向后取最多 12 个字符作为具体点名, 如"菜鸟驿站(阳光小区店)"
                String tail = text.substring(idx).trim();
                String detail = extractBracketName(tail);
                return detail.isEmpty() ? name : name + detail;
            }
        }
        return "";
    }

    /** 从"菜鸟驿站(阳光小区店)..."中提取 (阳光小区店) */
    private static String extractBracketName(String s) {
        Matcher m = Pattern.compile("[（(]([^（）()]{2,16})[）)]").matcher(s);
        if (m.find()) return "(" + m.group(1) + ")";
        return "";
    }

    private static String detectCode(Context ctx, String text) {
        List<Rule> rules = ctx == null ? null : RuleStore.get(ctx);
        if (rules != null) {
            for (Rule rule : rules) {
                if (!rule.enabled) continue;
                Pattern p = rule.regex();
                if (p == null) continue;
                String code = match(p, text);
                if (!code.isEmpty()) return code;
            }
            // 有可用规则时以规则为准; 规则全被关闭或全损坏才启用内置兜底
            for (Rule rule : rules) {
                if (rule.enabled && rule.regex() != null) return "";
            }
        }
        for (Pattern p : FALLBACK_PATTERNS) {
            String code = match(p, text);
            if (!code.isEmpty()) return code;
        }
        return "";
    }

    /** 找全部不重复的取件码(用于截图多码), 去重保持出现顺序 */
    private static List<String> detectAllCodes(Context ctx, String text) {
        List<String> found = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<Rule> rules = ctx == null ? null : RuleStore.get(ctx);

        if (rules != null) {
            for (Rule rule : rules) {
                if (!rule.enabled) continue;
                Pattern p = rule.regex();
                if (p == null) continue;
                for (String code : matchAll(p, text)) {
                    if (seen.add(code)) found.add(code);
                }
            }
            boolean hasUsable = false;
            for (Rule rule : rules) {
                if (rule.enabled && rule.regex() != null) { hasUsable = true; break; }
            }
            if (hasUsable) return found;
        }
        for (Pattern p : FALLBACK_PATTERNS) {
            for (String code : matchAll(p, text)) {
                if (seen.add(code)) found.add(code);
            }
        }
        return found;
    }

    private static String match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String code = m.group(1) == null ? "" : m.group(1).trim();
            if (isValidCode(code, text)) return code;
        }
        return "";
    }

    /** 找全部有效匹配(去重保持顺序) */
    private static List<String> matchAll(Pattern p, String text) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            String code = m.group(1) == null ? "" : m.group(1).trim();
            if (isValidCode(code, text) && seen.add(code)) {
                out.add(code);
            }
        }
        return out;
    }

    /** 排除手机号(11位且1开头)、运单号、日期等误判 */
    private static boolean isValidCode(String code, String text) {
        if (code.isEmpty() || code.length() > 16) return false;
        String digits = code.replaceAll("[^0-9]", "");
        boolean hasLetter = !code.replaceAll("[^A-Za-z]", "").isEmpty();
        if (digits.isEmpty()) return false;
        // 纯数字码至少 4 位; 含字母的格口号可以是 "t-2-2002" 这种短码
        if (digits.length() < (hasLetter ? 2 : 4)) return false;
        if (digits.length() > 12) return false;
        // 含字母的长码多为运单号(如 SF1234567890123), 取件码不会超过 10 位
        if (hasLetter && code.replace("-", "").length() > 10) return false;
        // 2024-1-1 / 2024-09-2218 这类是日期时间, 不是取件码
        if (code.matches("(19|20)[0-9]{2}-.*")) return false;
        // 11 位 1 开头通常是手机号
        if (digits.length() == 11 && digits.startsWith("1")) {
            // 除非明确处于"取件码"之后很近
            int ci = text.indexOf(code);
            int ki = indexOfAnyCodeKeyword(text);
            if (ki < 0 || ci < 0 || ci - ki > 8) return false;
        }
        return true;
    }

    private static int indexOfAnyCodeKeyword(String text) {
        String[] kws = {"取件码", "取货码", "提货码", "提取码", "凭码"};
        int best = -1;
        for (String k : kws) {
            int i = text.indexOf(k);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }
}

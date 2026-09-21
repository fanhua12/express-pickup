package com.pickup.assistant.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从短信/通知文本中提取: 取件码、快递公司、驿站或快递柜名
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

    /** 取件码规则, 按优先级排列 */
    private static final Pattern[] CODE_PATTERNS = {
        // 1. 带横杠格口码(需取件语境前缀, 避开日期): 取件码5-3-1234 / 凭码6-8-901
        Pattern.compile("(?:取件码|取货码|提货码|凭码|取件口令|凭)[是为：:\\s]*([0-9]{1,4}(?:-[0-9]{1,4}){1,3})"),
        // 2. 明确前缀 + 纯数字(贪婪到非数字边界): 取件码87654321 / 验证码23819
        Pattern.compile("(?:取件码|取货码|提货码|提取码|验证码|凭码|取件口令)[是为：:\\s]*([0-9]{4,12})"),
        // 3. 明确前缀 + 含字母的码: 取货码：A8B9 / AB-1234
        Pattern.compile("(?:取件码|取货码|提货码|提取码|凭码|取件口令)[是为：:\\s]*"
                + "((?=[0-9A-Za-z-]*[A-Za-z])[A-Za-z0-9]{2,8}(?:-[A-Za-z0-9]{2,8})?)"),
        // 4. 凭 + 纯数字(无"码"字): 凭123456 取件
        Pattern.compile("凭([0-9]{4,12})"),
        // 5. 丰巢/柜/驿站语境附近的 8-12 位数字
        Pattern.compile("([0-9]{8,12})(?=[^0-9]{0,12}(?:丰巢|柜|驿站|取件))")
    };

    /** 判断文本是否像快递消息 */
    public static boolean looksLikeExpress(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String h : EXPRESS_HINTS) {
            if (text.contains(h)) return true;
        }
        return false;
    }

    /** 解析入口 */
    public static ParseResult parse(String raw) {
        ParseResult r = ParseResult.empty();
        if (!looksLikeExpress(raw)) return r;

        // 去掉数字之间的空格, 适配 "8765 4321" 这类排版
        String text = raw.replaceAll("(?<=[0-9])[ \\u3000\\t]+(?=[0-9])", "");

        r.carrier = detect(text, CARRIERS);
        r.station = detectStation(text);
        r.code = detectCode(text);
        r.matched = !r.code.isEmpty();
        return r;
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

    private static String detectCode(String text) {
        for (Pattern p : CODE_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String code = m.group(1).trim();
                if (isValidCode(code, text)) return code;
            }
        }
        return "";
    }

    /** 排除手机号(11位且1开头)、运单号等误判 */
    private static boolean isValidCode(String code, String text) {
        String digits = code.replace("-", "");
        if (digits.length() < 4 || digits.length() > 12) return false;
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

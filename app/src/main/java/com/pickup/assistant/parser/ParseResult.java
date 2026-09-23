package com.pickup.assistant.parser;

import java.util.ArrayList;
import java.util.List;

/** 解析结果: 取件码 + 快递公司 + 驿站/柜名 */
public class ParseResult {
    public boolean matched;
    public String code = "";
    public String carrier = "";
    public String station = "";

    public static ParseResult empty() {
        return new ParseResult();
    }

    public static ParseResult of(String code, String carrier, String station) {
        ParseResult r = new ParseResult();
        r.code = code;
        r.carrier = carrier;
        r.station = station;
        r.matched = !code.isEmpty();
        return r;
    }

    public static List<ParseResult> emptyList() {
        return new ArrayList<>();
    }
}

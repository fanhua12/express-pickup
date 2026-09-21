package com.pickup.assistant.parser;

/** 解析结果: 取件码 + 快递公司 + 驿站/柜名 */
public class ParseResult {
    public boolean matched;
    public String code = "";
    public String carrier = "";
    public String station = "";

    public static ParseResult empty() {
        return new ParseResult();
    }
}

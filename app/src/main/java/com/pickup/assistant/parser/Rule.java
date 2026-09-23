package com.pickup.assistant.parser;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 一条提取规则。正则里第 1 个捕获组就是取件码，别忘了加括号 */
public class Rule {

    public String name;
    public String pattern;
    public boolean enabled = true;

    private transient Pattern compiled;
    private transient boolean broken;

    public Rule() {}

    public Rule(String name, String pattern, boolean enabled) {
        this.name = name;
        this.pattern = pattern;
        this.enabled = enabled;
    }

    /** 编译好的正则。编译失败就返回 null，这条跳过，不拖累别的规则 */
    public Pattern regex() {
        if (compiled == null && !broken) {
            try {
                compiled = Pattern.compile(pattern);
                if (compiled.matcher("").groupCount() < 1) broken = true;
            } catch (PatternSyntaxException e) {
                broken = true;
            }
        }
        return broken ? null : compiled;
    }

    /** 校验规则能不能用，返回错误说明，空串就是没问题 */
    public static String validate(String name, String pattern) {
        if (name == null || name.trim().isEmpty()) return "规则名不能为空";
        if (pattern == null || pattern.trim().isEmpty()) return "正则不能为空";
        try {
            Pattern p = Pattern.compile(pattern);
            if (p.matcher("").groupCount() < 1) return "正则必须包含至少一个括号捕获组";
        } catch (PatternSyntaxException e) {
            return "正则语法错误: " + e.getDescription();
        }
        return "";
    }
}
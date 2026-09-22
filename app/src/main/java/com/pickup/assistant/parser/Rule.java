package com.pickup.assistant.parser;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 一条取件码提取规则: 正则必须含第 1 个捕获组作为取件码 */
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

    /** 编译后的正则; 编译失败返回 null(该条规则自动跳过, 不影响其它规则) */
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

    /** 校验规则是否可用, 返回错误说明(空串表示正常) */
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
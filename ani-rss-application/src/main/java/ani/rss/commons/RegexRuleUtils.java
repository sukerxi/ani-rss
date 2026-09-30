package ani.rss.commons;

import ani.rss.enums.StringEnum;
import cn.hutool.core.util.StrUtil;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 订阅规则（匹配 / 排除 / 自定义集数规则）校验工具。
 */
public class RegexRuleUtils {

    private RegexRuleUtils() {
    }

    /**
     * 校验正则规则是否可编译。
     *
     * <p>规则支持「{{字幕组}}:正则」前缀（见 {@link StringEnum#SUBGROUP_REG_STR}），
     * 校验时只编译前缀之后的正则本体。</p>
     *
     * @param rules 规则集合
     * @throws IllegalArgumentException 存在非法正则时抛出，消息中携带具体规则
     */
    public static void validateRules(Collection<String> rules) {
        if (rules == null) {
            return;
        }
        for (String rule : rules) {
            if (StrUtil.isBlank(rule)) {
                continue;
            }
            String regex = stripSubgroupPrefix(rule.trim());
            try {
                Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        StrUtil.format("正则表达式不合法 [{}]: {}", rule, e.getDescription()));
            }
        }
    }

    /**
     * 校验单条自定义集数规则
     */
    public static void validateSingle(String rule) {
        if (StrUtil.isBlank(rule)) {
            return;
        }
        try {
            Pattern.compile(rule.trim());
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    StrUtil.format("正则表达式不合法 [{}]: {}", rule, e.getDescription()));
        }
    }

    /**
     * 去掉 {@code {{字幕组}}:} 前缀，返回正则本体
     */
    public static String stripSubgroupPrefix(String rule) {
        java.util.regex.Matcher matcher = Pattern.compile(StringEnum.SUBGROUP_REG_STR).matcher(rule);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return rule;
    }
}

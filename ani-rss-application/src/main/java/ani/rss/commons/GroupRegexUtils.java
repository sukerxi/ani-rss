package ani.rss.commons;

import ani.rss.entity.GroupRegex;
import ani.rss.entity.GroupRegex.RegexItem;
import cn.hutool.core.collection.CollUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroupRegexUtils {

    /**
     * 标签规则。
     *
     * @param regex   规则原文（同时作为前端「排除规则」使用，需保持为合法正则）
     * @param pattern 预编译后的规则，{@code (?i)} 内联标志统一忽略大小写，
     *                替代历史上手写的 {@code cht|Cht|CHT} 这类枚举，避免漏掉混合大小写
     */
    private record TagRule(String regex, Pattern pattern) {
        static TagRule of(String regex) {
            return new TagRule(regex, Pattern.compile(regex));
        }
    }

    private static final List<TagRule> RULES = List.of(
            TagRule.of("1920[Xx]1080"),
            TagRule.of("3840[Xx]2160"),
            TagRule.of("(?i)1080p"),
            TagRule.of("(?i)720p"),
            TagRule.of("(?i)4k"),
            TagRule.of("繁"),
            TagRule.of("简"),
            TagRule.of("日"),
            TagRule.of("内嵌"),
            TagRule.of("内封"),
            TagRule.of("外挂"),
            TagRule.of("(?i)cht"),
            TagRule.of("(?i)chs"),
            TagRule.of("(?i)avc"),
            TagRule.of("(?i)hevc"),
            TagRule.of("(?i)h264"),
            TagRule.of("(?i)h265"),
            TagRule.of("(?i)10bit"),
            TagRule.of("(?i)mp4"),
            TagRule.of("(?i)mkv")
    );

    public static <T> GroupRegex toGroupRegx(List<T> list, Function<T, String> getFun) {
        List<String> titles = list.stream()
                .map(getFun)
                .distinct()
                .toList();

        List<List<RegexItem>> regexList = new ArrayList<>();
        List<String> tags = new ArrayList<>();

        for (String title : titles) {
            List<RegexItem> regexItems = new ArrayList<>();
            for (TagRule rule : RULES) {
                Matcher matcher = rule.pattern().matcher(title);
                if (!matcher.find()) {
                    continue;
                }
                // 标签展示标题中实际命中的文本（保留原始大小写）
                String tag = matcher.group();

                RegexItem regexItem = new RegexItem();
                regexItem.setRegex(rule.regex())
                        .setLabel(tag);

                regexItems.add(regexItem);

                if (tags.size() < 5 && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
            if (regexItems.isEmpty()) {
                continue;
            }
            regexItems = CollUtil.distinct(regexItems, GsonStatic::toJson, true);
            regexList.add(regexItems);
        }

        regexList = CollUtil.distinct(regexList, GsonStatic::toJson, true);

        return new GroupRegex()
                .setRegexList(regexList)
                .setTags(tags);
    }
}

package ani.rss.commons;

import ani.rss.entity.GroupRegex;
import ani.rss.entity.GroupRegex.RegexItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class GroupRegexUtils {

    /**
     * 标签族。
     *
     * <p>同一族内的写法互为同义词（如 1080p / 1920x1080、繁 / CHT、HEVC / H265），
     * 生成的正则族内为 OR 关系，避免字幕组在同义词之间切换命名后匹配失效；
     * 不同族之间仍为 AND 关系（由 ItemsUtil 的多条 match 规则保证）。</p>
     *
     * @param label   归一化后的标签文案
     * @param regex   规则原文（同时作为前端「排除/匹配规则」使用，需保持为合法正则）
     * @param pattern 预编译后的规则
     * @param order   族顺序，决定组合内标签的稳定排列
     */
    private record TagRule(String label, String regex, Pattern pattern, int order) {
    }

    private static final List<TagRule> RULES = List.of(
            // 分辨率
            new TagRule("1080P", "(?i)(?:1920[Xx]1080|1080p)",
                    Pattern.compile("(?:1920[Xx]1080|1080p)", Pattern.CASE_INSENSITIVE), 10),
            new TagRule("720P", "(?i)(?:1280[Xx]720|720p)",
                    Pattern.compile("(?:1280[Xx]720|720p)", Pattern.CASE_INSENSITIVE), 11),
            new TagRule("4K", "(?i)(?:3840[Xx]2160|2160p|4k)",
                    Pattern.compile("(?:3840[Xx]2160|2160p|4k)", Pattern.CASE_INSENSITIVE), 12),
            // 语言（CHT/CHS 与中文单字同义归一）
            new TagRule("繁", "(?i)(?:繁|cht)",
                    Pattern.compile("(?:繁|cht)", Pattern.CASE_INSENSITIVE), 20),
            new TagRule("简", "(?i)(?:简|chs)",
                    Pattern.compile("(?:简|chs)", Pattern.CASE_INSENSITIVE), 21),
            new TagRule("日", "(?i)(?:日|jpn|jap)",
                    Pattern.compile("(?:日|jpn|jap)", Pattern.CASE_INSENSITIVE), 22),
            // 字幕形式
            new TagRule("内嵌", "内嵌", Pattern.compile("内嵌"), 30),
            new TagRule("内封", "内封", Pattern.compile("内封"), 31),
            new TagRule("外挂", "外挂", Pattern.compile("外挂"), 32),
            // 编码（AVC/H264/x264、HEVC/H265/x265 同义归一）
            new TagRule("AVC", "(?i)(?:avc|h\\.?264|x264)",
                    Pattern.compile("(?:avc|h\\.?264|x264)", Pattern.CASE_INSENSITIVE), 40),
            new TagRule("HEVC", "(?i)(?:hevc|h\\.?265|x265)",
                    Pattern.compile("(?:hevc|h\\.?265|x265)", Pattern.CASE_INSENSITIVE), 41),
            new TagRule("10bit", "(?i)10bit",
                    Pattern.compile("10bit", Pattern.CASE_INSENSITIVE), 42),
            // 容器
            new TagRule("MP4", "(?i)mp4",
                    Pattern.compile("mp4", Pattern.CASE_INSENSITIVE), 50),
            new TagRule("MKV", "(?i)mkv",
                    Pattern.compile("mkv", Pattern.CASE_INSENSITIVE), 51)
    );

    /**
     * 分组行预览标签最多展示数量
     */
    private static final int PREVIEW_TAG_LIMIT = 5;

    public static <T> GroupRegex toGroupRegx(List<T> list, Function<T, String> getFun) {
        List<String> titles = list.stream()
                .map(getFun)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        // 组合 key -> 聚合信息，保持首次出现顺序
        Map<String, Combo> comboMap = new LinkedHashMap<>();
        // 标签（族）在标题中的出现频次
        Map<String, Integer> tagFrequency = new LinkedHashMap<>();
        Map<String, TagRule> ruleByLabel = new LinkedHashMap<>();
        for (TagRule rule : RULES) {
            ruleByLabel.put(rule.label(), rule);
            tagFrequency.put(rule.label(), 0);
        }

        for (String title : titles) {
            List<RegexItem> regexItems = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (TagRule rule : RULES) {
                if (!rule.pattern().matcher(title).find()) {
                    continue;
                }
                regexItems.add(new RegexItem()
                        .setRegex(rule.regex())
                        .setLabel(rule.label()));
                labels.add(rule.label());
                tagFrequency.merge(rule.label(), 1, Integer::sum);
            }
            if (regexItems.isEmpty()) {
                continue;
            }
            String key = String.join("|", labels);
            Combo combo = comboMap.get(key);
            if (combo == null) {
                combo = new Combo(regexItems, labels, 0, title);
                comboMap.put(key, combo);
            }
            combo.count++;
        }

        List<Combo> combos = new ArrayList<>(comboMap.values());
        // 命中条数多的组合排前面（稳定排序，条数相同保持首次出现顺序）
        combos.sort(Comparator.comparingInt((Combo c) -> c.count).reversed());

        List<List<RegexItem>> regexList = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<String> sampleTitles = new ArrayList<>();
        for (Combo combo : combos) {
            regexList.add(combo.items);
            counts.add(combo.count);
            sampleTitles.add(combo.sampleTitle);
        }

        // 行预览标签：按出现频次倒序（族顺序兜底），取前 N 个
        List<String> tags = tagFrequency.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator
                        .<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparingInt(e -> ruleByLabel.get(e.getKey()).order()))
                .map(Map.Entry::getKey)
                .limit(PREVIEW_TAG_LIMIT)
                .toList();

        return new GroupRegex()
                .setRegexList(regexList)
                .setCounts(counts)
                .setSampleTitles(sampleTitles)
                .setTags(tags);
    }

    /**
     * 一个标签组合的聚合信息
     */
    private static final class Combo {
        private final List<RegexItem> items;
        private final List<String> labels;
        private int count;
        private final String sampleTitle;

        private Combo(List<RegexItem> items, List<String> labels, int count, String sampleTitle) {
            this.items = items;
            this.labels = labels;
            this.count = count;
            this.sampleTitle = sampleTitle;
        }
    }
}

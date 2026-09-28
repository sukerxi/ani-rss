package ani.rss.commons;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RSS 视频标题集数解析器。
 *
 * <p>替代历史上堆叠在单一巨型正则（RenameUtil.REG_STR）里的解析逻辑，采用
 * 「规则表 + 两阶段匹配」：</p>
 * <ol>
 *     <li><b>强标记</b>：[05] / 【05】 / 第05话 / E05 / EP05 / Episode 05 / #05 / Vol.05 / - 05，
 *     全局扫描后取位置最靠后的一个（与旧贪婪回溯语义一致），并对分辨率、年份等做边界防护；</li>
 *     <li><b>裸数字兜底</b>：仅当没有任何强标记时启用，识别「字幕组 标题 05 [1080p]」这类
 *     空格分隔的主流命名，同时排除年份 / 分辨率 / 码率 / 季（Season 2）/ 标题内数字等干扰。</li>
 * </ol>
 * <p>该类为无状态纯函数，不依赖 Spring 与业务配置，便于单元测试。</p>
 */
public final class EpisodeParser {

    private EpisodeParser() {
    }

    /**
     * 解析结果。
     *
     * @param present 是否识别到集数
     * @param episode 集数（已解析为数值，如 "05" -&gt; 5.0，"12.5" -&gt; 12.5）
     * @param source  命中的规则来源，用于日志排查
     */
    public record Result(boolean present, double episode, String source) {
        public static final Result MISSING = new Result(false, 0, null);

        public static Result of(double episode, String source) {
            return new Result(true, episode, source);
        }
    }

    /**
     * 强标记规则。每个正则<b>恰好包含一个捕获组</b>，且该捕获组即为集数文本。
     */
    private record StrongRule(Pattern pattern, String source, boolean special) {
    }

    /**
     * 常见技术参数数值（分辨率 / 尺寸），裸数字兜底时直接排除。
     */
    private static final Set<Integer> TECH_NUMS = Set.of(
            480, 576, 720, 1080, 2160, 4320, 1920, 3840, 1280, 854
    );

    private static final List<StrongRule> STRONG_RULES = List.of(
            // 特例：命中即确定，不再与其他规则比较位置
            new StrongRule(Pattern.compile("^\\[TOC][^\\n]*?(\\d+(?:\\.5)?)"), "toc", true),
            new StrongRule(Pattern.compile("^六四位元字幕组[^\\n]*?★(\\d+(?:\\.5)?)★"), "special", true),
            // 方括号：[05] [05v2] [05 END] [05 (12)] [12.5]；天然排除 [1080p] / [1920x1080 ...]
            new StrongRule(Pattern.compile(
                    "\\[(\\d{1,4}(?:\\.5)?)"
                            + "(?:[ ._]?\\(\\d+(?:\\.\\d+)?\\))?"
                            + "(?:[ ._]?[vV]\\d+)?"
                            + "(?:[ ._]?(?:END|End|end|完|FIN|Fin|fin|最終|最终))?"
                            + "[ ._]?\\]"), "bracket", false),
            // 全角括号：【05】 【05v2】 【05 完】
            new StrongRule(Pattern.compile(
                    "【\\s*(\\d+(?:\\.5)?)\\s*"
                            + "(?:(?:[vV]\\d+|END|End|end|完|終|终|FIN)[^】]*)?】"), "bracketCn", false),
            // 第 x 话/話/集/回/局/章/節/节（不含「季」，避免把季数当集数）
            new StrongRule(Pattern.compile(
                    "第\\s*(\\d+(?:\\.5)?)\\s*[话話集回局章節节]"
                            + "(?:\\s*[-－–—~〜]\\s*(?:END|End|end|完|終|终))?"), "cnWord", false),
            // Episode / EP / Ep / ep，允许空格、点、下划线、连字符分隔（Episode 05、EP.05、EP-05）
            new StrongRule(Pattern.compile(
                    "(?<![A-Za-z])(?:EPISODE|Episode|episode|EP|Ep|ep)[\\s.．_\\-—]*(\\d+(?:\\.5)?)"), "ep", false),
            // 裸 E/e 紧跟数字（最保守，等同旧行为）
            new StrongRule(Pattern.compile("(?<![A-Za-z])[Ee](\\d+(?:\\.5)?)"), "ep", false),
            // #05
            new StrongRule(Pattern.compile("#\\s*(\\d+(?:\\.5)?)"), "hash", false),
            // Vol / Volume，支持 Vol.05、VOL_05 等
            new StrongRule(Pattern.compile(
                    "(?<![A-Za-z])(?:VOLUME|Volume|volume|VOL|Vol|vol)[\\s.．_\\-—]*(\\d+)"), "vol", false),
            // 连字符：-05 / –05 / —05 / ～05。前置不能是字母数字（排除 Web-DL、x264-8bit），
            // 后置不能紧跟 P（排除「- 1080P」），连字符来源还会再排除 4 位年份（「- 2024」）
            new StrongRule(Pattern.compile(
                    "(?<![A-Za-z0-9])[-－–—~〜]\\s*(\\d{1,4}(?:\\.5)?)(?!\\s*[Pp])(?![\\d.])"), "dash", false)
    );

    private static final Pattern TOKEN = Pattern.compile("\\d+(?:\\.5)?");
    private static final Pattern SEASON_CONTEXT = Pattern.compile(
            "(?:Season|SEASON|season|Part|PART|part|Cour|COUR|Chapter|CHAPTER|Pt|PT|期|季)\\s*\\.?\\s*$");
    private static final Pattern VIDEO_EXT = Pattern.compile(
            "^\\.(?:mp4|mkv|avi|mov|wmv|flv|ts|webm)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIT_SUFFIX = Pattern.compile("^\\s?bit", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASH_AFTER_SPACE = Pattern.compile("^\\s+([-－–—])");

    private static final String ALLOWED_PREV_CHARS = "[]【】()（）/\\-－–—:：、,，";
    private static final String ALLOWED_NEXT_CHARS = "]】)）vV";

    /**
     * 解析标题中的集数。
     *
     * @param title RSS 条目标题
     * @return 解析结果，无法识别时 {@link Result#present()} 为 false
     */
    public static Result parse(String title) {
        if (title == null || title.isBlank()) {
            return Result.MISSING;
        }
        Result strong = parseStrong(title);
        if (strong != null) {
            return strong;
        }
        return parseBare(title);
    }

    private static Result parseStrong(String title) {
        Double bestEpisode = null;
        String bestSource = null;
        int bestStart = -1;

        for (StrongRule rule : STRONG_RULES) {
            Matcher matcher = rule.pattern().matcher(title);
            while (matcher.find()) {
                double episode = Double.parseDouble(matcher.group(1));
                if (rule.special()) {
                    // 特例优先级最高，命中直接返回
                    return Result.of(episode, rule.source());
                }
                // 连字符形式额外排除年份，如「Title - 2024 OVA」
                if ("dash".equals(rule.source()) && isYear(episode)) {
                    continue;
                }
                int start = matcher.start();
                if (start >= bestStart) {
                    bestStart = start;
                    bestEpisode = episode;
                    bestSource = rule.source();
                }
            }
        }
        return bestEpisode == null ? null : Result.of(bestEpisode, bestSource);
    }

    /**
     * 裸数字兜底：扫描所有数字片段，按边界与上下文剔除技术参数、年份、季数、标题内数字，
     * 取最后一个存活的片段（集数通常位于标题后部）。
     */
    private static Result parseBare(String title) {
        Matcher matcher = TOKEN.matcher(title);
        List<double[]> hits = new ArrayList<>();

        while (matcher.find()) {
            String raw = matcher.group();
            double episode = Double.parseDouble(raw);
            int start = matcher.start();
            int end = matcher.end();

            if (isTechNumber(episode) || (raw.length() == 4 && isYear(episode))) {
                continue;
            }

            // 前边界：开头，或空白/括号/分隔符；不能是字母、数字、点（避免小数 2.43）、普通中文
            if (start > 0 && !isAllowedPrevChar(title.charAt(start - 1))) {
                continue;
            }

            String after = end >= title.length() ? "" : title.substring(end);

            // 数字本身就是文件名主干，直接以视频扩展名结尾
            if (VIDEO_EXT.matcher(after).find()) {
                hits.add(new double[]{start, episode});
                continue;
            }

            // 后边界：结尾，或空白/右括号/版本号 v
            if (!after.isEmpty() && !isAllowedNextChar(after.charAt(0))) {
                continue;
            }

            // 「86 -不存在的战区」：数字后空白紧跟一个引导文字的破折号，属于标题数字
            Matcher dashMatcher = DASH_AFTER_SPACE.matcher(after);
            if (dashMatcher.find()) {
                int afterDash = dashMatcher.end(1);
                char next = afterDash < after.length() ? after.charAt(afterDash) : ' ';
                if (!Character.isDigit(next)) {
                    continue;
                }
            }

            // 季 / 部分语境：Season 2、Part 2、2期
            String before = title.substring(Math.max(0, start - 16), start);
            if (SEASON_CONTEXT.matcher(before).find()) {
                continue;
            }

            // 10bit / 8bit
            if (BIT_SUFFIX.matcher(after).find()) {
                continue;
            }

            hits.add(new double[]{start, episode});
        }

        if (hits.isEmpty()) {
            return Result.MISSING;
        }
        double[] last = hits.get(hits.size() - 1);
        return Result.of(last[1], "bare");
    }

    private static boolean isYear(double episode) {
        return episode >= 1900 && episode <= 2099 && episode == Math.floor(episode);
    }

    private static boolean isTechNumber(double episode) {
        if (episode != Math.floor(episode)) {
            return false;
        }
        return TECH_NUMS.contains((int) episode);
    }

    private static boolean isAllowedPrevChar(char c) {
        return Character.isWhitespace(c) || ALLOWED_PREV_CHARS.indexOf(c) >= 0;
    }

    private static boolean isAllowedNextChar(char c) {
        return Character.isWhitespace(c) || ALLOWED_NEXT_CHARS.indexOf(c) >= 0;
    }
}

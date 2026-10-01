package ani.rss.commons;

import ani.rss.entity.GroupRegex;
import ani.rss.entity.GroupRegex.RegexItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GroupRegexUtils} 标签识别回归测试。
 */
class GroupRegexUtilsTest {

    private List<String> labels(GroupRegex groupRegex) {
        return groupRegex.getRegexList().stream()
                .flatMap(items -> items.stream().map(RegexItem::getLabel))
                .distinct()
                .toList();
    }

    private List<String> regexes(GroupRegex groupRegex) {
        return groupRegex.getRegexList().stream()
                .flatMap(items -> items.stream().map(RegexItem::getRegex))
                .distinct()
                .toList();
    }

    @Test
    void recognizeCommonTags() {
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Frieren [05][1080P][HEVC10bit][MKV]",
                "[Sub] Solo Leveling [12][720P][AVC][MP4]"
        ), title -> title);

        List<String> labels = labels(groupRegex);
        assertTrue(labels.contains("1080P"));
        assertTrue(labels.contains("720P"));
        assertTrue(labels.contains("HEVC"));
        assertTrue(labels.contains("AVC"));
        // 标签归一化后保留统一文案
        assertTrue(labels.contains("MKV"));
        assertTrue(labels.contains("MP4"));
    }

    @Test
    void mixedCaseIsMatched() {
        // 历史上手写 mp4|MP4 枚举会漏掉 Mp4 这类混合大小写
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Title [05][1080P][Mp4]"
        ), title -> title);

        assertTrue(labels(groupRegex).stream().anyMatch("MP4"::equalsIgnoreCase));
        assertTrue(regexes(groupRegex).contains("(?i)mp4"));
    }

    @Test
    void titleWithoutTagsProducesNoGroup() {
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] 普通标题 05"
        ), title -> title);

        assertTrue(groupRegex.getRegexList().isEmpty());
        assertTrue(groupRegex.getTags().isEmpty());
    }

    @Test
    void duplicateTagSetsAreDeduplicated() {
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[SubA] Title A [05][1080P][CHT]",
                "[SubB] Title B [06][1080P][繁]"
        ), title -> title);

        // CHT 与 繁 归一为同一标签族，两条标题组合一致，只保留一组且计数为 2
        assertEquals(1, groupRegex.getRegexList().size());
        assertEquals(1, groupRegex.getCounts().size());
        assertEquals(2, groupRegex.getCounts().get(0));
        assertTrue(labels(groupRegex).contains("繁"));
    }

    @Test
    void synonymFamiliesAreNormalized() {
        // 分辨率、语言、编码的不同写法归一为同一组合
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Title A [05][1920x1080][H265][CHS]",
                "[Sub] Title B [06][1080P][HEVC][简]"
        ), title -> title);

        assertEquals(1, groupRegex.getRegexList().size());
        List<String> labels = labels(groupRegex);
        assertTrue(labels.contains("1080P"));
        assertTrue(labels.contains("HEVC"));
        assertTrue(labels.contains("简"));
        assertFalse(labels.contains("CHS"));

        // 生成的族正则为 OR 写法，任一同义词都能命中
        List<String> regexes = regexes(groupRegex);
        assertTrue(regexes.contains("(?i)(?:1920[Xx]1080|1080p)"));
        assertTrue(regexes.contains("(?i)(?:hevc|h\\.?265|x265)"));
    }

    @Test
    void combosOrderedByFrequencyAndAlignedWithSamples() {
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Common [01][1080P][繁]",
                "[Sub] Common [02][1080P][繁]",
                "[Sub] Common [03][1080P][繁]",
                "[Sub] Rare [01][1080P][简]"
        ), title -> title);

        assertEquals(2, groupRegex.getRegexList().size());
        assertEquals(2, groupRegex.getCounts().size());
        assertEquals(2, groupRegex.getSampleTitles().size());
        // 命中多的组合排第一
        assertEquals(3, groupRegex.getCounts().get(0));
        assertEquals(1, groupRegex.getCounts().get(1));
    }

    @Test
    void globalTagsCappedAtFive() {
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Title [05][1080P][720P][4K][HEVC][AVC][CHT][CHS][MKV]"
        ), title -> title);

        assertFalse(groupRegex.getTags().isEmpty());
        assertTrue(groupRegex.getTags().size() <= 5);
    }

    @Test
    void previewTagsOrderedByCategoryRegardlessOfFrequency() {
        // 1080P/AVC/MP4 出现 2 次，繁/简 仅 1 次；
        // 选标签仍按频次取前 5，但展示顺序必须按类别（分辨率→语言→编码→容器）对齐
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Title A [05][1080P][繁][AVC][MP4]",
                "[Sub] Title B [06][1080P][简][AVC][MP4]"
        ), title -> title);

        assertEquals(List.of("1080P", "繁", "简", "AVC", "MP4"), groupRegex.getTags());
    }
}

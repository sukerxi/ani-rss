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
        // 实际命中的标签保留原始大小写
        assertTrue(labels.contains("MKV"));
        assertTrue(labels.contains("MP4"));
    }

    @Test
    void mixedCaseIsMatched() {
        // 历史上手写 mp4|MP4 枚举会漏掉 Mp4 这类混合大小写
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Title [05][1080P][Mp4]"
        ), title -> title);

        assertTrue(labels(groupRegex).stream().anyMatch("Mp4"::equalsIgnoreCase));
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
                "[SubB] Title B [06][1080P][CHT]"
        ), title -> title);

        // 两条标题标签组合完全一致，只保留一组
        assertEquals(1, groupRegex.getRegexList().size());
    }

    @Test
    void globalTagsCappedAtFive() {
        GroupRegex groupRegex = GroupRegexUtils.toGroupRegx(List.of(
                "[Sub] Title [05][1080P][720P][4K][HEVC][AVC][CHT][CHS][MKV]"
        ), title -> title);

        assertFalse(groupRegex.getTags().isEmpty());
        assertTrue(groupRegex.getTags().size() <= 5);
    }
}

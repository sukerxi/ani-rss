package ani.rss.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class GroupRegex implements Serializable {
    /**
     * Regex
     */
    @Schema(description = "正则表达式列表")
    private List<List<RegexItem>> regexList;

    @Schema(description = "标签集合")
    private List<String> tags;

    /**
     * 与 {@link #regexList} 一一对应的近期命中条数，用于前端展示组合热度
     */
    @Schema(description = "每个标签组合的近期命中条数")
    private List<Integer> counts;

    /**
     * 与 {@link #regexList} 一一对应的命中样例标题
     */
    @Schema(description = "每个标签组合的命中样例标题")
    private List<String> sampleTitles;

    @Data
    @Accessors(chain = true)
    @Schema(description = "正则表达式项")
    public static class RegexItem implements Serializable {
        @Schema(description = "标签")
        private String label;
        @Schema(description = "正则表达式")
        private String regex;
    }
}

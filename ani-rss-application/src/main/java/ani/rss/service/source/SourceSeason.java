package ani.rss.service.source;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 季度选项（Mikan 专有，其他源不产生）。
 */
@Data
@Accessors(chain = true)
public class SourceSeason implements Serializable {

    /**
     * 年，如 2026
     */
    private Integer year;

    /**
     * 季度，如 春/夏/秋/冬
     */
    private String season;

    /**
     * 选项文案，如 "2026 秋"
     */
    private String seasonLabel;

    /**
     * 是否当前选中
     */
    private Boolean select;
}

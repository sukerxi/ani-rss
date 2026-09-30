package ani.rss.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * RSS 中被过滤掉的资源条目（用于预览页排查「为什么没下载」）
 */
@Data
@Accessors(chain = true)
@Schema(description = "被过滤的资源条目")
public class RejectedItem implements Serializable {
    @Schema(description = "字幕组")
    private String subgroup;

    @Schema(description = "标题")
    private String title;

    /**
     * 过滤原因，如：排除规则 xxx、匹配规则未命中 xxx、集数无法识别
     */
    @Schema(description = "过滤原因")
    private String reason;

    @Schema(description = "发布时间")
    private Date pubDate;
}

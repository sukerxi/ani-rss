package ani.rss.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * TMDB 剧集组分段
 */
@Data
@Accessors(chain = true)
public class ThemoviedbGroupSeasonVO implements Serializable {

    @Schema(description = "分段序号，与订阅的季对应")
    private Integer order;

    @Schema(description = "分段名称")
    private String name;

    @Schema(description = "集数")
    private Integer episodeCount;

    @Schema(description = "首播日期 yyyy-MM-dd")
    private String startAirDate;

    @Schema(description = "最后播出日期 yyyy-MM-dd")
    private String endAirDate;

    @Schema(description = "原始季编号（整段来自同一季时返回）")
    private Integer originSeasonNumber;

    @Schema(description = "原始起始集编号")
    private Integer originEpisodeStart;

    @Schema(description = "原始结束集编号")
    private Integer originEpisodeEnd;
}

package ani.rss.service.source;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 订阅源番剧列表结果（统一模型）。
 *
 * <p>公共部分为按星期分组的番剧；Mikan 季度选择器、AniBT 季度元数据
 * 作为可选字段携带，不污染接口签名。</p>
 */
@Data
@Accessors(chain = true)
public class SourceListResult implements Serializable {

    /**
     * 按星期分组的番剧
     */
    private List<SourceWeek> weeks = new ArrayList<>();

    /**
     * 总番剧数
     */
    private int totalItem;

    /**
     * 季度选择项（Mikan）
     */
    private List<SourceSeason> seasons = new ArrayList<>();

    /**
     * 实际返回数据对应的季度（AniBT）
     */
    private String requestedSeason;

    /**
     * 源站可用季度列表（AniBT）
     */
    private List<String> availableSeasons = new ArrayList<>();

    /**
     * 跳过基类统一富化（AnimeGarden 用 bgmUrl 合成单番周时为 true）
     */
    private boolean bypassEnrichment;

    /**
     * 源内原始列表根实体（内部回程载体，非契约字段）
     */
    private Object raw;
}

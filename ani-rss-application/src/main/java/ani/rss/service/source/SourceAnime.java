package ani.rss.service.source;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 订阅源番剧条目（统一模型）。
 *
 * <p>raw 为源内原始实体的回程载体：实现方抓取时映射一次，
 * 基类富化后由 facade 取回原始实体并回填公共字段，避免透传数据的双重映射。
 * raw 不是统一模型的契约字段。</p>
 */
@Data
@Accessors(chain = true)
public class SourceAnime implements Serializable {

    /**
     * bgm.tv subject id；可能由 {@code resolveBgmIds} 从源内链接补全
     */
    private String bgmId;

    /**
     * 源内番剧 id（如 Mikan 数字 id、AniBT animeId）
     */
    private String sourceId;

    /**
     * 源内详情页链接（Mikan）
     */
    private String sourceUrl;

    /**
     * 详情页解析出的 bgm.tv 链接（Mikan）
     */
    private String bgmUrl;

    /**
     * 展示标题
     */
    private String title;

    /**
     * 源站自有评分，bgm 评分未就绪时兜底（AniBT）
     */
    private Double sourceRating;

    /**
     * 源本地预判的订阅状态（Mikan 按源内 id 预判；最终状态由基类并入 bgm 口径）
     */
    private Boolean exists;

    /**
     * 源站 RSS 发布数（AniBT 零发布过滤）
     */
    private Integer releaseCount;

    /**
     * 最终评分（基类写入）
     */
    private Double score;

    /**
     * 源内原始实体（内部回程载体，非契约字段）
     */
    private Object raw;
}

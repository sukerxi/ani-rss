package ani.rss.service.source;

import ani.rss.entity.GroupRegex;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 字幕组（统一模型）。
 */
@Data
@Accessors(chain = true)
public class SourceGroup implements Serializable {

    /**
     * 字幕组 id（Mikan subgroupId / AniBT groupId / AnimeGarden fansub id）
     */
    private String id;

    /**
     * 字幕组名称
     */
    private String name;

    /**
     * RSS 地址
     */
    private String rss;

    /**
     * bgm.tv subject id
     */
    private String bgmId;

    /**
     * 详情页上的 bgm.tv 链接（Mikan 透传）
     */
    private String bgmUrl;

    /**
     * 更新日文案（Mikan）
     */
    private String updateDay;

    /**
     * 组内资源
     */
    private List<SourceResource> items = new ArrayList<>();

    /**
     * 字幕组匹配正则（基类写入）
     */
    private GroupRegex groupRegex;

    /**
     * 源内原始字幕组实体（内部回程载体，非契约字段）
     */
    private Object raw;
}

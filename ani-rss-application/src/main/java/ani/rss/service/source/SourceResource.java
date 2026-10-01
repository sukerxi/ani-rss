package ani.rss.service.source;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 字幕组内的资源项（统一模型）。
 *
 * <p>仅收敛基类需要处理的字段（标题、大小）；其余源特有字段由 raw 原样携带。
 * size 为 null 时不做格式化（Mikan 沿用站点文本）。</p>
 */
@Data
@Accessors(chain = true)
public class SourceResource implements Serializable {

    /**
     * 资源标题（字幕组正则的唯一输入）
     */
    private String title;

    /**
     * 字节大小；数值型由基类统一格式化
     */
    private Long size;

    /**
     * 格式化后的大小（基类写入）
     */
    private String formatSize;

    /**
     * 源内原始资源实体（内部回程载体，非契约字段）
     */
    private Object raw;
}

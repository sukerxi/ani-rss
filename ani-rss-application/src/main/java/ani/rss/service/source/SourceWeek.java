package ani.rss.service.source;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 订阅源星期分组（统一模型）。
 */
@Data
@Accessors(chain = true)
public class SourceWeek implements Serializable {

    /**
     * 星期标签；文本搜索结果统一为 "Search"/"搜索"
     */
    private String weekLabel;

    /**
     * 该星期下的番剧
     */
    private List<SourceAnime> items = new ArrayList<>();

    /**
     * 源内原始分组实体（内部回程载体，非契约字段）
     */
    private Object raw;
}

package ani.rss.service.source;

import java.util.List;

/**
 * 番剧订阅源策略接口。
 *
 * <p>Mikan / AniBT / AnimeGarden 各自实现：抓取源数据并映射为统一模型，
 * 公共后处理（bgm 评分、订阅状态、星期排序、字幕组正则、大小格式化）
 * 由 {@link AbstractBangumiSource} 统一完成。</p>
 */
public interface BangumiSource {

    /**
     * 源标识，与订阅 {@code ani.type} 口径一致（mikan / ani-bt / anime-garden）
     */
    String code();

    /**
     * 番剧列表（按星期分组，已完成评分/订阅态/排序等统一富化）
     */
    SourceListResult list(SourceQuery query);

    /**
     * 某部番剧的字幕组列表（含 RSS、资源项与匹配正则）
     */
    List<SourceGroup> groups(BangumiRef ref);
}

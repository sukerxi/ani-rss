package ani.rss.service.source;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 订阅源统一查询条件。
 *
 * <p>不同源按需取用：Mikan 用 text（支持 "id: 123"）+ year/season；
 * AniBT 用 text/bgmUrl/season；AnimeGarden 用 bgmUrl。</p>
 */
@Data
@Accessors(chain = true)
public class SourceQuery implements Serializable {

    /**
     * 关键字：Mikan 搜索词（特殊语法 "id: xxx" 直查番剧）；AniBT 标题
     */
    private String text;

    /**
     * bgm.tv 番剧链接，用于单番反查（AniBT / AnimeGarden）
     */
    private String bgmUrl;

    /**
     * 年份（Mikan 季度选择）
     */
    private Integer year;

    /**
     * 季度：Mikan 为 春/夏/秋/冬；AniBT 为其源站季度标识，原样透传
     */
    private String season;

    /**
     * 强制刷新：绕过响应缓存实时拉取
     */
    private boolean refresh;
}

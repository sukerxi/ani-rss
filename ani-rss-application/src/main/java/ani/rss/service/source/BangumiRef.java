package ani.rss.service.source;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 字幕组查询的番剧定位。
 *
 * <p>Mikan 以详情页 URL 定位，AniBT / AnimeGarden 以 bgmId 定位。</p>
 */
@Data
@Accessors(chain = true)
public class BangumiRef implements Serializable {

    /**
     * bgm.tv subject id
     */
    private String bgmId;

    /**
     * 源内详情页 URL（Mikan）
     */
    private String url;
}

package ani.rss.entity.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class AniBTQueryDTO implements Serializable {
    private String season;
    private String bgmUrl;
    private String title;
    /**
     * 手动刷新: 强制绕过响应缓存同步拉取
     */
    private Boolean refresh;
}

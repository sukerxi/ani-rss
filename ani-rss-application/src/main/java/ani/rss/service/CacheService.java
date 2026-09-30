package ani.rss.service;

import ani.rss.cache.HttpResponseCache;
import ani.rss.commons.GsonStatic;
import ani.rss.util.basic.HttpReq;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class CacheService {

    /**
     * 封面映射 fresh 10min, stale 7 天; 源站返回 ETag, 过期后条件请求刷新, 失败时旧数据兜底
     */
    private static final Duration COVER_FRESH_TTL = Duration.ofMinutes(10);
    private static final Duration COVER_STALE_TTL = Duration.ofDays(7);

    /**
     * 获取 BGM 封面缓存
     * k: Bgm Id, v: BgmInfo.Images
     *
     * @return JsonObject
     */
    public JsonObject getBgmCover() {
        try {
            String body = HttpResponseCache.get("http:cache:bgm-cover",
                    () -> HttpReq.get("https://cache.wushuo.top/bgm/cover"),
                    COVER_FRESH_TTL, COVER_STALE_TTL);
            return GsonStatic.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new JsonObject();
    }
}

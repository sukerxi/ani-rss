package ani.rss.service.source.anibt;

import ani.rss.cache.HttpResponseCache;
import ani.rss.commons.GsonStatic;
import ani.rss.entity.AniBT;
import ani.rss.service.source.*;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.BgmUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AniBT 订阅源实现：JSON API 抓取 + 映射为统一模型。
 */
@Service
public class AniBTSource extends AbstractBangumiSource {

    private static final String HOST = "https://anibt.net";

    /**
     * 季番列表 fresh 60s, stale 10min (对齐源站 s-maxage=300, stale-while-revalidate=300)
     */
    private static final Duration LIST_FRESH_TTL = Duration.ofSeconds(60);
    private static final Duration LIST_STALE_TTL = Duration.ofMinutes(10);

    @Override
    public String code() {
        return "ani-bt";
    }

    @Override
    public SourceListResult fetchList(SourceQuery query) {
        String title = StrUtil.blankToDefault(query.getText(), "");

        // 一次性计算, 保证 season 和 bgmId 都是 effectively final
        final String season;
        final String bgmId;
        if (StrUtil.isNotBlank(title)) {
            season = "";
            bgmId = "";
        } else if (StrUtil.isNotBlank(query.getBgmUrl())) {
            season = StrUtil.blankToDefault(query.getSeason(), "");
            bgmId = BgmUtil.getSubjectId(query.getBgmUrl());
        } else {
            season = StrUtil.blankToDefault(query.getSeason(), "");
            bgmId = "";
        }

        boolean force = query.isRefresh();
        String body;
        if (StrUtil.isNotBlank(title)) {
            // 搜索请求不缓存, 始终实时
            body = HttpReq.getRetry(HOST + "/api/seasons/anime")
                    .form("season", season)
                    .form("bgmId", bgmId)
                    .form("query", title)
                    .thenFunction(res -> {
                        HttpReq.assertStatus(res);
                        return res.body();
                    });
        } else {
            String cacheKey = "http:anibt:seasons:" + SecureUtil.md5(season + "|" + bgmId);
            body = HttpResponseCache.get(cacheKey,
                    () -> HttpReq.getRetry(HOST + "/api/seasons/anime")
                            .form("season", season)
                            .form("bgmId", bgmId)
                            .form("query", ""),
                    LIST_FRESH_TTL, LIST_STALE_TTL, force);
        }

        JsonObject jsonObject = GsonStatic.fromJson(body, JsonObject.class);
        JsonObject data = Objects.isNull(jsonObject) ? null : jsonObject.getAsJsonObject("data");
        Assert.notNull(data, "AniBT 响应格式异常, 缺少 data 字段");
        AniBT aniBT = GsonStatic.fromJson(data, AniBT.class);
        Assert.notNull(aniBT, "AniBT 响应格式异常");

        SourceListResult result = new SourceListResult();
        result.setRaw(aniBT);
        result.setRequestedSeason(aniBT.getRequestedSeason());
        result.setAvailableSeasons(ObjectUtil.defaultIfNull(aniBT.getAvailableSeasons(), List.of()));

        List<AniBT.ByWeekday> rawWeeks = aniBT.getByWeekday() == null
                ? List.of()
                : aniBT.getByWeekday();

        List<SourceWeek> weeks = new ArrayList<>();
        for (AniBT.ByWeekday rawWeek : rawWeeks) {
            List<AniBT.Anime> rawAnimes = rawWeek.getAnimes() == null
                    ? List.of()
                    : rawWeek.getAnimes();
            List<SourceAnime> items = rawAnimes.stream()
                    .map(AniBTSource::toCanonical)
                    .toList();
            weeks.add(new SourceWeek()
                    .setWeekLabel(rawWeek.getWeekdayLabel())
                    .setItems(new ArrayList<>(items))
                    .setRaw(rawWeek));
        }
        result.setWeeks(weeks);
        return result;
    }

    @Override
    protected List<SourceGroup> fetchGroups(BangumiRef ref) {
        String bgmId = ref.getBgmId();
        return HttpReq.getRetry(HOST + "/api/anime/groups")
                .form("bgmId", bgmId)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    JsonObject data = Objects.isNull(jsonObject) ? null : jsonObject.getAsJsonObject("data");
                    Assert.notNull(data, "AniBT 字幕组响应格式异常, 缺少 data 字段");
                    JsonArray groups = data.getAsJsonArray("groups");
                    Assert.notNull(groups, "AniBT 字幕组响应格式异常, 缺少 groups 字段");
                    List<AniBT.Group> rawList = GsonStatic.fromJsonList(groups, AniBT.Group.class);

                    List<SourceGroup> result = new ArrayList<>();
                    for (AniBT.Group raw : rawList) {
                        String rss = StrUtil.format(
                                "https://anibt.net/rss/anime.xml?bgmId={}&groupSlug={}",
                                bgmId, raw.getSlug());
                        raw.setRss(rss).setBgmId(bgmId);

                        List<AniBT.Item> rawItems = raw.getItems() == null
                                ? List.of()
                                : raw.getItems();
                        List<SourceResource> resources = rawItems.stream()
                                .map(item -> new SourceResource()
                                        .setTitle(item.getTitle())
                                        .setSize(item.getSize())
                                        .setRaw(item))
                                .toList();

                        result.add(new SourceGroup()
                                .setId(raw.getGroupId())
                                .setName(raw.getName())
                                .setRss(rss)
                                .setBgmId(bgmId)
                                .setItems(new ArrayList<>(resources))
                                .setRaw(raw));
                    }
                    return result;
                });
    }

    /**
     * 非搜索时过滤没有 RSS 发布的番剧
     */
    @Override
    protected List<SourceAnime> filterAnimes(List<SourceAnime> items, SourceQuery query) {
        if (StrUtil.isNotBlank(query.getText())) {
            return items;
        }
        return items.stream()
                .filter(anime -> anime.getReleaseCount() != null && anime.getReleaseCount() > 0)
                .toList();
    }

    /**
     * BGM 评分未就绪时先用 AniBT 源站评分临时展示，后续由 BGM 缓存校准
     */
    @Override
    protected double resolveScore(SourceAnime anime, Double bgmScore) {
        if (bgmScore != null && bgmScore > 0) {
            return bgmScore;
        }
        return ObjectUtil.defaultIfNull(anime.getSourceRating(), 0.0);
    }

    private static SourceAnime toCanonical(AniBT.Anime raw) {
        AniBT.Title title = raw.getTitle();
        return new SourceAnime()
                .setBgmId(raw.getBgmId())
                .setSourceId(raw.getAnimeId())
                .setTitle(title == null ? null : title.getPrimary())
                .setSourceRating(raw.getRating())
                .setReleaseCount(raw.getRssReleaseCount())
                .setRaw(raw);
    }
}

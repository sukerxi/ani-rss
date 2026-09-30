package ani.rss.service;

import ani.rss.cache.HttpResponseCache;
import ani.rss.commons.FileUtils;
import ani.rss.commons.GroupRegexUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.comparator.WeekComparator;
import ani.rss.entity.Ani;
import ani.rss.entity.AniBT;
import ani.rss.entity.GroupRegex;
import ani.rss.entity.dto.AniBTQueryDTO;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.BgmUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AniBTService {
    private static final String HOST = "https://anibt.net";

    /**
     * 季番列表 fresh 60s, stale 10min (对齐源站 s-maxage=300, stale-while-revalidate=300)
     */
    private static final Duration LIST_FRESH_TTL = Duration.ofSeconds(60);
    private static final Duration LIST_STALE_TTL = Duration.ofMinutes(10);

    public AniBT list(AniBTQueryDTO dto) {
        String title = dto.getTitle();

        Set<String> subscribedBgmIds = AniUtil.getSubscribedBgmIds();

        String bgmUrl = dto.getBgmUrl();
        String seasonInput = dto.getSeason();

        // 一次性计算, 保证 season 和 bgmId 都是 effectively final
        final String season;
        final String bgmId;
        if (StrUtil.isNotBlank(title)) {
            season = "";
            bgmId = "";
        } else if (StrUtil.isNotBlank(bgmUrl)) {
            season = seasonInput;
            bgmId = BgmUtil.getSubjectId(bgmUrl);
        } else {
            season = seasonInput;
            bgmId = "";
        }

        boolean force = Boolean.TRUE.equals(dto.getRefresh());
        String query = StrUtil.blankToDefault(title, "");
        String body;
        if (StrUtil.isNotBlank(title)) {
            // 搜索请求不缓存, 始终实时
            body = HttpReq.get(HOST + "/api/seasons/anime")
                    .form("season", season)
                    .form("bgmId", bgmId)
                    .form("query", query)
                    .thenFunction(res -> {
                        HttpReq.assertStatus(res);
                        return res.body();
                    });
        } else {
            String cacheKey = "http:anibt:seasons:" + SecureUtil.md5(season + "|" + bgmId);
            body = HttpResponseCache.get(cacheKey,
                    () -> HttpReq.get(HOST + "/api/seasons/anime")
                            .form("season", season)
                            .form("bgmId", bgmId)
                            .form("query", ""),
                    LIST_FRESH_TTL, LIST_STALE_TTL, force);
        }

        JsonObject jsonObject = GsonStatic.fromJson(body, JsonObject.class);
        JsonObject data = jsonObject.getAsJsonObject("data");
        AniBT aniBT = GsonStatic.fromJson(data, AniBT.class);

        List<AniBT.ByWeekday> byWeekday = aniBT.getByWeekday();

        // 统一从 bgm.tv 获取全部番剧评分 (唯一口径), 热路径命中本地缓存
        List<String> allBgmIds = byWeekday.stream()
                .map(AniBT.ByWeekday::getAnimes)
                .flatMap(List::stream)
                .map(AniBT.Anime::getBgmId)
                .toList();
        Map<String, Double> scoreMap = BgmUtil.getScores(allBgmIds);

        for (AniBT.ByWeekday weekday : byWeekday) {
            List<AniBT.Anime> animeList = weekday.getAnimes();
            animeList = animeList.stream()
                    .filter(anime -> {
                        if (StrUtil.isBlank(title)) {
                            return anime.getRssReleaseCount() > 0;
                        }
                        return true;
                    })
                    .peek(anime -> {
                        String bgmIdOfAnime = anime.getBgmId();
                        Double bgmScore = scoreMap.get(bgmIdOfAnime);
                        // BGM 评分未就绪时先用 AniBT 源站评分临时展示, 后续由 BGM 缓存校准
                        double score = (bgmScore != null && bgmScore > 0)
                                ? bgmScore
                                : ObjectUtil.defaultIfNull(anime.getRating(), 0.0);
                        anime.setScore(score)
                                .setExists(subscribedBgmIds.contains(bgmIdOfAnime));
                    })
                    .sorted(Comparator.comparingDouble(AniBT.Anime::getScore).reversed())
                    .toList();
            weekday.setAnimes(animeList);
        }

        WeekComparator weekComparator = new WeekComparator();
        byWeekday = byWeekday.stream()
                .filter(weekday -> CollUtil.isNotEmpty(weekday.getAnimes()))
                .sorted((a, b) ->
                        weekComparator.compare(a.getWeekdayLabel(), b.getWeekdayLabel())
                )
                .toList();
        aniBT.setByWeekday(byWeekday);

        return aniBT;
    }

    public List<AniBT.Group> getGroups(String bgmId) {
        return HttpReq.get(HOST + "/api/anime/groups")
                .form("bgmId", bgmId)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    JsonArray groups = jsonObject.getAsJsonObject("data")
                            .getAsJsonArray("groups");
                    List<AniBT.Group> groupList = GsonStatic.fromJsonList(groups, AniBT.Group.class);
                    for (AniBT.Group group : groupList) {
                        String slug = group.getSlug();
                        String rss = "https://anibt.net/rss/anime.xml?bgmId={}&groupSlug={}";
                        rss = StrUtil.format(rss, bgmId, slug);
                        group.setRss(rss);

                        List<AniBT.Item> items = group.getItems();
                        GroupRegex groupRegx = GroupRegexUtils.toGroupRegx(items, AniBT.Item::getTitle);

                        for (AniBT.Item item : items) {
                            Long size = item.getSize();
                            String formatSize = FileUtils.formatSize(size, true);
                            item.setFormatSize(formatSize);
                        }

                        group.setBgmId(bgmId)
                                .setGroupRegex(groupRegx);
                    }
                    return groupList;
                });
    }
}

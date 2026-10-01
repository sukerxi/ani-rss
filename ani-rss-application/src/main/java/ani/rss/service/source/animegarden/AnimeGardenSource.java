package ani.rss.service.source.animegarden;

import ani.rss.cache.HttpResponseCache;
import ani.rss.commons.GsonStatic;
import ani.rss.entity.AnimeGarden;
import ani.rss.entity.BgmInfo;
import ani.rss.service.CacheService;
import ani.rss.service.source.*;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.BgmUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AnimeGarden 订阅源实现：JSON API 抓取 + bgm 封面/星期预处理 + 映射为统一模型。
 */
@Service
public class AnimeGardenSource extends AbstractBangumiSource {

    private static final String HOST = "https://api.animes.garden";

    /**
     * /subjects 源站 max-age=86400; 本地 fresh 5min, stale 24h
     */
    private static final Duration SUBJECTS_FRESH_TTL = Duration.ofMinutes(5);
    private static final Duration SUBJECTS_STALE_TTL = Duration.ofHours(24);

    private static final List<String> WEEK_LABELS = List.of(
            "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"
    );

    @Resource
    private CacheService cacheService;

    @Override
    public String code() {
        return "anime-garden";
    }

    @Override
    protected SourceListResult fetchList(SourceQuery query) {
        SourceListResult result = new SourceListResult();

        if (StrUtil.isNotBlank(query.getBgmUrl())) {
            // 单番反查：直接从 bgm 信息合成一周，跳过基类富化
            String bgmId = BgmUtil.getSubjectId(query.getBgmUrl());
            BgmInfo bgmInfo = BgmUtil.getBgmInfo(bgmId);
            Double score = Optional.ofNullable(bgmInfo.getRating())
                    .map(BgmInfo.Rating::getScore)
                    .orElse(0.0);

            AnimeGarden.Subject subject = new AnimeGarden.Subject()
                    .setName(BgmUtil.getFinalName(bgmInfo))
                    .setId(bgmId)
                    .setCover(bgmInfo.getImages().getSmall())
                    .setScore(score)
                    .setExists(true);

            AnimeGarden.Week rawWeek = new AnimeGarden.Week()
                    .setWeekLabel("搜索")
                    .setSubjects(List.of(subject));

            SourceWeek week = new SourceWeek()
                    .setWeekLabel("搜索")
                    .setItems(new ArrayList<>(List.of(toCanonical(subject))))
                    .setRaw(rawWeek);

            result.setWeeks(new ArrayList<>(List.of(week)));
            result.setBypassEnrichment(true);
            return result;
        }

        JsonObject bgmCover = cacheService.getBgmCover();

        String subjectsBody = HttpResponseCache.get("http:animesgarden:subjects",
                () -> HttpReq.get(HOST + "/subjects"),
                SUBJECTS_FRESH_TTL, SUBJECTS_STALE_TTL, query.isRefresh());
        JsonObject subjectsJson = GsonStatic.fromJson(subjectsBody, JsonObject.class);
        JsonArray subjects = subjectsJson.getAsJsonArray("subjects");
        List<AnimeGarden.Subject> subjectList =
                GsonStatic.fromJsonList(subjects, AnimeGarden.Subject.class);

        // 封面回填（bgm 缓存）+ 激活时间 → 星期标签
        subjectList = subjectList.stream()
                .peek(subject -> {
                    String cover = Optional.ofNullable(bgmCover.get(subject.getId()))
                            .map(it -> GsonStatic.fromJson(it, BgmInfo.Images.class))
                            .map(BgmInfo.Images::getSmall)
                            .orElse("");
                    subject.setCover(cover);

                    int i = DateUtil.dayOfWeek(subject.getActivedAt()) - 1;
                    subject.setWeekLabel(WEEK_LABELS.get(i));
                })
                .toList();

        Map<String, List<AnimeGarden.Subject>> grouped = subjectList.stream()
                .collect(Collectors.groupingBy(AnimeGarden.Subject::getWeekLabel));

        List<SourceWeek> weeks = new ArrayList<>();
        for (String weekLabel : WEEK_LABELS) {
            List<AnimeGarden.Subject> rawItems = grouped.get(weekLabel);
            if (rawItems == null) {
                continue;
            }
            AnimeGarden.Week rawWeek = new AnimeGarden.Week()
                    .setWeekLabel(weekLabel)
                    .setSubjects(rawItems);
            SourceWeek week = new SourceWeek()
                    .setWeekLabel(weekLabel)
                    .setItems(rawItems.stream().map(AnimeGardenSource::toCanonical)
                            .collect(Collectors.toCollection(ArrayList::new)))
                    .setRaw(rawWeek);
            weeks.add(week);
        }
        result.setWeeks(weeks);
        return result;
    }

    @Override
    protected List<SourceGroup> fetchGroups(BangumiRef ref) {
        String bgmId = ref.getBgmId();

        List<AnimeGarden.Item> items = HttpReq.get(HOST + "/resources")
                .form("subject", bgmId)
                .form("pageSize", 200)
                .form("duplicate", false)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    JsonArray resources = jsonObject.getAsJsonArray("resources");
                    return GsonStatic.fromJsonList(resources, AnimeGarden.Item.class);
                });

        // 仅保留可归属字幕组的资源
        items = items.stream()
                .filter(it -> it.getFansub() != null)
                .toList();

        Map<String, List<AnimeGarden.Item>> groupIdMap = items.stream()
                .collect(Collectors.groupingBy(it -> it.getFansub().getId()));

        // 字幕组元数据：按最近更新倒序后按 id 去重
        List<AnimeGarden.Group> rawGroups = items.stream()
                .map(it -> {
                    AnimeGarden.Fansub fansub = it.getFansub();
                    String rss = StrUtil.format(
                            "{}/feed.xml?subject={}&fansub={}",
                            HOST, bgmId, fansub.getName().replace("&", "%26")
                    );
                    return new AnimeGarden.Group()
                            .setId(fansub.getId())
                            .setName(fansub.getName())
                            .setLastUpdatedAt(it.getCreatedAt())
                            .setRss(rss)
                            .setBgmId(bgmId);
                })
                .sorted(Comparator.comparing(AnimeGarden.Group::getLastUpdatedAt).reversed())
                .collect(Collectors.toList());
        rawGroups = CollUtil.distinct(rawGroups, AnimeGarden.Group::getId, false);

        List<SourceGroup> result = new ArrayList<>();
        for (AnimeGarden.Group raw : rawGroups) {
            List<AnimeGarden.Item> groupItems = groupIdMap.get(raw.getId());
            raw.setItems(groupItems);

            List<SourceResource> resources = groupItems.stream()
                    .map(item -> new SourceResource()
                            .setTitle(item.getTitle())
                            .setSize(item.getSize())
                            .setRaw(item))
                    .toList();

            result.add(new SourceGroup()
                    .setId(raw.getId())
                    .setName(raw.getName())
                    .setRss(raw.getRss())
                    .setBgmId(bgmId)
                    .setItems(new ArrayList<>(resources))
                    .setRaw(raw));
        }
        return result;
    }

    private static SourceAnime toCanonical(AnimeGarden.Subject raw) {
        return new SourceAnime()
                .setBgmId(raw.getId())
                .setSourceId(raw.getId())
                .setTitle(raw.getName())
                .setExists(raw.getExists())
                .setScore(raw.getScore())
                .setRaw(raw);
    }
}

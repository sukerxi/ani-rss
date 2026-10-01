package ani.rss.service.source.mikan;

import ani.rss.cache.CacheUtils;
import ani.rss.entity.Mikan;
import ani.rss.entity.MikanInfo;
import ani.rss.service.source.*;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.BgmUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Mikan 订阅源实现：HTML 抓取 + 映射为统一模型。
 */
@Slf4j
@Service
public class MikanSource extends AbstractBangumiSource {

    /**
     * 并发补全 bgmId 时对 Mikan 的最大并发数, 避免整季番剧同时抓取触发站点限流
     */
    private static final Semaphore BGM_ID_PERMITS = new Semaphore(8);

    @Override
    public String code() {
        return "mikan";
    }

    @Override
    public SourceListResult fetchList(SourceQuery query) {
        Set<String> subscribedMikanIds = AniUtil.ANI_LIST.stream()
                .map(AniUtil::getBangumiId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());

        String text = StrUtil.blankToDefault(query.getText(), "");
        String regex = "^id: (\\d+)$";

        SourceListResult result = new SourceListResult();

        if (ReUtil.contains(regex, text)) {
            String mikanId = ReUtil.get(regex, text, 1);
            SourceAnime item = toCanonical(MikanParser.getMikanInfo(mikanId));
            SourceWeek week = new SourceWeek()
                    .setWeekLabel("Search")
                    .setItems(List.of(item));
            result.setWeeks(new ArrayList<>(List.of(week)));
            result.setTotalItem(1);
            return result;
        }

        String host = MikanParser.getHost();
        String url = host;
        if (StrUtil.isNotBlank(text)) {
            url = host + "/Home/Search?searchstr=" + URLUtil.encodeBlank(text);
        } else {
            Integer year = query.getYear();
            String seasonStr = query.getSeason();
            if (Objects.nonNull(year) && StrUtil.isNotBlank(seasonStr)) {
                url = StrUtil.format(
                        "{}/Home/BangumiCoverFlowByDayOfWeek?year={}&seasonStr={}",
                        url, year, seasonStr
                );
            }
        }

        // 页面级响应缓存 (fresh 2min / stale 30min) + 状态校验 + 幂等重试
        Document document = Jsoup.parse(MikanParser.fetchHtml(url, query.isRefresh()));

        result.setSeasons(MikanParser.parseSeasons(document).stream()
                .map(MikanSource::toCanonicalSeason)
                .toList());

        List<SourceWeek> weeks = MikanParser
                .parseCoverFlow(document, host, subscribedMikanIds)
                .stream()
                .map(MikanSource::toCanonicalWeek)
                .toList();
        result.setWeeks(new ArrayList<>(weeks));

        result.setTotalItem(result.getWeeks().stream()
                .mapToInt(week -> week.getItems().size())
                .sum());
        return result;
    }

    @Override
    protected List<SourceGroup> fetchGroups(BangumiRef ref) {
        String url = ref.getUrl();
        String host = MikanParser.getHost();
        Document document = Jsoup.parse(MikanParser.fetchHtml(url, false));
        return MikanParser.parseGroups(document, host)
                .stream()
                .map(MikanSource::toCanonicalGroup)
                .toList();
    }

    /**
     * 字幕组页体量较大，缓存富化后的结果 1 分钟，避免展开/添加时短时间重复抓取
     */
    @Override
    public List<SourceGroup> groups(BangumiRef ref) {
        String cacheKey = "mikan:groups:" + ref.getUrl();
        List<SourceGroup> cached = CacheUtils.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<SourceGroup> groups = super.groups(ref);
        CacheUtils.put(cacheKey, groups, TimeUnit.MINUTES.toMillis(1));
        return groups;
    }

    /**
     * 并行从 bgmUrl / mikanId 补全 bgmId
     * <p>
     * 并发受 {@link #BGM_ID_PERMITS} 限制；未命中映射的番剧由
     * {@link BgmUtil#getSubjectIdByMikanId} 做短时负缓存，避免每次刷新重复抓取
     */
    @Override
    protected void resolveBgmIds(List<SourceAnime> items) {
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = items.stream()
                    .map(anime -> CompletableFuture.runAsync(() -> {
                        BGM_ID_PERMITS.acquireUninterruptibly();
                        try {
                            if (StrUtil.isNotBlank(anime.getBgmId())) {
                                return;
                            }
                            if (StrUtil.isNotBlank(anime.getBgmUrl())) {
                                anime.setBgmId(BgmUtil.getSubjectId(anime.getBgmUrl()));
                            } else {
                                String sourceUrl = StrUtil.blankToDefault(anime.getSourceUrl(), "");
                                String mikanId = ReUtil.get("\\d+(/)?$", sourceUrl, 0);
                                anime.setBgmId(BgmUtil.getSubjectIdByMikanId(mikanId));
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        } finally {
                            BGM_ID_PERMITS.release();
                        }
                    }, executorService))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    private static SourceWeek toCanonicalWeek(Mikan.Week raw) {
        return new SourceWeek()
                .setWeekLabel(raw.getWeekLabel())
                .setItems(raw.getItems().stream().map(MikanSource::toCanonical).toList())
                .setRaw(raw);
    }

    private static SourceAnime toCanonical(MikanInfo raw) {
        return new SourceAnime()
                .setBgmId(raw.getBgmId())
                .setSourceId(StrUtil.blankToDefault(
                        ReUtil.get("\\d+(/)?$", StrUtil.blankToDefault(raw.getUrl(), ""), 0), ""))
                .setSourceUrl(raw.getUrl())
                .setBgmUrl(raw.getBgmUrl())
                .setTitle(raw.getTitle())
                .setExists(raw.getExists())
                .setRaw(raw);
    }

    private static SourceSeason toCanonicalSeason(Mikan.Season raw) {
        return new SourceSeason()
                .setYear(raw.getYear())
                .setSeason(raw.getSeason())
                .setSeasonLabel(raw.getSeasonLabel())
                .setSelect(raw.getSelect());
    }

    private static SourceGroup toCanonicalGroup(Mikan.Group raw) {
        List<SourceResource> resources = raw.getItems().stream()
                .map(item -> new SourceResource()
                        .setTitle(item.getTitle())
                        .setSize(item.getSize())
                        .setRaw(item))
                .toList();
        return new SourceGroup()
                .setId(raw.getSubgroupId())
                .setName(raw.getLabel())
                .setRss(raw.getRss())
                .setBgmUrl(raw.getBgmUrl())
                .setUpdateDay(raw.getUpdateDay())
                .setItems(resources)
                .setRaw(raw);
    }
}

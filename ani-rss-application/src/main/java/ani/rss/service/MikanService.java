package ani.rss.service;

import ani.rss.entity.Ani;
import ani.rss.entity.Mikan;
import ani.rss.entity.MikanInfo;
import ani.rss.service.source.BangumiRef;
import ani.rss.service.source.SourceAnime;
import ani.rss.service.source.SourceListResult;
import ani.rss.service.source.SourceQuery;
import ani.rss.service.source.SourceSeason;
import ani.rss.service.source.SourceWeek;
import ani.rss.service.source.mikan.MikanParser;
import ani.rss.service.source.mikan.MikanSource;
import ani.rss.util.other.AniUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mikan 订阅 facade：保持 Controller / MCP / 静态回填的既有签名，
 * 抓取与富化委托 {@link MikanSource}，统一模型在此映射回 Mikan 实体。
 */
@Slf4j
@Service
public class MikanService {

    @Resource
    private MikanSource mikanSource;

    public static String getMikanHost() {
        return MikanParser.getHost();
    }

    /**
     * 搜索mikan番剧列表
     *
     * @param text   关键字
     * @param season 集度
     * @return Mikan
     */
    public Mikan list(String text, Mikan.Season season) {
        return toMikan(mikanSource.list(toQuery(text, season)));
    }

    public Mikan search(String text, Mikan.Season season) {
        // 原始结果：未经评分/订阅态/星期排序富化
        return toMikan(mikanSource.fetchList(toQuery(text, season)));
    }

    /**
     * 获取番剧字幕组
     *
     * @param url 链接
     * @return 字幕组列表
     */
    public List<Mikan.Group> getGroups(String url) {
        return mikanSource.groups(new BangumiRef().setUrl(url)).stream()
                .map(group -> {
                    Mikan.Group raw = (Mikan.Group) group.getRaw();
                    raw.setGroupRegex(group.getGroupRegex());
                    return raw;
                })
                .toList();
    }

    public static MikanInfo getMikanInfo(String bangumiId) {
        return MikanParser.getMikanInfo(bangumiId);
    }

    public static void getMikanInfo(Ani ani, String subgroupId) {
        String bangumiId = AniUtil.getBangumiId(ani);
        if (StrUtil.isBlank(bangumiId)) {
            return;
        }

        MikanInfo mikanInfo = MikanParser.getMikanInfo(bangumiId);
        Assert.notNull(mikanInfo, "未获取到 Mikan 信息");

        String title = mikanInfo.getTitle();
        String bgmUrl = mikanInfo.getBgmUrl();
        List<Mikan.Group> groups = mikanInfo.getGroups();

        ani
                .setMikanTitle(title)
                .setBgmUrl(bgmUrl);

        for (Mikan.Group group : groups) {
            String id = group.getSubgroupId();
            String label = group.getLabel();
            if (subgroupId.equals(id)) {
                ani.setSubgroup(label);
            }
        }
    }

    /**
     * 从rss中获得字幕组id
     *
     * @param url 链接
     * @return 字幕组id
     */
    public static String getSubgroupId(String url) {
        Map<String, String> decodeParamMap = HttpUtil.decodeParamMap(url, StandardCharsets.UTF_8);

        for (String k : decodeParamMap.keySet()) {
            String v = decodeParamMap.get(k);
            if (k.equalsIgnoreCase("subgroupid")) {
                return v;
            }
        }
        return "";
    }

    private static SourceQuery toQuery(String text, Mikan.Season season) {
        SourceQuery query = new SourceQuery();
        query.setText(text);
        if (Objects.nonNull(season)) {
            query.setYear(season.getYear())
                    .setSeason(season.getSeason());
        }
        return query;
    }

    private static Mikan toMikan(SourceListResult result) {
        List<Mikan.Season> seasons = result.getSeasons().stream()
                .map(MikanService::toMikanSeason)
                .toList();
        List<Mikan.Week> weeks = result.getWeeks().stream()
                .map(MikanService::toMikanWeek)
                .toList();
        return new Mikan()
                .setSeasons(seasons)
                .setWeeks(weeks)
                .setTotalItem(result.getTotalItem());
    }

    private static Mikan.Season toMikanSeason(SourceSeason season) {
        return new Mikan.Season()
                .setYear(season.getYear())
                .setSeason(season.getSeason())
                .setSeasonLabel(season.getSeasonLabel())
                .setSelect(season.getSelect());
    }

    private static Mikan.Week toMikanWeek(SourceWeek week) {
        return new Mikan.Week()
                .setWeekLabel(week.getWeekLabel())
                .setItems(week.getItems().stream()
                        .map(MikanService::toMikanInfo)
                        .toList());
    }

    private static MikanInfo toMikanInfo(SourceAnime anime) {
        MikanInfo raw = (MikanInfo) anime.getRaw();
        if (StrUtil.isNotBlank(anime.getBgmId())) {
            raw.setBgmId(anime.getBgmId());
        }
        if (anime.getScore() != null) {
            raw.setScore(anime.getScore());
        }
        if (anime.getExists() != null) {
            raw.setExists(anime.getExists());
        }
        return raw;
    }
}

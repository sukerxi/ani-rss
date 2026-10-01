package ani.rss.service.source;

import ani.rss.commons.FileUtils;
import ani.rss.commons.GroupRegexUtils;
import ani.rss.comparator.WeekComparator;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.BgmUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅源抽象基类：收敛三个源的公共后处理流程。
 *
 * <p>列表流程：源内 id 补 bgmId（钩子）→ 源特有过滤（钩子）→
 * bgm.tv 统一评分 + 订阅状态 → 组内按评分倒序 → 空星期过滤 + 从今天起排序。</p>
 *
 * <p>字幕组流程：数值大小统一格式化 → 字幕组匹配正则。</p>
 */
@Slf4j
public abstract class AbstractBangumiSource implements BangumiSource {

    @Override
    public SourceListResult list(SourceQuery query) {
        SourceListResult result = fetchList(query);

        // AnimeGarden bgmUrl 单番合成周：结果已就绪，跳过统一富化
        if (result.isBypassEnrichment()) {
            return result;
        }

        List<SourceWeek> weeks = result.getWeeks();

        List<SourceAnime> allItems = weeks.stream()
                .map(SourceWeek::getItems)
                .flatMap(List::stream)
                .toList();

        // 1. 源内链接/id → bgmId（Mikan 独有，并行抓取）
        resolveBgmIds(allItems);

        // 2. 源特有过滤（AniBT 非搜索时过滤零发布番剧）
        // 钩子可能返回 Stream.toList()/List.of 等不可变列表，统一复制为可变列表，
        // 后续评分排序为原地操作
        for (SourceWeek week : weeks) {
            week.setItems(new ArrayList<>(filterAnimes(week.getItems(), query)));
        }

        // 3. 统一从 bgm.tv 取评分（唯一口径），并入订阅状态
        List<String> bgmIds = allItems.stream()
                .map(SourceAnime::getBgmId)
                .filter(StrUtil::isNotBlank)
                .toList();
        Map<String, Double> scoreMap = BgmUtil.getScores(bgmIds);
        Set<String> subscribedBgmIds = AniUtil.getSubscribedBgmIds();

        for (SourceWeek week : weeks) {
            for (SourceAnime anime : week.getItems()) {
                String bgmId = StrUtil.blankToDefault(anime.getBgmId(), "");
                anime.setScore(resolveScore(anime, scoreMap.get(bgmId)));
                anime.setExists(Boolean.TRUE.equals(anime.getExists())
                        || subscribedBgmIds.contains(bgmId));
            }
            week.getItems().sort(Comparator.comparingDouble(SourceAnime::getScore).reversed());
        }

        // 4. 丢弃空星期，统一按「今天起向后」排序
        WeekComparator weekComparator = new WeekComparator();
        weeks = weeks.stream()
                .filter(week -> CollUtil.isNotEmpty(week.getItems()))
                .sorted(Comparator.comparing(SourceWeek::getWeekLabel, weekComparator))
                .toList();

        result.setWeeks(weeks);
        result.setTotalItem(weeks.stream().mapToInt(week -> week.getItems().size()).sum());
        return result;
    }

    @Override
    public List<SourceGroup> groups(BangumiRef ref) {
        List<SourceGroup> groups = fetchGroups(ref);

        for (SourceGroup group : groups) {
            for (SourceResource resource : group.getItems()) {
                if (resource.getSize() != null) {
                    // 数值型大小统一格式化；Mikan 资源 size 为 null，沿用站点文本
                    resource.setFormatSize(FileUtils.formatSize(resource.getSize(), true));
                }
            }
            group.setGroupRegex(
                    GroupRegexUtils.toGroupRegx(group.getItems(), SourceResource::getTitle)
            );
        }
        return groups;
    }

    /**
     * 抓取并映射番剧列表（原始状态，尚未富化）
     */
    protected abstract SourceListResult fetchList(SourceQuery query);

    /**
     * 抓取并映射字幕组列表（尚未格式化/生成正则）
     */
    protected abstract List<SourceGroup> fetchGroups(BangumiRef ref);

    /**
     * 补全缺失的 bgmId，默认无需补全（AniBT / AnimeGarden 原始数据已携带）
     */
    protected void resolveBgmIds(List<SourceAnime> items) {
    }

    /**
     * 源特有过滤，默认不过滤
     */
    protected List<SourceAnime> filterAnimes(List<SourceAnime> items, SourceQuery query) {
        return items;
    }

    /**
     * 评分解析，默认直接采用 bgm 评分（缺失为 0）；AniBT 在 bgm 评分未就绪时回退源站评分
     */
    protected double resolveScore(SourceAnime anime, Double bgmScore) {
        return bgmScore == null ? 0.0 : bgmScore;
    }
}

package ani.rss.service;

import ani.rss.entity.AnimeGarden;
import ani.rss.service.source.BangumiRef;
import ani.rss.service.source.SourceGroup;
import ani.rss.service.source.SourceListResult;
import ani.rss.service.source.SourceQuery;
import ani.rss.service.source.SourceResource;
import ani.rss.service.source.SourceWeek;
import ani.rss.service.source.animegarden.AnimeGardenSource;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AnimeGarden 订阅 facade：保持 Controller / MCP 既有签名，
 * 抓取与富化委托 {@link AnimeGardenSource}，统一模型在此映射回 AnimeGarden 实体。
 */
@Service
public class AnimeGardenService {

    @Resource
    private AnimeGardenSource animeGardenSource;

    public List<AnimeGarden.Week> list(String bgmUrl) {
        return list(bgmUrl, false);
    }

    public List<AnimeGarden.Week> list(String bgmUrl, boolean refresh) {
        SourceQuery query = new SourceQuery()
                .setBgmUrl(bgmUrl)
                .setRefresh(refresh);

        SourceListResult result = animeGardenSource.list(query);

        List<AnimeGarden.Week> weeks = new ArrayList<>();
        for (SourceWeek week : result.getWeeks()) {
            AnimeGarden.Week rawWeek = (AnimeGarden.Week) week.getRaw();

            List<AnimeGarden.Subject> subjects = new ArrayList<>();
            for (var anime : week.getItems()) {
                AnimeGarden.Subject raw = (AnimeGarden.Subject) anime.getRaw();
                if (anime.getScore() != null) {
                    raw.setScore(anime.getScore());
                }
                if (anime.getExists() != null) {
                    raw.setExists(anime.getExists());
                }
                subjects.add(raw);
            }
            rawWeek.setSubjects(subjects);
            weeks.add(rawWeek);
        }
        return weeks;
    }

    public List<AnimeGarden.Group> group(String bgmId) {
        return animeGardenSource.groups(new BangumiRef().setBgmId(bgmId)).stream()
                .map(AnimeGardenService::toRawGroup)
                .toList();
    }

    private static AnimeGarden.Group toRawGroup(SourceGroup group) {
        AnimeGarden.Group raw = (AnimeGarden.Group) group.getRaw();
        raw.setGroupRegex(group.getGroupRegex());

        for (SourceResource resource : group.getItems()) {
            AnimeGarden.Item rawItem = (AnimeGarden.Item) resource.getRaw();
            if (resource.getFormatSize() != null) {
                rawItem.setFormatSize(resource.getFormatSize());
            }
        }
        return raw;
    }
}

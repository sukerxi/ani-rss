package ani.rss.service;

import ani.rss.entity.AniBT;
import ani.rss.entity.dto.AniBTQueryDTO;
import ani.rss.service.source.BangumiRef;
import ani.rss.service.source.SourceGroup;
import ani.rss.service.source.SourceListResult;
import ani.rss.service.source.SourceQuery;
import ani.rss.service.source.SourceResource;
import ani.rss.service.source.SourceWeek;
import ani.rss.service.source.anibt.AniBTSource;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AniBT 订阅 facade：保持 Controller / MCP 既有签名，
 * 抓取与富化委托 {@link AniBTSource}，统一模型在此映射回 AniBT 实体。
 */
@Service
public class AniBTService {

    @Resource
    private AniBTSource aniBTSource;

    public AniBT list(AniBTQueryDTO dto) {
        SourceQuery query = new SourceQuery()
                .setText(dto.getTitle())
                .setBgmUrl(dto.getBgmUrl())
                .setSeason(dto.getSeason())
                .setRefresh(Boolean.TRUE.equals(dto.getRefresh()));

        SourceListResult result = aniBTSource.list(query);

        AniBT raw = (AniBT) result.getRaw();
        raw.setRequestedSeason(result.getRequestedSeason());

        List<AniBT.ByWeekday> byWeekday = new ArrayList<>();
        for (SourceWeek week : result.getWeeks()) {
            AniBT.ByWeekday rawWeek = (AniBT.ByWeekday) week.getRaw();

            List<AniBT.Anime> animes = new ArrayList<>();
            for (var anime : week.getItems()) {
                AniBT.Anime rawAnime = (AniBT.Anime) anime.getRaw();
                rawAnime.setScore(anime.getScore())
                        .setExists(anime.getExists());
                animes.add(rawAnime);
            }
            rawWeek.setAnimes(animes);
            byWeekday.add(rawWeek);
        }
        raw.setByWeekday(byWeekday);
        return raw;
    }

    public List<AniBT.Group> getGroups(String bgmId) {
        return aniBTSource.groups(new BangumiRef().setBgmId(bgmId)).stream()
                .map(AniBTService::toRawGroup)
                .toList();
    }

    private static AniBT.Group toRawGroup(SourceGroup group) {
        AniBT.Group raw = (AniBT.Group) group.getRaw();
        raw.setGroupRegex(group.getGroupRegex());

        for (SourceResource resource : group.getItems()) {
            AniBT.Item rawItem = (AniBT.Item) resource.getRaw();
            if (resource.getFormatSize() != null) {
                rawItem.setFormatSize(resource.getFormatSize());
            }
        }
        return raw;
    }
}

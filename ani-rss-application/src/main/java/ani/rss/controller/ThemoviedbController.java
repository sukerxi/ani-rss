package ani.rss.controller;

import ani.rss.annotation.Auth;
import ani.rss.entity.Ani;
import ani.rss.entity.dto.ThemoviedbDTO;
import ani.rss.entity.vo.ThemoviedbGroupSeasonVO;
import ani.rss.entity.vo.ThemoviedbVO;
import ani.rss.entity.web.Result;
import ani.rss.entity.web.ResultCode;
import ani.rss.util.other.RenameUtil;
import ani.rss.util.other.TmdbUtils;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import wushuo.tmdb.api.entity.Tmdb;
import wushuo.tmdb.api.entity.TmdbEpisode;
import wushuo.tmdb.api.entity.TmdbGroup;
import wushuo.tmdb.api.entity.TmdbSeason;
import wushuo.tmdb.api.enums.TmdbTypeEnum;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class ThemoviedbController extends BaseController {

    @Auth
    @Operation(summary = "获取TMDB标题")
    @PostMapping("/getThemoviedbName")
    public Result<ThemoviedbVO> getThemoviedbName(@RequestBody ThemoviedbDTO dto) {
        Boolean ova = dto.getOva();
        String tmdbId = dto.getTmdbId();
        String title = dto.getTitle();

        Assert.isTrue(
                StrUtil.isNotBlank(tmdbId) || StrUtil.isNotBlank(title),
                "TmdbId 或 标题 不能为空"
        );

        Optional<Tmdb> tmdbOpt;

        if (StrUtil.isNotBlank(tmdbId)) {
            Tmdb tmdb = new Tmdb().setId(tmdbId);
            TmdbTypeEnum tmdbType = ova ? TmdbTypeEnum.MOVIE : TmdbTypeEnum.TV;
            tmdbOpt = TmdbUtils.getTmdb(tmdb, tmdbType);
        } else {
            title = RenameUtil.renameDel(title, false);
            tmdbOpt = ova ? TmdbUtils.getTmdbMovie(title) : TmdbUtils.getTmdbTv(title);
        }

        Assert.isFalse(tmdbOpt.isEmpty(), "未获取到 TMDB");

        Tmdb tmdb = tmdbOpt.get();

        String themoviedbName = TmdbUtils.getFinalName(tmdb);

        ThemoviedbVO themoviedbVO = new ThemoviedbVO()
                .setTmdb(tmdb)
                .setThemoviedbName(themoviedbName);

        Result<ThemoviedbVO> result = new Result<ThemoviedbVO>()
                .setCode(ResultCode.HTTP_OK)
                .setMessage("获取 TMDB 成功")
                .setData(themoviedbVO);
        if (StrUtil.isBlank(themoviedbName)) {
            result.setCode(ResultCode.HTTP_INTERNAL_ERROR)
                    .setMessage("获取 TMDB 失败");
        }
        return result;
    }

    @Auth
    @Operation(summary = "获取TMDB剧集组")
    @PostMapping("/getThemoviedbGroup")
    public Result<List<TmdbGroup>> getThemoviedbGroup(@RequestBody Ani ani) {
        Tmdb tmdb = ani.getTmdb();
        Objects.requireNonNull(tmdb, "tmdb is null");
        Assert.notBlank(tmdb.getId(), "tmdb is null");
        List<TmdbGroup> tmdbGroup = TmdbUtils.getTmdbGroup(tmdb);
        return Result.success(tmdbGroup);
    }

    @Auth
    @Operation(summary = "获取TMDB剧集组分段详情")
    @PostMapping("/getThemoviedbGroupDetail")
    public Result<List<ThemoviedbGroupSeasonVO>> getThemoviedbGroupDetail(@RequestBody ThemoviedbDTO dto) {
        String groupId = dto.getGroupId();
        Assert.notBlank(groupId, "剧集组 id 不能为空");

        List<ThemoviedbGroupSeasonVO> list = TmdbUtils.getTmdbGroupSeasonList(groupId)
                .stream()
                .map(this::toGroupSeasonVO)
                .toList();
        return Result.success(list);
    }

    /**
     * 剧集组分段转 VO
     *
     * @param season 分段
     * @return VO
     */
    private ThemoviedbGroupSeasonVO toGroupSeasonVO(TmdbSeason season) {
        List<TmdbEpisode> episodes = Optional.ofNullable(season.getEpisodes()).orElse(List.of());

        Integer order = season.getOrder();
        String name = StrUtil.blankToDefault(season.getName(), StrFormatter.format("分段 {}", order));

        List<Date> airDates = episodes.stream()
                .map(TmdbEpisode::getAirDate)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        // 整段来自同一个原始季时，给出原始季与集数范围
        Set<Integer> seasonNumbers = episodes.stream()
                .map(TmdbEpisode::getSeasonNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Integer originSeasonNumber = seasonNumbers.size() == 1 ? seasonNumbers.iterator().next() : null;

        Integer originEpisodeStart = null;
        Integer originEpisodeEnd = null;
        if (Objects.nonNull(originSeasonNumber)) {
            List<Integer> episodeNumbers = episodes.stream()
                    .map(TmdbEpisode::getEpisodeNumber)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
            if (!episodeNumbers.isEmpty()) {
                originEpisodeStart = episodeNumbers.get(0);
                originEpisodeEnd = episodeNumbers.get(episodeNumbers.size() - 1);
            }
        }

        String startAirDate = airDates.isEmpty() ? null
                : DateUtil.format(airDates.get(0), DatePattern.NORM_DATE_PATTERN);
        String endAirDate = airDates.isEmpty() ? null
                : DateUtil.format(airDates.get(airDates.size() - 1), DatePattern.NORM_DATE_PATTERN);

        return new ThemoviedbGroupSeasonVO()
                .setOrder(order)
                .setName(name)
                .setEpisodeCount(episodes.size())
                .setStartAirDate(startAirDate)
                .setEndAirDate(endAirDate)
                .setOriginSeasonNumber(originSeasonNumber)
                .setOriginEpisodeStart(originEpisodeStart)
                .setOriginEpisodeEnd(originEpisodeEnd);
    }
}

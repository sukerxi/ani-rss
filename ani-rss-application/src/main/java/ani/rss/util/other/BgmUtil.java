package ani.rss.util.other;

import ani.rss.cache.CacheUtils;
import ani.rss.cache.PersistCacheUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.entity.*;
import ani.rss.entity.web.ContentType;
import ani.rss.entity.web.Header;
import ani.rss.enums.BgmTokenTypeEnum;
import ani.rss.service.DownloadService;
import ani.rss.service.MikanService;
import ani.rss.util.basic.HttpReq;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.*;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import wushuo.tmdb.api.entity.Tmdb;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BGM
 */
@Slf4j
public class BgmUtil {
    private static final Config CONFIG = ConfigUtil.CONFIG;

    /**
     * 获取bgm名称
     *
     * @param bgmInfo bgm信息
     * @return 名称
     */
    public static String getFinalName(BgmInfo bgmInfo) {
        Boolean bgmJpName = CONFIG.getBgmJpName();

        String name = bgmInfo.getName();
        String nameCn = bgmInfo.getNameCn();
        String title = StrUtil.blankToDefault(nameCn, name);

        if (bgmJpName) {
            title = name;
        }

        if (StrUtil.isBlank(title)) {
            title = "无标题";
        }

        return title.trim();
    }

    /**
     * 获取bgm名称
     *
     * @param bgmInfo bgm信息
     * @param tmdb    tmdb信息
     * @return 名称
     */
    public static String getFinalName(BgmInfo bgmInfo, Tmdb tmdb) {
        Boolean titleYear = CONFIG.getTitleYear();

        String title = getFinalName(bgmInfo);

        Date date = bgmInfo.getDate();

        if (titleYear) {
            title = StrFormatter.format("{} ({})", title, DateUtil.year(date));
        }

        return TmdbUtils.getFinalName(title, tmdb);
    }

    /**
     * 搜索番剧
     *
     * @param name 名称
     * @return 番剧列表
     */
    public static List<BgmInfo> search(String name) {
        if (StrUtil.isBlank(name)) {
            return new ArrayList<>();
        }

        name = name.replace("1/2", "½");

        String bgmApi = CONFIG.getBgmApi();

        String url = UrlBuilder.of(bgmApi + "/search/subject/" + name)
                .addQuery("type", 2)
                .addQuery("max_results", 25)
                .addQuery("responseGroup", "small")
                .toString();

        HttpRequest httpRequest = HttpReq.get(url);

        return setToken(httpRequest)
                .thenFunction(res -> {
                    if (!res.isOk()) {
                        return new ArrayList<>();
                    }
                    String body = res.body();
                    if (!JSONUtil.isTypeJSON(body)) {
                        return new ArrayList<>();
                    }

                    JsonObject jsonObject = GsonStatic.fromJson(body, JsonObject.class);
                    JsonElement code = jsonObject.get("code");
                    if (Objects.nonNull(code)) {
                        if (code.getAsInt() == 404) {
                            return new ArrayList<>();
                        }
                    }
                    JsonArray list = jsonObject.getAsJsonArray("list");
                    List<BgmInfo> bgmInfos = GsonStatic.fromJsonList(list, BgmInfo.class);
                    for (BgmInfo bgmInfo : bgmInfos) {
                        Integer season = getSeasonByBgmInfo(bgmInfo);
                        bgmInfo.setSeason(season);
                    }
                    return bgmInfos;
                });
    }

    /**
     * 查找番剧id
     *
     * @param bgmName 名称
     * @param s       季度
     * @return 番剧id
     */
    public static String getSubjectId(String bgmName, Integer s) {
        if (StrUtil.isBlank(bgmName)) {
            return "";
        }

        String key = "BGM_getSubjectId:" + bgmName;

        if (CacheUtils.containsKey(key)) {
            return CacheUtils.get(key);
        }
        List<BgmInfo> list = search(bgmName);

        // 仅保留季数一致
        list = list.stream()
                .filter(bgmInfo -> bgmInfo.getSeason() == s.intValue())
                .toList();

        if (list.isEmpty()) {
            return "";
        }

        String id = "";
        // 优先使用名称与季完全匹配的
        for (BgmInfo bgmInfo : list) {
            String name = bgmInfo.getName();
            String nameCn = bgmInfo.getNameCn();

            if (List.of(name, nameCn).contains(bgmName)) {
                id = bgmInfo.getId();
                break;
            }
        }
        // 次之使用第一个
        if (StrUtil.isBlank(id)) {
            id = list.get(0).getId();
        }
        ThreadUtil.sleep(1000);
        CacheUtils.put(key, id, TimeUnit.MINUTES.toMillis(10));
        return id;
    }

    /**
     * 获取番剧id
     *
     * @param ani 订阅
     * @return 番剧id
     */
    public static String getSubjectId(Ani ani) {
        String bgmUrl = ani.getBgmUrl();
        if (StrUtil.isBlank(bgmUrl) && "mikan".equals(ani.getType())) {
            String bangumiId = AniUtil.getBangumiId(ani);
            Assert.notBlank(bangumiId, "无法取得 bangumiId, {}", ani.getTitle());
            MikanService.getMikanInfo(ani, "");
            bgmUrl = ani.getUrl();
        }
        return getSubjectId(bgmUrl);
    }

    /**
     * 获取番剧id
     *
     * @param bgmUrl bangmui链接
     * @return 番剧id
     */
    public static String getSubjectId(String bgmUrl) {
        Assert.notBlank(bgmUrl, "bgmUrl 不能为空");
        String regStr = "^http(s)?://.+\\/(\\d+)(\\/)?$";
        Assert.isTrue(ReUtil.contains(regStr, bgmUrl));
        return ReUtil.get(regStr, bgmUrl, 2);
    }

    /**
     * 获取视频列表
     *
     * @param subjectId 番剧id
     * @param type      0正常 1番外
     * @return 视频列表
     */
    public static List<BgmEpisodes.BgmEpisode> getEpisodes(String subjectId, Integer type) {
        ThreadUtil.sleep(500);
        Objects.requireNonNull(subjectId);
        String bgmApi = CONFIG.getBgmApi();
        HttpRequest httpRequest = HttpReq.get(bgmApi + "/v0/episodes");
        setToken(httpRequest);

        return httpRequest
                .form("subject_id", subjectId)
                .form("type", 0)
                .form("limit", 1000)
                .form("offset", 0)
                .thenFunction(res -> {
                    if (!res.isOk()) {
                        return List.of();
                    }

                    String body = res.body();
                    if (!JSONUtil.isTypeJSON(body)) {
                        return List.of();
                    }

                    return GsonStatic.fromJson(body, BgmEpisodes.class)
                            .getData()
                            .stream()
                            .filter(bgmEpisode -> {
                                if (Objects.nonNull(type)) {
                                    return type.intValue() == bgmEpisode.getType();
                                }
                                return true;
                            })
                            .toList();
                });
    }

    public static BgmMe me() {
        String bgmToken = CONFIG.getBgmToken();
        Assert.notBlank(bgmToken, "BgmToken 未填写");

        String key = "BGM_me:" + bgmToken;

        String me = CacheUtils.get(key);
        if (StrUtil.isNotBlank(me)) {
            return GsonStatic.fromJson(me, BgmMe.class);
        }

        String bgmApi = CONFIG.getBgmApi();
        BgmMe bgmMe = setToken(HttpReq.get(bgmApi + "/v0/me"))
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    return GsonStatic.fromJson(res.body(), BgmMe.class);
                });

        CacheUtils.put(key, GsonStatic.toJson(bgmMe), TimeUnit.MINUTES.toMillis(10));
        return bgmMe;
    }

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    public static String username() {
        BgmMe me = me();
        return Opt.of(me)
                .map(BgmMe::getUsername)
                .filter(Objects::nonNull)
                .filter(StrUtil::isNotBlank)
                .orElse(String.valueOf(me.getId()));
    }

    /**
     * 对番剧进行评分
     */
    public static Integer rate(String subjectId, Integer rate) {
        if (Objects.isNull(rate)) {
            // 获取评分
            String username = username();
            String bgmApi = CONFIG.getBgmApi();
            return setToken(HttpReq.get(bgmApi + "/v0/users/" + username + "/collections/" + subjectId))
                    .thenFunction(res -> {
                        if (res.getStatus() == 404) {
                            return 0;
                        }
                        HttpReq.assertStatus(res);
                        JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                        return jsonObject.get("rate").getAsInt();
                    });
        }

        String bgmApi = CONFIG.getBgmApi();

        Map<String, Integer> bodyMap = Map.of(
                "type", 3,
                "rate", rate
        );

        setToken(HttpReq.post(bgmApi + "/v0/users/-/collections/" + subjectId, bodyMap))
                .contentType(ContentType.JSON)
                .then(HttpReq::assertStatus);
        return rate;
    }

    /**
     * 收藏番剧
     *
     * @param subjectId 番剧id
     */
    public static void collections(String subjectId) {
        Assert.notBlank(subjectId, "subjectId 不能为空");

        String key = "BGM_collections:" + subjectId;
        if (CacheUtils.containsKey(key)) {
            return;
        }
        CacheUtils.put(key, subjectId, TimeUnit.MINUTES.toMillis(5));

        String username = username();

        // 如果已经订阅，则不再订阅
        String bgmApi = CONFIG.getBgmApi();
        Boolean ok = setToken(HttpReq.get(bgmApi + "/v0/users/" + username + "/collections/" + subjectId))
                .thenFunction(HttpResponse::isOk);

        if (ok) {
            // 已经收藏
            log.info("已收藏番剧: {}", subjectId);
            return;
        }

        Map<String, Integer> bodyMap = Map.of("type", 3);

        setToken(HttpReq.post(bgmApi + "/v0/users/-/collections/" + subjectId, bodyMap))
                .contentType(ContentType.JSON)
                .thenFunction(HttpResponse::isOk);
    }

    /**
     * 获取 EpisodeId
     *
     * @param subjectId 番剧id
     * @param e         集数
     * @return 集id
     */
    public static String getEpisodeId(String subjectId, Double e) {
        String epId = "";
        String sortId = "";

        String key = "BGM_getEpisodeId:" + subjectId;

        List<BgmEpisodes.BgmEpisode> episodes = CacheUtils.get(key);
        if (Objects.isNull(episodes)) {
            episodes = getEpisodes(subjectId, 0);
            CacheUtils.put(key, episodes, TimeUnit.MINUTES.toMillis(10));
        }
        for (BgmEpisodes.BgmEpisode itemObject : episodes) {
            int ep = itemObject.getEp();
            int sort = itemObject.getSort();
            if (ep == e) {
                epId = itemObject.getId();
                break;
            }
            if (sort == e) {
                sortId = itemObject.getId();
                break;
            }
        }

        return StrUtil.blankToDefault(epId, sortId);
    }

    /**
     * 标记
     *
     * @param episodeId 集id
     * @param type      0 未看过, 1 想看, 2 看过
     */
    public static void collectionsEpisodes(String episodeId, Integer type) {
        ThreadUtil.sleep(500);
        Objects.requireNonNull(episodeId);

        // bgm点格子前先判断状态，防止刷屏 #142
        String bgmApi = CONFIG.getBgmApi();
        JsonObject jsonObject = setToken(HttpReq.get(bgmApi + "/v0/users/-/collections/-/episodes/" + episodeId))
                .contentType(ContentType.JSON)
                .thenFunction(res -> GsonStatic.fromJson(res.body(), JsonObject.class));

        int typeNow = jsonObject.get("type").getAsInt();
        if (type == typeNow) {
            return;
        }

        // 间隔 500 毫秒, 防止流控
        ThreadUtil.sleep(500);

        setToken(HttpReq.put(bgmApi + "/v0/users/-/collections/-/episodes/" + episodeId))
                .contentType(ContentType.JSON)
                .body(GsonStatic.toJson(Map.of("type", type)))
                .thenFunction(HttpResponse::isOk);
    }

    /**
     * 获取对应的bgm信息
     *
     * @param ani     订阅
     * @param isCache 是否使用缓存
     * @return bgm信息
     */
    public static BgmInfo getBgmInfo(Ani ani, Boolean isCache) {
        String subjectId = getSubjectId(ani);
        Assert.notBlank(subjectId, "无法取得 subjectId, {}", ani.getTitle());
        return getBgmInfo(subjectId, isCache);
    }

    /**
     * 获取对应的bgm信息
     *
     * @param ani 订阅
     * @return bgm信息
     */
    public static BgmInfo getBgmInfo(Ani ani) {
        String subjectId = getSubjectId(ani);
        return getBgmInfo(subjectId);
    }

    /**
     * 获取对应的bgm信息
     *
     * @param subjectId 番剧id
     * @return bgm信息
     */
    public static BgmInfo getBgmInfo(String subjectId) {
        return getBgmInfo(subjectId, false);
    }

    /**
     * 获取对应的bgm信息
     *
     * @param subjectId 番剧id
     * @param isCache   是否使用缓存
     * @return bgm信息
     */
    public static BgmInfo getBgmInfo(String subjectId, Boolean isCache) {
        String bgmApi = CONFIG.getBgmApi();

        Function<HttpResponse, BgmInfo> fun = res -> {
            HttpReq.assertStatus(res);
            String body = res.body();
            Assert.isTrue(JSONUtil.isTypeJSON(body), "no json");
            BgmInfo bgmInfo = GsonStatic.fromJson(body, BgmInfo.class);

            String name = bgmInfo.getName();
            String nameCn = bgmInfo.getNameCn();

            name = RenameUtil.getName(name);
            nameCn = RenameUtil.getName(nameCn);

            int season = getSeasonByBgmInfo(bgmInfo);

            Date date = bgmInfo.getDate();
            date = ObjectUtil.defaultIfNull(date, new Date());

            return bgmInfo
                    .setName(name)
                    .setNameCn(nameCn)
                    .setSeason(season)
                    .setDate(date);
        };

        if (!isCache) {
            // 不使用缓存
            HttpRequest httpRequest = HttpReq.get(bgmApi + "/v0/subjects/" + subjectId);
            return setToken(httpRequest).thenFunction(fun);
        }

        AtomicReference<BgmInfo> bgmInfoAR = new AtomicReference<>();
        AtomicReference<BgmInfo> bgmInfoCacheAR = new AtomicReference<>();

        // 并行获取bgm信息
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> {
                    // 不使用缓存
                    HttpRequest httpRequest = HttpReq.get(bgmApi + "/v0/subjects/" + subjectId);
                    try {
                        BgmInfo bgmInfo = setToken(httpRequest)
                                .thenFunction(fun);
                        bgmInfoAR.set(bgmInfo);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }),
                CompletableFuture.runAsync(() -> {
                    HttpRequest httpRequest = HttpReq
                            .get("https://cache.wushuo.top/bgm/subjects/" + subjectId);
                    try {
                        BgmInfo bgmInfo = httpRequest
                                .thenFunction(fun);
                        bgmInfoCacheAR.set(bgmInfo);
                    } catch (Exception ignored) {
                    }
                })
        ).join();

        BgmInfo bgmInfo = bgmInfoAR.get();

        bgmInfo = ObjectUtil.defaultIfNull(bgmInfo, bgmInfoCacheAR.get());

        Assert.notNull(bgmInfo, "获取 bgmInfo 失败!");

        return bgmInfo;
    }

    public static Integer getSeasonByBgmInfo(BgmInfo bgmInfo) {
        String name = bgmInfo.getName();
        String nameCn = bgmInfo.getNameCn();
        List<BgmInfo.Tag> tags = bgmInfo.getTags();
        tags = ObjectUtil.defaultIfNull(tags, new ArrayList<>());
        List<JsonObject> infobox = bgmInfo.getInfobox();
        infobox = ObjectUtil.defaultIfNull(infobox, new ArrayList<>());

        // 从标签获取季
        for (BgmInfo.Tag tag : tags) {
            String tagName = tag.getName();
            int season = getSeasonByName(tagName);
            if (season > 1) {
                return season;
            }
        }

        // 从中文标题获取季
        if (StrUtil.isNotBlank(nameCn)) {
            int season = getSeasonByName(nameCn);
            if (season > 1) {
                return season;
            }
        }

        // 从原标题获取季
        if (StrUtil.isNotBlank(name)) {
            int season = getSeasonByName(name);
            if (season > 1) {
                return season;
            }
        }

        // 从别名获取
        for (JsonObject jsonObject : infobox) {
            String key = jsonObject.get("key").getAsString();
            if (!key.equals("别名")) {
                continue;
            }
            JsonArray value = jsonObject.getAsJsonArray("value");
            for (JsonElement jsonElement : value.asList()) {
                JsonObject item = jsonElement.getAsJsonObject();
                String v = item.get("v").getAsString();
                int season = getSeasonByName(v);
                if (season > 1) {
                    return season;
                }
            }
        }

        // 都未匹配到 返回季度1
        return 1;
    }

    /**
     * 季数解析规则，<b>顺序即优先级</b>，首个成功解析的规则生效。
     */
    private static final List<Pattern> SEASON_REGEX_LIST = List.of(
            // 第一季 第一期
            Pattern.compile("第 ?([一二三四五六七八九十百千]+) ?[季期]"),
            // Season 1
            Pattern.compile("[Ss]eason ?(\\d+)"),
            // 1st Season
            Pattern.compile("(\\d+)(st|nd|rd|th) ?[Ss]eason"),
            // S1 S01
            Pattern.compile("[Ss](\\d+)$")
    );

    public static Integer getSeasonByName(String name) {
        for (Pattern pattern : SEASON_REGEX_LIST) {
            Matcher matcher = pattern.matcher(name);
            if (!matcher.find()) {
                continue;
            }

            try {
                String s = matcher.group(1);
                int season = NumberUtil.isInteger(s)
                        ? Integer.parseInt(s)
                        : Convert.chineseToNumber(s);
                if (season >= 1) {
                    return season;
                }
            } catch (Exception ignored) {
            }
        }
        return 1;
    }

    /**
     * 设置token
     *
     * @param httpRequest HttpRequest
     * @return HttpRequest
     */
    public static HttpRequest setToken(HttpRequest httpRequest) {
        String bgmToken = CONFIG.getBgmToken();

        if (StrUtil.isNotBlank(bgmToken)) {
            httpRequest.header(Header.AUTHORIZATION, "Bearer " + bgmToken);
        }

        ThreadUtil.sleep(RandomUtil.randomInt(500, 1000));
        return httpRequest;
    }

    /**
     * 获取剩余过期时间 单位: 天
     *
     * @return 天数
     */
    public static Integer getExpiresDays() {
        String bgmToken = CONFIG.getBgmToken();
        if (StrUtil.isBlank(bgmToken)) {
            return 0;
        }
        long expires = HttpReq.post("https://bgm.tv/oauth/token_status")
                .form("access_token", bgmToken)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    return jsonObject.get("expires").getAsLong() * 1000L;
                });

        long currentTimeMillis = System.currentTimeMillis();

        int days = 0;

        if (expires > currentTimeMillis) {
            days = Math.toIntExact(TimeUnit.MILLISECONDS.toDays(expires - currentTimeMillis));
        }
        return days;
    }

    /**
     * 刷新token
     */
    public static void refreshToken() {
        BgmTokenTypeEnum bgmTokenType = CONFIG.getBgmTokenType();
        if (bgmTokenType != BgmTokenTypeEnum.AUTO) {
            return;
        }

        String bgmToken = CONFIG.getBgmToken();
        if (StrUtil.isBlank(bgmToken)) {
            return;
        }

        long days = getExpiresDays();

        if (days >= 3) {
            return;
        }

        String bgmAppID = CONFIG.getBgmAppID();
        String bgmAppSecret = CONFIG.getBgmAppSecret();
        String bgmRefreshToken = CONFIG.getBgmRefreshToken();
        String bgmRedirectUri = CONFIG.getBgmRedirectUri();

        if (StrUtil.isBlank(bgmAppID)) {
            return;
        }
        if (StrUtil.isBlank(bgmAppSecret)) {
            return;
        }
        if (StrUtil.isBlank(bgmRefreshToken)) {
            return;
        }
        if (StrUtil.isBlank(bgmRedirectUri)) {
            return;
        }

        Map<String, String> bodyMap = Map.of(
                "grant_type", "refresh_token",
                "client_id", bgmAppID,
                "client_secret", bgmAppSecret,
                "refresh_token", bgmRefreshToken,
                "redirect_uri", bgmRedirectUri
        );

        HttpReq.post("https://bgm.tv/oauth/access_token", bodyMap)
                .then(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    String accessToken = jsonObject.get("access_token").getAsString();
                    String refreshToken = jsonObject.get("refresh_token").getAsString();
                    CONFIG.setBgmToken(accessToken)
                            .setBgmRefreshToken(refreshToken);
                });

        ConfigUtil.sync();
        log.info("BgmToken 已自动刷新");
    }

    /**
     * 获取每集的标题
     *
     * @param ani 订阅
     * @return 每集标题
     */
    public static Map<Integer, BgmEpisodes.BgmEpisode> getEpisodeTitleMap(Ani ani) {
        Map<Integer, BgmEpisodes.BgmEpisode> episodeTitleMap = new HashMap<>();

        if (Objects.isNull(ani)) {
            return episodeTitleMap;
        }

        String subjectId = getSubjectId(ani);

        if (StrUtil.isBlank(subjectId)) {
            return episodeTitleMap;
        }

        if (ani.getOva()) {
            return episodeTitleMap;
        }

        String key = "BGM_getEpisodeTitleMap:" + subjectId;

        Map<Integer, BgmEpisodes.BgmEpisode> cacheMap = CacheUtils.get(key);
        if (Objects.nonNull(cacheMap)) {
            return cacheMap;
        }

        try {
            List<BgmEpisodes.BgmEpisode> data = getEpisodes(subjectId, 0);
            for (BgmEpisodes.BgmEpisode bgmEpisode : data) {
                int ep = bgmEpisode.getEp();

                String defaultEpisodeTitle = "第" + ep + "集";

                String name = bgmEpisode.getName();
                String nameCn = bgmEpisode.getNameCn();

                nameCn = StrUtil.blankToDefault(nameCn, name);

                nameCn = RenameUtil.getName(nameCn);
                name = RenameUtil.getName(name);

                nameCn = StrUtil.blankToDefault(nameCn, defaultEpisodeTitle);
                name = StrUtil.blankToDefault(name, defaultEpisodeTitle);

                bgmEpisode.setName(name)
                        .setNameCn(nameCn);

                episodeTitleMap.put(ep, bgmEpisode);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        CacheUtils.put(key, episodeTitleMap, TimeUnit.MINUTES.toMillis(5));
        return episodeTitleMap;
    }

    /**
     * 获取集数 排除ova
     *
     * @param bgmInfo bgm信息
     * @return 集数
     */
    public static Integer getEps(BgmInfo bgmInfo) {
        int eps = bgmInfo.getEps();
        String subjectId = bgmInfo.getId();
        if (eps < 1) {
            return 0;
        }
        try {
            int size = BgmUtil.getEpisodes(subjectId, 0).size();
            if (size > 0) {
                // 获取集数不为零
                eps = size;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return eps;
    }

    /**
     * bgm转ani
     *
     * @param bgmInfo bgm信息
     * @param ani     订阅
     * @return 订阅
     */
    public static Ani toAni(BgmInfo bgmInfo, Ani ani) {
        String bgmImageSize = CONFIG.getBgmImageSize();
        // 使用tmdb标题
        Boolean tmdb = CONFIG.getTmdb();

        String title = BgmUtil.getFinalName(bgmInfo);

        int eps = getEps(bgmInfo);

        BgmInfo.Images images = bgmInfo.getImages();

        String image = (String) ReflectUtil.getFieldValue(images, bgmImageSize);

        double score = Optional.ofNullable(bgmInfo.getRating())
                .map(BgmInfo.Rating::getScore)
                .orElse(0.0);

        String platform = bgmInfo.getPlatform();

        boolean ova = List.of("OVA", "剧场版").contains(platform.toUpperCase());

        Date date = bgmInfo.getDate();

        ani
                .setBgmUrl("https://bgm.tv/subject/" + bgmInfo.getId())
                // 标题
                .setTitle(title)
                .setJpTitle(bgmInfo.getName())
                // 季
                .setSeason(bgmInfo.getSeason())
                // 总集数
                .setTotalEpisodeNumber(eps)
                // 剧场版
                .setOva(ova)
                // 评分
                .setScore(score)
                // 发布日期"
                .setReleaseDate(date)
                // 图片http地址
                .setImage(image)
                // 本地图片地址
                .setCover(AniUtil.saveCover(image));

        // 获取tmdb标题
        String themoviedbName = TmdbUtils.getFinalName(ani);

        // 是否使用tmdb标题
        if (StrUtil.isNotBlank(themoviedbName) && tmdb) {
            ani
                    .setTitle(themoviedbName);
        } else {
            title = BgmUtil.getFinalName(bgmInfo, ani.getTmdb());
            ani.setTitle(title);
        }

        // 下载位置
        DownloadService downloadService = SpringUtil.getBean(DownloadService.class);
        String downloadPath = downloadService.getDownloadPath(ani);

        String completedPathTemplate = CONFIG.getCompletedPathTemplate();

        if (ova) {
            // 剧场版默认不开启摸鱼检测
            ani.setProcrastinating(false);
        }

        return ani
                // tmdb 标题
                .setThemoviedbName(themoviedbName)
                .setCustomDownloadPathTemplate(downloadPath)
                .setCustomCompletedPathTemplate(completedPathTemplate);
    }


    /**
     * 评分内存缓存前缀, 正分 fresh 12 小时, 0 分/失败 fresh 5 分钟 (避免重试风暴)
     */
    private static final String SCORE_KEY_PREFIX = "BGM_score:";
    private static final long SCORE_FRESH_MS = TimeUnit.HOURS.toMillis(12);
    private static final long SCORE_EMPTY_FRESH_MS = TimeUnit.MINUTES.toMillis(5);
    /**
     * 评分持久缓存有效期, 过期后重新请求 bgm.tv 校准
     */
    private static final long SCORE_PERSIST_TTL_MS = TimeUnit.HOURS.toMillis(24);

    /**
     * 同一 bgm id 的并发评分请求合并 (single-flight)
     */
    private static final Map<String, CompletableFuture<Double>> SCORE_LOADING = new ConcurrentHashMap<>();

    /**
     * 评分请求最大并发, 避免整季无界请求打爆 bgm.tv
     */
    private static final Semaphore SCORE_PERMITS = new Semaphore(16);

    /**
     * bgm.tv 全局请求最小间隔, 约 12 req/s
     */
    private static final long SCORE_MIN_INTERVAL_MS = 80;
    private static final AtomicLong LAST_SCORE_REQUEST = new AtomicLong(0);

    /**
     * 从 bgm.tv 获取番剧评分
     * <p>
     * 内存缓存 (正分 12h / 0 分 5min) + 持久化缓存 (正分, 跨重启复用);
     * 未命中时经过全局限流与并发控制请求源站, 同一 id 的并发请求只发出一次
     *
     * @param subjectId bgm 番剧 id
     * @return 评分, 0.0 表示暂无评分或本次获取失败
     */
    public static Double getScore(String subjectId) {
        if (StrUtil.isBlank(subjectId)) {
            return 0.0;
        }

        String key = SCORE_KEY_PREFIX + subjectId;

        Double cacheScore = CacheUtils.get(key);
        if (Objects.nonNull(cacheScore)) {
            return cacheScore;
        }

        // 持久化正分缓存, 重启后仍可立即渲染
        Object persisted = getBgmScoreCache().get("score:" + subjectId);
        Double persistedScore = Objects.isNull(persisted) ? null : Convert.toDouble(persisted);
        if (Objects.nonNull(persistedScore) && persistedScore > 0) {
            CacheUtils.put(key, persistedScore, SCORE_FRESH_MS);
            return persistedScore;
        }

        return requestScore(subjectId, key);
    }

    /**
     * 单次评分请求, 合并同 id 并发并写回缓存
     */
    private static Double requestScore(String subjectId, String cacheKey) {
        CompletableFuture<Double> future = SCORE_LOADING.computeIfAbsent(subjectId,
                id -> CompletableFuture.supplyAsync(() -> doRequestScore(id), SCORE_EXECUTOR));
        try {
            Double score = ObjectUtil.defaultIfNull(future.join(), 0.0);
            if (score > 0) {
                CacheUtils.put(cacheKey, score, SCORE_FRESH_MS);
            } else {
                // 0 分/失败短缓存, 避免失败时反复请求
                CacheUtils.put(cacheKey, 0.0, SCORE_EMPTY_FRESH_MS);
            }
            return score;
        } finally {
            SCORE_LOADING.remove(subjectId, future);
        }
    }

    private static final ExecutorService SCORE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 真正请求 bgm.tv 评分, 受全局并发与速率限制, 替代每个请求前的固定 sleep
     */
    private static Double doRequestScore(String subjectId) {
        SCORE_PERMITS.acquireUninterruptibly();
        try {
            awaitScoreRateLimit();

            String bgmApi = CONFIG.getBgmApi();
            HttpRequest request = HttpReq.get(bgmApi + "/v0/subjects/" + subjectId);
            String bgmToken = CONFIG.getBgmToken();
            if (StrUtil.isNotBlank(bgmToken)) {
                request.header(Header.AUTHORIZATION, "Bearer " + bgmToken);
            }

            return request.thenFunction(res -> {
                if (!res.isOk()) {
                    return 0.0;
                }
                String body = res.body();
                if (!JSONUtil.isTypeJSON(body)) {
                    return 0.0;
                }
                BgmInfo bgmInfo = GsonStatic.fromJson(body, BgmInfo.class);
                return Opt.ofNullable(bgmInfo.getRating())
                        .map(BgmInfo.Rating::getScore)
                        .orElse(0.0);
            });
        } catch (Exception e) {
            log.warn("获取 bgm 评分失败 id={}, {}", subjectId, e.getMessage());
            return 0.0;
        } finally {
            SCORE_PERMITS.release();
        }
    }

    /**
     * 全局评分请求节流, 保证对 bgm.tv 的请求间隔不短于 {@link #SCORE_MIN_INTERVAL_MS}
     */
    private static void awaitScoreRateLimit() {
        while (true) {
            long now = System.currentTimeMillis();
            long last = LAST_SCORE_REQUEST.get();
            long next = Math.max(now, last + SCORE_MIN_INTERVAL_MS);
            if (LAST_SCORE_REQUEST.compareAndSet(last, next)) {
                long wait = next - now;
                if (wait > 0) {
                    ThreadUtil.sleep(wait);
                }
                return;
            }
        }
    }

    /**
     * 批量并行获取 bgm.tv 评分
     * <p>
     * 各订阅源评分的唯一入口, 保证口径一致; 内部使用虚拟线程并发, 自动去重,
     * 真实请求受 {@link #SCORE_PERMITS} 与 {@link #SCORE_MIN_INTERVAL_MS} 约束;
     * 新得到的正分会批量持久化一次, 供重启后复用
     *
     * @param subjectIds bgm 番剧 id 集合
     * @return k: bgm id, v: 评分 (未找到或获取失败为 0.0)
     */
    public static Map<String, Double> getScores(Collection<String> subjectIds) {
        Map<String, Double> scoreMap = new ConcurrentHashMap<>();

        List<String> ids = subjectIds.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return scoreMap;
        }

        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = ids.stream()
                    .map(id -> CompletableFuture.runAsync(
                            () -> scoreMap.put(id, getScore(id)), executorService))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        // 仅持久化新增或变化的正分, 单次写盘
        Map<String, Object> toPersist = new HashMap<>();
        scoreMap.forEach((id, score) -> {
            if (score > 0) {
                String persistKey = "score:" + id;
                Object existed = getBgmScoreCache().get(persistKey);
                if (!Objects.equals(Convert.toDouble(existed), score)) {
                    toPersist.put(persistKey, score);
                }
            }
        });
        if (!toPersist.isEmpty()) {
            try {
                getBgmScoreCache().putAll(toPersist,
                        System.currentTimeMillis() + SCORE_PERSIST_TTL_MS);
            } catch (Exception e) {
                log.warn("持久化 bgm 评分失败: {}", e.getMessage());
            }
        }

        return scoreMap;
    }

    private volatile static PersistCacheUtils bgmScoreCache;

    private synchronized static PersistCacheUtils getBgmScoreCache() {
        if (Objects.isNull(bgmScoreCache)) {
            File configDir = ConfigUtil.getConfigDir();
            File cacheFile = new File(configDir, "cache/bgm-score.json");
            bgmScoreCache = PersistCacheUtils.getInstance(cacheFile);
        }
        return bgmScoreCache;
    }

    private volatile static PersistCacheUtils mikanBgmCache;

    private synchronized static PersistCacheUtils getMikanBgmCache() {
        if (Objects.isNull(mikanBgmCache)) {
            File configDir = ConfigUtil.getConfigDir();
            File cacheFile = new File(configDir, "cache/mikan-bgm.json");
            mikanBgmCache = PersistCacheUtils.getInstance(cacheFile);
        }
        return mikanBgmCache;
    }

    /**
     * 通过 Mikan 番剧 id 获取对应的 bgm.tv 番剧 id
     * <p>
     * 抓取 Mikan 番剧详情页中的 Bangumi 链接, 映射关系长期持久化缓存
     *
     * @param mikanId mikan 番剧 id
     * @return bgm 番剧 id, 空字符串表示未找到或获取失败
     */
    public static String getSubjectIdByMikanId(String mikanId) {
        if (StrUtil.isBlank(mikanId)) {
            return "";
        }

        String cacheKey = "bgmId:" + mikanId;

        String bgmId = getMikanBgmCache().get(cacheKey);
        if (StrUtil.isNotBlank(bgmId)) {
            return bgmId;
        }

        String url = MikanService.getMikanHost() + "/Home/Bangumi/" + mikanId;

        try {
            bgmId = HttpReq.get(url)
                    .timeout(1000 * 10)
                    .thenFunction(res -> {
                        if (!res.isOk()) {
                            return "";
                        }
                        Document document = Jsoup.parse(res.body());
                        for (Element bangumiInfo : document.select(".bangumi-info")) {
                            if (!"Bangumi番组计划链接：".equals(bangumiInfo.ownText())) {
                                continue;
                            }
                            String bgmUrl = bangumiInfo.selectFirst("a").attr("href");
                            if (StrUtil.isBlank(bgmUrl)) {
                                return "";
                            }
                            return getSubjectId(bgmUrl);
                        }
                        return "";
                    });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return "";
        }

        if (StrUtil.isNotBlank(bgmId)) {
            getMikanBgmCache().put(cacheKey, bgmId);
        }
        return bgmId;
    }

}

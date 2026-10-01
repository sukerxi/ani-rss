package ani.rss.service.source.mikan;

import ani.rss.cache.HttpResponseCache;
import ani.rss.entity.Config;
import ani.rss.entity.Mikan;
import ani.rss.entity.MikanInfo;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Mikan 页面解析器：列表封面流、详情页、字幕组表格的解析全部收敛于此，
 * 供 {@link MikanSource} 与订阅回填的静态方法共用，避免解析逻辑分叉。
 *
 * <p>页面抓取统一走 {@link #fetchHtml}：带响应缓存、幂等重试与非 2xx 状态校验，
 * 站点错误页/拦截页会明确报错，不再静默产出空数据。</p>
 */
@Slf4j
public class MikanParser {

    /**
     * 页面 HTML 响应缓存：fresh 2 分钟不请求源站，stale 30 分钟内源站失败时回显旧页面
     */
    private static final Duration PAGE_FRESH_TTL = Duration.ofMinutes(2);
    private static final Duration PAGE_STALE_TTL = Duration.ofMinutes(30);

    /**
     * Cloudflare 拦截页特征，用于把拦截页与正常的空结果区分开
     */
    private static final List<String> CHALLENGE_MARKS = List.of(
            "Just a moment", "Attention Required", "cf-error-details", "Enable JavaScript and cookies to continue"
    );

    /**
     * Mikan 站点地址，缺省 https://mikanani.me
     */
    public static String getHost() {
        Config config = ConfigUtil.CONFIG;
        return StrUtil.blankToDefault(config.getMikanHost(), "https://mikanani.me");
    }

    /**
     * 番剧详情页链接，host 统一取 URI 形式（去除多余路径与尾斜杠）
     *
     * @param bangumiId mikan 番剧 id
     * @return 详情页链接
     */
    public static String detailUrl(String bangumiId) {
        URI host = URLUtil.getHost(URLUtil.url(getHost()));
        return host + "/Home/Bangumi/" + bangumiId;
    }

    /**
     * 抓取 Mikan 页面 HTML
     * <p>
     * 带响应缓存（fresh 2 分钟 / stale 30 分钟）、幂等重试；非 2xx 状态与
     * Cloudflare 拦截页会抛出异常，避免把错误页解析成空数据
     *
     * @param url   页面链接
     * @param force 强制刷新，绕过 fresh 缓存
     * @return HTML
     */
    public static String fetchHtml(String url, boolean force) {
        String html = HttpResponseCache.get("mikan:html:" + url,
                () -> HttpReq.getRetry(url),
                PAGE_FRESH_TTL, PAGE_STALE_TTL, force);

        for (String mark : CHALLENGE_MARKS) {
            if (html.contains(mark)) {
                throw new IllegalStateException(StrUtil.format("Mikan 页面被拦截或异常: {}", url));
            }
        }
        return html;
    }

    /**
     * 抓取番剧详情页并解析为 MikanInfo（封面/标题/bgmUrl/字幕组）
     *
     * @param bangumiId mikan 番剧 id
     * @return MikanInfo
     * @throws IllegalStateException 页面结构异常（错误页/改版）时抛出
     */
    public static MikanInfo getMikanInfo(String bangumiId) {
        URI host = URLUtil.getHost(URLUtil.url(getHost()));
        String url = detailUrl(bangumiId);

        Document html = Jsoup.parse(fetchHtml(url, false));

        MikanInfo mikanInfo = new MikanInfo();
        mikanInfo.setUrl(url);

        Element bangumiTitle = html.selectFirst(".bangumi-title");
        if (Objects.isNull(bangumiTitle) || StrUtil.isBlank(bangumiTitle.text())) {
            throw new IllegalStateException(StrUtil.format("Mikan 详情页解析失败, 未找到标题: {}", url));
        }
        mikanInfo.setTitle(bangumiTitle.text().trim());

        Element cover = html.selectFirst(".content > img");
        if (Objects.nonNull(cover)) {
            mikanInfo.setCover(absUrl(host.toString(), cover.attr("src")));
        }

        String bgmUrl = parseBgmUrl(html);
        if (StrUtil.isNotBlank(bgmUrl)) {
            mikanInfo.setBgmUrl(bgmUrl);
        }

        // 与旧版一致：rss/torrent 前缀用配置 host（url/cover 用 URI 剥离 host）
        mikanInfo.setGroups(parseGroups(html, getHost()));
        return mikanInfo;
    }

    /**
     * 解析日期选择器上的全部季度选项
     */
    public static List<Mikan.Season> parseSeasons(Document document) {
        List<Mikan.Season> seasons = new ArrayList<>();

        Element dateSelect = document.selectFirst(".date-select");
        if (Objects.isNull(dateSelect)) {
            return seasons;
        }
        Element dateTextElement = dateSelect.selectFirst(".date-text");
        String dateText = Objects.isNull(dateTextElement) ? "" : dateTextElement.text().trim();
        Element dropdownMenu = dateSelect.selectFirst(".dropdown-menu");
        if (Objects.isNull(dropdownMenu)) {
            return seasons;
        }

        for (Element child : dropdownMenu.children()) {
            Elements seasonItems = child.select("li");
            // 首个 li 为当前季度占位，跳过；结构异常（无 li）时跳过该组而非中断整页解析
            for (int i = 1; i < seasonItems.size(); i++) {
                Element a = seasonItems.get(i).selectFirst("a");
                if (Objects.isNull(a)) {
                    continue;
                }
                String dataYear = a.attr("data-year").trim();
                String dataSeason = a.attr("data-season").trim();
                if (!NumberUtil.isInteger(dataYear) || StrUtil.isBlank(dataSeason)) {
                    continue;
                }
                String selectLabel = StrUtil.format("{} {}", dataYear, dataSeason);
                seasons.add(
                        new Mikan.Season()
                                .setYear(Integer.parseInt(dataYear))
                                .setSeason(dataSeason)
                                .setSeasonLabel(selectLabel)
                                .setSelect(dateText.startsWith(selectLabel))
                );
            }
        }
        return seasons;
    }

    /**
     * 解析封面流：搜索页（.an-ul）返回单个 "Search" 周；
     * 封面流（.sk-bangumi）按星期返回，空分组跳过
     *
     * <p>新季度刚开播、Mikan 尚未收录时页面可能完全没有列表容器，
     * 此时返回空结果而不报错，避免阻断季度选择等后续逻辑；
     * 真正的错误页（非 2xx / Cloudflare 拦截）已在 {@link #fetchHtml} 拦截。</p>
     */
    public static List<Mikan.Week> parseCoverFlow(Document document, String host,
                                                  Set<String> subscribedMikanIds) {
        List<Mikan.Week> weeks = new ArrayList<>();

        Elements skBangumis = document.select(".sk-bangumi");
        if (skBangumis.isEmpty()) {
            Element anUl = document.selectFirst(".an-ul");
            if (Objects.isNull(anUl)) {
                // 跨季初期新季度尚未收录时页面没有任何列表容器，属合法空结果，返回空列表
                log.warn("Mikan 页面未找到番剧列表容器 (.sk-bangumi / .an-ul), 按空结果处理");
                return weeks;
            }
            List<MikanInfo> mikanInfos = parseAnimeList(anUl, host, subscribedMikanIds);
            weeks.add(new Mikan.Week().setItems(mikanInfos).setWeekLabel("Search"));
            return weeks;
        }

        for (Element skBangumi : skBangumis) {
            Element labelElement = skBangumi.children().first();
            if (Objects.isNull(labelElement) || StrUtil.isBlank(labelElement.text())) {
                continue;
            }
            List<MikanInfo> mikanInfos = parseAnimeList(skBangumi, host, subscribedMikanIds);
            if (mikanInfos.isEmpty()) {
                continue;
            }
            weeks.add(new Mikan.Week().setWeekLabel(labelElement.text().trim()).setItems(mikanInfos));
        }
        return weeks;
    }

    /**
     * 解析一个容器内的番剧封面列表，容器为 null 或结构异常时跳过对应项
     */
    private static List<MikanInfo> parseAnimeList(Element container, String host,
                                                  Set<String> subscribedMikanIds) {
        List<MikanInfo> mikanInfos = new ArrayList<>();
        if (Objects.isNull(container)) {
            return mikanInfos;
        }
        for (Element li : container.select("li")) {
            Element span = li.selectFirst("span");
            if (Objects.isNull(span)) {
                continue;
            }
            String cover = absUrl(host, StrUtil.blankToDefault(span.attr("data-src"), span.attr("src")));
            Elements aa = li.select("a");
            if (aa.isEmpty()) {
                continue;
            }
            String href = absUrl(host, aa.get(0).attr("href"));
            String title = aa.get(0).text();

            String id = ReUtil.get("\\d+(/)?$", href, 0);
            id = StrUtil.blankToDefault(id, "");

            mikanInfos.add(
                    new MikanInfo()
                            .setCover(cover)
                            .setTitle(title)
                            .setUrl(href)
                            .setExists(subscribedMikanIds.contains(id))
                            .setScore(0.0)
            );
        }
        return mikanInfos;
    }

    /**
     * 解析详情页上的全部字幕组
     */
    public static List<Mikan.Group> parseGroups(Document document, String host) {
        String bgmUrl = parseBgmUrl(document);
        List<Mikan.Group> groups = new ArrayList<>();

        Elements subgroupTitles = document.select(".leftbar-item");
        for (Element subgroupText : subgroupTitles) {
            Element nameElement = subgroupText.selectFirst("a.subgroup-name");
            if (Objects.isNull(nameElement)) {
                continue;
            }
            String label = nameElement.text().trim();
            // id 锚点，例如 #213
            String id = nameElement.attr("data-anchor");

            Element anchor = StrUtil.isNotBlank(id) ? document.selectFirst(id) : null;
            if (Objects.isNull(anchor)) {
                continue;
            }

            Mikan.Group group = new Mikan.Group();
            group.setItems(new ArrayList<>())
                    .setBgmUrl(bgmUrl)
                    .setLabel(label)
                    .setSubgroupId(id.replace("#", "").trim())
                    .setUpdateDay(subgroupText.select(".date").text().trim());

            Element rssElement = anchor.selectFirst(".mikan-rss");
            if (Objects.nonNull(rssElement)) {
                group.setRss(absUrl(host, rssElement.attr("href")));
            }

            Element table = anchor.nextElementSibling();
            if (Objects.nonNull(table)) {
                parseGroupItems(table, host, group.getItems());
            }
            groups.add(group);
        }
        return groups;
    }

    /**
     * 解析某个字幕组的资源表格，单行结构异常时跳过该行
     */
    private static void parseGroupItems(Element table, String host, List<Mikan.Item> items) {
        Element tbody = table.selectFirst("tbody");
        if (Objects.isNull(tbody)) {
            return;
        }
        for (Element tr : tbody.children()) {
            Elements links = tr.select("a");
            Elements tds = tr.select("td");
            if (links.size() < 3 || tds.size() < 4) {
                continue;
            }
            String title = links.get(0).ownText();
            // 磁力链接优先按 data-clipboard-text 定位，站点列序变化时不会取错链接
            Element magnetLink = tr.selectFirst("a[data-clipboard-text]");
            if (Objects.isNull(magnetLink)) {
                magnetLink = links.get(1);
            }
            String magnet = magnetLink.attr("data-clipboard-text");
            String formatSize = tds.get(2).text().trim();
            String dateStr = tds.get(3).text().trim();
            String torrent = absUrl(host, links.get(2).attr("href"));

            Date createdAt = null;
            try {
                createdAt = DateUtil.parse(dateStr);
            } catch (Exception ignored) {
            }

            items.add(
                    new Mikan.Item()
                            .setTitle(title)
                            .setMagnet(magnet)
                            .setFormatSize(formatSize)
                            .setCreatedAt(createdAt)
                            .setTorrent(torrent)
            );
        }
    }

    /**
     * 解析详情页上的 Bangumi 链接
     */
    public static String parseBgmUrl(Document document) {
        Elements bangumiInfos = document.select(".bangumi-info");
        for (Element bangumiInfo : bangumiInfos) {
            if (!"Bangumi番组计划链接：".equals(bangumiInfo.ownText())) {
                continue;
            }
            Element link = bangumiInfo.selectFirst("a");
            if (Objects.nonNull(link) && StrUtil.isNotBlank(link.attr("href"))) {
                return link.attr("href").trim();
            }
        }
        return "";
    }

    /**
     * 拼接站点链接：相对路径补 host，已是完整链接（含协议相对）则原样返回
     */
    private static String absUrl(String host, String path) {
        if (StrUtil.isBlank(path)) {
            return "";
        }
        if (StrUtil.startWithAnyIgnoreCase(path, "http://", "https://")) {
            return path;
        }
        if (path.startsWith("//")) {
            // 协议相对链接，沿用配置 host 的协议
            return StrUtil.subBefore(host, "//", false) + path;
        }
        return host + path;
    }
}

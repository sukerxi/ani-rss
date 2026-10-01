package ani.rss.service.source.mikan;

import ani.rss.entity.Mikan;
import ani.rss.entity.MikanInfo;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import ani.rss.entity.Config;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Mikan 页面解析器：列表封面流、详情页、字幕组表格的解析全部收敛于此，
 * 供 {@link MikanSource} 与订阅回填的静态方法共用，避免解析逻辑分叉。
 */
public class MikanParser {

    /**
     * Mikan 站点地址，缺省 https://mikanani.me
     */
    public static String getHost() {
        Config config = ConfigUtil.CONFIG;
        return StrUtil.blankToDefault(config.getMikanHost(), "https://mikanani.me");
    }

    /**
     * 抓取番剧详情页并解析为 MikanInfo（封面/标题/bgmUrl/字幕组）
     */
    public static MikanInfo getMikanInfo(String bangumiId) {
        URI host = URLUtil.getHost(URLUtil.url(getHost()));
        String url = host + "/Home/Bangumi/" + bangumiId;
        return HttpReq.get(url)
                .thenFunction(res -> {
                    MikanInfo mikanInfo = new MikanInfo();
                    mikanInfo.setUrl(url);

                    Document html = Jsoup.parse(res.body());

                    Element cover = html.selectFirst(".content > img");
                    if (Objects.nonNull(cover)) {
                        mikanInfo.setCover(host + cover.attr("src"));
                    }

                    Element bangumiTitle = html.selectFirst(".bangumi-title");
                    if (Objects.nonNull(bangumiTitle)) {
                        mikanInfo.setTitle(bangumiTitle.text().trim());
                    }

                    String bgmUrl = parseBgmUrl(html);
                    if (StrUtil.isNotBlank(bgmUrl)) {
                        mikanInfo.setBgmUrl(bgmUrl);
                    }

                    // 与旧版一致：rss/torrent 前缀用配置 host（url/cover 用 URI 剥离 host）
                    mikanInfo.setGroups(parseGroups(html, getHost()));
                    return mikanInfo;
                });
    }

    /**
     * 解析日期选择器上的全部季度选项
     */
    public static List<Mikan.Season> parseSeasons(Document document) {
        List<Mikan.Season> seasons = new ArrayList<>();

        Elements dateSelects = document.select(".date-select");
        if (dateSelects.isEmpty()) {
            return seasons;
        }
        Element dateSelect = dateSelects.get(0);
        String dateText = dateSelect.select(".date-text").text().trim();
        Element dropdownMenu = dateSelect.selectFirst(".dropdown-menu");
        for (Element child : dropdownMenu.children()) {
            Elements seasonItems = child.select("li");
            for (Element seasonItem : seasonItems.subList(1, seasonItems.size())) {
                Element a = seasonItem.selectFirst("a");
                String dataYear = a.attr("data-year");
                String dataSeason = a.attr("data-season");
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
     */
    public static List<Mikan.Week> parseCoverFlow(Document document, String host,
                                                  Set<String> subscribedMikanIds) {
        List<Mikan.Week> weeks = new ArrayList<>();

        Elements skBangumis = document.select(".sk-bangumi");
        if (skBangumis.isEmpty()) {
            List<MikanInfo> mikanInfos = parseAnimeList(document.selectFirst(".an-ul"),
                    host, subscribedMikanIds);
            weeks.add(new Mikan.Week().setItems(mikanInfos).setWeekLabel("Search"));
            return weeks;
        }

        for (Element skBangumi : skBangumis) {
            List<MikanInfo> mikanInfos = parseAnimeList(skBangumi, host, subscribedMikanIds);
            if (mikanInfos.isEmpty()) {
                continue;
            }
            String label = skBangumi.children().get(0).text().trim();
            weeks.add(new Mikan.Week().setWeekLabel(label).setItems(mikanInfos));
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
            String img = host + span.attr("data-src");
            Elements aa = li.select("a");
            if (aa.isEmpty()) {
                continue;
            }
            String href = host + aa.get(0).attr("href");
            String title = aa.get(0).text();

            String id = ReUtil.get("\\d+(/)?$", href, 0);
            id = StrUtil.blankToDefault(id, "");

            mikanInfos.add(
                    new MikanInfo()
                            .setCover(img)
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
                group.setRss(host + rssElement.attr("href"));
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
            String magnet = links.get(1).attr("data-clipboard-text");
            String formatSize = tds.get(2).text().trim();
            String dateStr = tds.get(3).text().trim();
            String torrent = links.get(2).attr("href");

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
                            .setTorrent(host + torrent)
            );
        }
    }

    /**
     * 解析详情页上的 Bangumi 链接
     */
    private static String parseBgmUrl(Document document) {
        Elements bangumiInfos = document.select(".bangumi-info");
        for (Element bangumiInfo : bangumiInfos) {
            if (bangumiInfo.ownText().equals("Bangumi番组计划链接：")) {
                Element link = bangumiInfo.selectFirst("a");
                if (Objects.nonNull(link)) {
                    return link.attr("href");
                }
            }
        }
        return "";
    }
}

package ani.rss.util.other;

import ani.rss.cache.CacheUtils;
import ani.rss.commons.FileUtils;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.Item;
import ani.rss.entity.RejectedItem;
import ani.rss.entity.StandbyRss;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.StandbyModeEnum;
import ani.rss.enums.StringEnum;
import ani.rss.util.basic.HttpReq;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.*;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


@Slf4j
public class ItemsUtil {
    private static final Config CONFIG = ConfigUtil.CONFIG;

    /**
     * 获取视频列表
     *
     * @param ani 订阅
     * @return 视频列表
     */
    public static List<Item> getItems(Ani ani) {
        return getItems(ani, null);
    }

    /**
     * 获取视频列表
     *
     * @param ani      订阅
     * @param rejected 非空时收集被过滤掉的条目及原因（仅预览/排查使用）
     * @return 视频列表
     */
    public static List<Item> getItems(Ani ani, List<RejectedItem> rejected) {
        String url = ani.getUrl();
        String subgroup = StrUtil.blankToDefault(ani.getSubgroup(), "未知字幕组");
        List<Item> items = new ArrayList<>(ItemsUtil.getItems(ani, url, subgroup, rejected)
                .stream()
                .peek(item -> item.setMaster(true))
                .toList());

        if (!CONFIG.getStandbyRss()) {
            items.sort(Comparator.comparingDouble(Item::getEpisode));
            return items;
        }

        List<StandbyRss> standbyRssList = ani.getStandbyRssList();
        for (StandbyRss rss : standbyRssList) {
            ThreadUtil.sleep(1000);
            subgroup = StrUtil.blankToDefault(rss.getLabel(), "未知字幕组");
            Ani clone = ObjUtil.clone(ani);
            clone.setOffset(rss.getOffset());
            items.addAll(ItemsUtil.getItems(clone, rss.getUrl(), subgroup, rejected)
                    .stream()
                    .peek(item -> item.setMaster(false))
                    .toList());
        }
        // 多字幕组共存模式（订阅级模式优先, default 回落全局设置）
        boolean coexist = isCoexist(ani);
        if (coexist) {
            items = CollUtil.distinct(items, Item::getReName, false);
        } else {
            items = CollUtil.distinct(items, it -> it.getEpisode().toString(), false);
        }
        items.sort(Comparator.comparingDouble(Item::getEpisode));
        return items;
    }

    /**
     * 获取视频列表
     *
     * @param ani          订阅
     * @param rssUrl       RSS链接
     * @param subgroupName 字幕组名
     * @return 视频列表
     */
    public static List<Item> getItems(Ani ani, String rssUrl, String subgroupName) {
        return getItems(ani, rssUrl, subgroupName, null);
    }

    /**
     * 获取视频列表
     *
     * @param ani          订阅
     * @param rssUrl       RSS链接
     * @param subgroupName 字幕组名
     * @param rejected     非空时收集被过滤掉的条目及原因
     * @return 视频列表
     */
    public static List<Item> getItems(Ani ani, String rssUrl, String subgroupName, List<RejectedItem> rejected) {
        String xml = getRss(rssUrl);

        List<String> exclude = ani.getExclude();
        List<String> match = ani.getMatch();

        List<Item> items = new ArrayList<>();

        Document document = XmlUtil.readXML(xml);
        Node channel = document.getElementsByTagName("channel").item(0);
        Assert.notNull(channel, "rss 格式错误, 缺少 channel 节点: {}", rssUrl);
        NodeList childNodes = channel.getChildNodes();
        List<String> globalExcludeList = CONFIG.getExclude();
        Boolean globalExclude = ani.getGlobalExclude();

        for (int i = childNodes.getLength() - 1; i >= 0; i--) {
            Node item = childNodes.item(i);
            String nodeName = item.getNodeName();
            if (!nodeName.equals("item")) {
                continue;
            }
            String itemTitle = "";
            String torrent = "";
            String length = "";
            String infoHash = "";

            String formatSize = "0MiB";

            DateTime pubDate = null;

            NodeList itemChildNodes = item.getChildNodes();
            for (int j = 0; j < itemChildNodes.getLength(); j++) {
                Node itemChild = itemChildNodes.item(j);
                String itemChildNodeName = itemChild.getNodeName();
                if (itemChildNodeName.equals("title")) {
                    itemTitle = itemChild.getTextContent();
                }

                if (itemChildNodeName.equals("enclosure")) {
                    NamedNodeMap attributes = itemChild.getAttributes();
                    Node urlNode = attributes.getNamedItem("url");
                    if (Objects.nonNull(urlNode)) {
                        torrent = urlNode.getNodeValue();
                    }
                    length = Optional.of(attributes)
                            .map(it -> it.getNamedItem("length"))
                            .map(Node::getNodeValue)
                            .filter(NumberUtil::isLong)
                            .orElse("1");

                    if (ReUtil.contains(StringEnum.MAGNET_REG, torrent)) {
                        infoHash = ReUtil.get(StringEnum.MAGNET_REG, torrent, 1);
                    }
                    if (ReUtil.contains(StringEnum.ED2K_REG, torrent)) {
                        infoHash = ReUtil.get(StringEnum.ED2K_REG, torrent, 3);
                    }
                }

                if ("guid".equals(itemChildNodeName)) {
                    // 仅当 guid 本身就是 40 位 info hash 时采用，避免把数字 ID、URL 等误当作种子哈希
                    if (ReUtil.isMatch(StringEnum.INFO_HASH_REG, itemChild.getTextContent())) {
                        infoHash = itemChild.getTextContent();
                    }
                }

                if ("nyaa:infoHash".equals(itemChildNodeName)) {
                    infoHash = itemChild.getTextContent();
                }
                if (itemChildNodeName.equals("nyaa:size")) {
                    formatSize = itemChild.getTextContent();
                }

                if (itemChildNodeName.equals("pubDate")) {
                    try {
                        pubDate = DateUtil.parse(itemChild.getTextContent());
                    } catch (Exception ignored) {
                    }
                }

                if (itemChildNodeName.equals("torrent")) {
                    try {
                        Element infoHashEl = XmlUtil.getElement((Element) itemChild, "infohash");
                        if (Objects.nonNull(infoHashEl)) {
                            infoHash = infoHashEl.getTextContent();
                        }

                        Element pubDateEl = XmlUtil.getElement((Element) itemChild, "pubDate");
                        if (Objects.nonNull(pubDateEl) && Objects.isNull(pubDate)) {
                            String pubDateStr = pubDateEl.getTextContent();
                            pubDate = DateUtil.parse(pubDateStr);
                        }

                        Element contentLength = XmlUtil.getElement((Element) itemChild, "contentLength");
                        if (Objects.nonNull(contentLength) && StrUtil.isBlank(length)) {
                            length = contentLength.getTextContent();
                        }

                        Element magneturi = XmlUtil.getElement((Element) itemChild, "magneturi");
                        if (Objects.nonNull(magneturi) && StrUtil.isBlank(torrent)) {
                            torrent = magneturi.getTextContent();
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (itemChildNodeName.equals("link")) {
                    String link = itemChild.getTextContent();
                    if (link.endsWith(".torrent")) {
                        torrent = link;
                    }
                }

            }

            if (StrUtil.isBlank(torrent)) {
                reject(rejected, subgroupName, itemTitle, null, "缺少种子/磁力链接");
                continue;
            }

            if (StrUtil.isBlank(infoHash)) {
                infoHash = FileUtil.mainName(torrent);
            }

            infoHash = infoHash.toLowerCase();
            infoHash = URLUtil.decode(infoHash);

            try {
                length = StrUtil.nullToDefault(length, "0");
                if (formatSize.equals("0MiB")) {
                    formatSize = FileUtils.formatSize(Long.parseLong(length), true);
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }

            Item addNewItem = new Item();

            addNewItem
                    .setSubgroup(subgroupName)
                    .setEpisode(1.0)
                    .setTitle(itemTitle)
                    .setReName(itemTitle)
                    .setTorrent(torrent)
                    .setInfoHash(infoHash)
                    .setFormatSize(formatSize)
                    .setPubDate(pubDate);

            Function<String, String> map = s -> {
                String subgroup = ReUtil.get(StringEnum.SUBGROUP_REG_STR, s, 1);
                if (StrUtil.isBlank(subgroup)) {
                    return s;
                }
                if (subgroup.equals(subgroupName)) {
                    return ReUtil.get(StringEnum.SUBGROUP_REG_STR, s, 2);
                }
                return "";
            };

            // 排除
            if (!exclude.isEmpty()) {
                String hitRule = null;
                for (String rule : exclude) {
                    String regex = map.apply(rule);
                    if (StrUtil.isNotBlank(regex) && ReUtil.contains(regex, addNewItem.getTitle())) {
                        hitRule = rule;
                        break;
                    }
                }
                if (hitRule != null) {
                    reject(rejected, subgroupName, addNewItem.getTitle(), pubDate, "排除规则命中: " + hitRule);
                    continue;
                }
            }

            // 匹配
            if (!match.isEmpty()) {
                String missingRule = null;
                for (String rule : match) {
                    String regex = map.apply(rule);
                    if (StrUtil.isNotBlank(regex) && !ReUtil.contains(regex, addNewItem.getTitle())) {
                        missingRule = rule;
                        break;
                    }
                }
                if (missingRule != null) {
                    reject(rejected, subgroupName, addNewItem.getTitle(), pubDate, "匹配规则未命中: " + missingRule);
                    continue;
                }
            }

            // 全局排除
            if (globalExclude) {
                String hitRule = null;
                for (String rule : globalExcludeList) {
                    String regex = map.apply(rule);
                    if (StrUtil.isNotBlank(regex) && ReUtil.contains(regex, addNewItem.getTitle())) {
                        hitRule = rule;
                        break;
                    }
                }
                if (hitRule != null) {
                    reject(rejected, subgroupName, addNewItem.getTitle(), pubDate, "全局排除命中: " + hitRule);
                    continue;
                }
            }
            items.add(addNewItem);
        }

        List<Item> renameItems = new ArrayList<>();
        List<String> unrecognizedTitles = new ArrayList<>();
        for (Item item : items) {
            boolean keep;
            try {
                keep = RenameUtil.rename(ani, item);
            } catch (Exception e) {
                log.error("解析rss视频集次出现问题");
                log.error(e.getMessage(), e);
                keep = false;
            }
            if (keep) {
                renameItems.add(item);
            } else {
                String reason = ItemsUtil.is5(item.getEpisode()) ? "跳过 x.5 集" : "集数无法识别";
                reject(rejected, subgroupName, item.getTitle(), item.getPubDate(), reason);
                if (!ItemsUtil.is5(item.getEpisode())) {
                    // rename 返回 false 有两种：开启 skip5 跳过 x.5 集（此时集数已解析为 x.5），
                    // 以及集数无法识别（集数仍为初始值 1.0）。这里只统计后者，便于排查漏订阅。
                    unrecognizedTitles.add(item.getTitle());
                }
            }
        }
        items = renameItems;

        if (!unrecognizedTitles.isEmpty()) {
            String preview = CollUtil.join(unrecognizedTitles.stream().limit(3).toList(), " | ");
            log.info("[{}][{}] 有 {} 个 RSS 条目无法识别集数，已跳过；如确需订阅，请为该订阅配置自定义集数规则: {}",
                    ani.getTitle(), subgroupName, unrecognizedTitles.size(), preview);
            if (log.isDebugEnabled()) {
                unrecognizedTitles.forEach(title -> log.debug("未识别集数的标题: {}", title));
            }
        }
        return CollUtil.distinct(items, item -> item.getEpisode().toString(), true);
    }

    /**
     * 该订阅是否为多字幕组共存模式
     * 订阅显式指定 coexist 时为 true；显式指定其他模式时为 false；
     * 为空/default 时回落全局 CONFIG.coexist（兼容旧订阅）
     *
     * @param ani 订阅
     * @return 是否共存
     */
    public static boolean isCoexist(Ani ani) {
        String mode = ani.getStandbyMode();
        if (StrUtil.isBlank(mode) || StandbyModeEnum.DEFAULT.getValue().equals(mode)) {
            return CONFIG.getCoexist();
        }
        return StandbyModeEnum.COEXIST.getValue().equals(mode);
    }

    /**
     * 该订阅是否为先到先得（不覆盖）模式
     *
     * @param ani 订阅
     * @return 是否不覆盖
     */
    public static boolean isSticky(Ani ani) {
        return StandbyModeEnum.STICKY.getValue().equals(ani.getStandbyMode());
    }

    /**
     * 获取rss内容
     *
     * @param url RSS链接
     * @return XML
     */
    public static String getRss(String url) {
        String xml = HttpReq.get(url)
                .timeout(CONFIG.getRssTimeout() * 1000)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    HttpReq.assertXml(res);
                    return res.body();
                });

        Assert.notBlank(xml, "xml is blank");
        boolean isXml = StrUtil.startWith(xml, '<');
        Assert.isTrue(isXml, "xml error");

        return xml;
    }

    public static List<Integer> omitList(Ani ani, List<Item> items) {
        ArrayList<Integer> list = new ArrayList<>();
        Boolean omit = CONFIG.getOmit();
        if (!omit) {
            return list;
        }
        if (items.isEmpty()) {
            return list;
        }

        if (!ani.getOmit()) {
            return list;
        }

        Boolean ova = ani.getOva();
        if (ova) {
            return list;
        }

        int[] array = items.stream().mapToInt(o -> o.getEpisode().intValue()).distinct().toArray();
        int max = ArrayUtil.max(array);
        int min = ArrayUtil.min(array);
        if (max == min) {
            return list;
        }

        for (int ep = min; ep <= max; ep++) {
            if (ArrayUtil.contains(array, ep)) {
                // 包含该集
                continue;
            }
            if (50 < list.size()) {
                // 防止list过多
                return list;
            }
            list.add(ep);
        }
        return list;
    }

    /**
     * 检测是否缺集
     *
     * @param ani   订阅
     * @param items 资源列表
     */
    public static void omit(Ani ani, List<Item> items) {
        List<Integer> list = omitList(ani, items);

        if (list.isEmpty()) {
            return;
        }

        // 缺少集数大于10个时可能是误判。因此不进行通知
        if (list.size() > 10) {
            return;
        }

        Integer season = ani.getSeason();
        String title = ani.getTitle();
        String id = ani.getId();

        ArrayList<String> sList = new ArrayList<>();

        for (Integer ep : list) {
            String s = StrFormatter.format("缺少集数 {} S{}E{}", title, String.format("%02d", season), String.format("%02d", ep));
            String key = StrFormatter.format("omit:{}:ep-{}", id, ep);
            if (CacheUtils.containsKey(key)) {
                // 一天内已经提醒过了
                continue;
            }
            log.info(s);
            // 缓存一天 不重复发送
            CacheUtils.put(key, s, TimeUnit.DAYS.toMillis(1));
            sList.add(s);
        }

        if (sList.isEmpty()) {
            return;
        }

        NotificationUtil.send(CONFIG, ani, CollUtil.join(sList, "\n"), NotificationStatusEnum.OMIT);
    }

    public static int currentEpisodeNumber(Ani ani, List<Item> items) {
        Boolean standbyRss = CONFIG.getStandbyRss();
        boolean coexist = isCoexist(ani);
        if (standbyRss && coexist) {
            // 开启多字幕组共存模式则只计算主rss集数
            items = items.stream()
                    .filter(Item::getMaster)
                    .toList();
        }

        // 过滤掉x.5集
        items = items
                .stream()
                .filter(it -> it.getEpisode() == it.getEpisode().intValue())
                .toList();

        if (items.isEmpty()) {
            return 0;
        }

        Boolean downloadNew = ani.getDownloadNew();
        if (downloadNew) {
            return items
                    .stream()
                    .mapToInt(item -> item.getEpisode().intValue())
                    .max()
                    .orElse(0);
        }
        return items.size();
    }

    /**
     * 摸鱼检测
     *
     * @param ani   订阅
     * @param items 资源列表
     */
    public static void procrastinating(Ani ani, List<Item> items) {
        Boolean procrastinating = CONFIG.getProcrastinating();
        Integer procrastinatingDay = CONFIG.getProcrastinatingDay();
        if (!procrastinating) {
            return;
        }

        procrastinating = ani.getProcrastinating();

        if (!procrastinating) {
            // 未开启摸鱼检测
            return;
        }

        Boolean procrastinatingMasterOnly = CONFIG.getProcrastinatingMasterOnly();
        if (procrastinatingMasterOnly) {
            // 仅启用主rss摸鱼检测
            items = items.stream()
                    .filter(Item::getMaster)
                    .toList();
        }

        items.stream()
                .map(Item::getPubDate)
                .filter(Objects::nonNull)
                .mapToLong(Date::getTime)
                .max()
                .ifPresent(t -> {
                    DateTime date = DateUtil.date(t);
                    DateTime now = DateTime.now();

                    // 时间不对
                    if (now.getTime() <= t) {
                        return;
                    }
                    long day = DateUtil.between(date, now, DateUnit.DAY);
                    if (procrastinatingDay > day) {
                        // 未达到指定摸鱼时间
                        return;
                    }

                    String id = ani.getId();
                    String title = ani.getTitle();

                    String text = StrFormatter.format("检测到{}, 已摸鱼{}天", title, day);

                    String key = StrFormatter.format("procrastinating:{}", id);

                    if (CacheUtils.containsKey(key)) {
                        // 一天内已经提醒过了
                        return;
                    }

                    CacheUtils.put(key, text, TimeUnit.DAYS.toMillis(1));
                    NotificationUtil.send(CONFIG, ani, text, NotificationStatusEnum.PROCRASTINATING);
                });
    }

    private static void reject(List<RejectedItem> rejected, String subgroupName,
                               String title, Date pubDate, String reason) {
        if (rejected == null) {
            return;
        }
        rejected.add(new RejectedItem()
                .setSubgroup(subgroupName)
                .setTitle(title)
                .setPubDate(pubDate)
                .setReason(reason));
    }

    /**
     * 找出 match/exclude 中引用了不存在字幕组的规则。
     * 字幕组改名后这些规则会静默失效（匹配变全下、排除不生效），用于预览告警。
     *
     * @param ani 订阅
     * @return 孤立规则列表
     */
    public static List<String> orphanRules(Ani ani) {
        Set<String> labels = new HashSet<>();
        if (StrUtil.isNotBlank(ani.getSubgroup())) {
            labels.add(ani.getSubgroup());
        }
        if (ani.getStandbyRssList() != null) {
            for (StandbyRss rss : ani.getStandbyRssList()) {
                if (StrUtil.isNotBlank(rss.getLabel())) {
                    labels.add(rss.getLabel());
                }
            }
        }

        List<String> rules = new ArrayList<>();
        if (ani.getMatch() != null) {
            rules.addAll(ani.getMatch());
        }
        if (ani.getExclude() != null) {
            rules.addAll(ani.getExclude());
        }

        return rules.stream()
                .filter(StrUtil::isNotBlank)
                .filter(rule -> {
                    String label = ReUtil.get(StringEnum.SUBGROUP_REG_STR, rule, 1);
                    return StrUtil.isNotBlank(label) && !labels.contains(label);
                })
                .distinct()
                .toList();
    }

    public static String getSubgroup(List<Item> items) {
        String reg = "^\\[(.+?)]";
        for (Item item : items) {
            String title = item.getTitle();
            if (!ReUtil.contains(reg, title)) {
                title = FileUtil.getName(title);
            }
            if (ReUtil.contains(reg, title)) {
                return ReUtil.get(reg, title, 1);
            }
        }
        return "未知字幕组";
    }

    /**
     * 判断是否为 x.5 集
     *
     * @param item 集数
     * @return 判断结果
     */
    public static Boolean is5(Item item) {
        if (Objects.isNull(item)) {
            return false;
        }
        return is5(item.getEpisode());
    }

    /**
     * 判断是否为 x.5 集
     *
     * @param episode 集数
     * @return 判断结果
     */
    public static Boolean is5(Double episode) {
        if (Objects.isNull(episode)) {
            return false;
        }
        return episode.intValue() != episode;
    }

}

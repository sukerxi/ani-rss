package ani.rss.service;

import ani.rss.commons.FileUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.download.qBittorrent;
import ani.rss.entity.Ani;
import ani.rss.entity.CollectionInfo;
import ani.rss.entity.Config;
import ani.rss.entity.Item;
import ani.rss.entity.torrent.TorrentsInfo;
import ani.rss.entity.torrent.qBittorrentTorrentsInfo;
import ani.rss.enums.StringEnum;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.*;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CollectionService {

    private static final Config CONFIG = ConfigUtil.CONFIG;

    /**
     * 合集任务标签
     */
    private static final String COLLECTION_TAG = "ANI-RSS合集下载";

    /**
     * 磁力预览占位任务标签，正式开始下载时移除，用于区分用户自己的同 hash 任务
     */
    private static final String COLLECTION_PREVIEW_TAG = "ANI-RSS合集预览";

    /**
     * 磁力元数据等待次数（每次间隔 2 秒，共 90 秒）
     */
    private static final int METADATA_WAIT_RETRY = 45;
    private static final long METADATA_WAIT_SLEEP = 2000;

    /**
     * 开始下载合集
     *
     * @param collectionInfo 合集信息
     */
    public void startCollection(CollectionInfo collectionInfo) {
        String torrent = collectionInfo.getTorrent();
        Ani ani = collectionInfo.getAni();
        String title = ani.getTitle();
        String subgroup = ani.getSubgroup();
        String downloadPath = ani.getCustomDownloadPathTemplate();

        String name = StrFormatter.format("[{}] {} 第{}季", subgroup, title, ani.getSeason());
        List<String> tags = collectionTags(ani);

        String hash;
        if (TorrentUtil.isMagnet(torrent)) {
            hash = TorrentUtil.parseMagnetHash(torrent);
            // 复用预览阶段创建的占位任务（若存在），并等待磁力元数据就绪
            ensureMagnetTask(hash, name, torrent, downloadPath, tags);
            // 移除预览标签后该任务即视为正式下载任务，取消预览时不会再被清理
            removeTagsQuietly(hash, COLLECTION_PREVIEW_TAG);
            // 元数据就绪后确保任务停止，避免在设置文件优先级前下载正文
            stopTorrentsIfOwned(hash);
        } else {
            File tempFile = FileUtil.createTempFile();
            Base64.decodeToFile(torrent, tempFile);
            TorrentMetadata torrentFile;
            try {
                torrentFile = TorrentMetadata.from(tempFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            hash = torrentFile.getHash();
            download(name, tempFile, downloadPath, List.of(COLLECTION_TAG, subgroup));
        }

        TorrentsInfo torrentsInfo = new TorrentsInfo()
                .setHash(hash);

        List<qBittorrentTorrentsInfo.FileEntity> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ThreadUtil.sleep(500);
            try {
                files.addAll(qBittorrent.files(torrentsInfo, false));
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            if (!files.isEmpty()) {
                // 添加下载完成
                break;
            }
        }

        Map<String, String> reNameMap = preview(collectionInfo)
                .stream()
                .map(item -> {
                    Optional<qBittorrentTorrentsInfo.FileEntity> fileEntity = files.stream()
                            .filter(f -> new File(f.getName()).getName().equals(new File(item.getTitle()).getName()))
                            .filter(f -> f.getSize().longValue() == item.getLength())
                            .findFirst();
                    if (fileEntity.isEmpty()) {
                        return null;
                    }
                    String oldPath = fileEntity.get().getName();
                    return item.setTitle(oldPath);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Item::getTitle,
                        Item::getReName
                ));

        for (int i = 0; i < 30; i++) {
            for (qBittorrentTorrentsInfo.FileEntity file : files) {
                String oldPath = file.getName();
                String newPath = reNameMap.get(oldPath);

                if (!reNameMap.containsKey(oldPath)) {
                    if (!reNameMap.containsValue(oldPath) && file.getPriority() > 0) {
                        qBittorrent.postApi("/api/v2/torrents/filePrio")
                                .form("hash", hash)
                                .form("id", file.getIndex())
                                .form("priority", 0)
                                .thenFunction(HttpResponse::isOk);
                    }
                    continue;
                }
                log.info("重命名 {} ==> {}", oldPath, newPath);
                qBittorrent.postApi("/api/v2/torrents/renameFile")
                        .form("hash", hash)
                        .form("oldPath", oldPath)
                        .form("newPath", newPath)
                        .thenFunction(HttpResponse::isOk);
            }
            files.clear();
            files.addAll(qBittorrent.files(torrentsInfo, false));

            if (CollUtil.containsAll(files.stream()
                    .map(qBittorrentTorrentsInfo.FileEntity::getName)
                    .toList(), reNameMap.values())) {
                // 所有命名已完成
                break;
            }
            // 命名有遗漏 继续
            ThreadUtil.sleep(1000);
        }

        qBittorrent.start(torrentsInfo);
    }

    /**
     * 合集预览
     *
     * @param collectionInfo 合集信息
     * @return 预览的条目列表
     */
    public List<Item> previewCollection(CollectionInfo collectionInfo) {
        List<Item> preview = preview(collectionInfo);
        return CollUtil.sort(new ArrayList<>(preview), Comparator.comparingDouble(it -> {
            Double episode = it.getEpisode();
            return ObjectUtil.defaultIfNull(episode, 0.0);
        }));
    }

    /**
     * 获取合集字幕组
     *
     * @param collectionInfo 合集信息
     * @return 字幕组名称
     */
    public String getCollectionSubgroup(CollectionInfo collectionInfo) {
        List<Item> items = previewCollection(collectionInfo);
        return ItemsUtil.getSubgroup(items);
    }

    /**
     * 下载
     *
     * @param name        任务名称
     * @param torrentFile 种子
     * @param savePath    保存位置
     * @param tags        标签
     */
    public void download(String name, File torrentFile, String savePath, List<String> tags) {
        assertQbLogin();

        collectionAddForm(name, savePath, tags)
                .form("paused", true)
                .form("stopped", true)
                .form("torrents", torrentFile)
                .then(HttpReq::assertStatus);
    }

    /**
     * 通过磁力链接添加合集任务。
     * 使用 stopCondition=MetadataReceived：qBittorrent 拉取到元数据后自动停止，
     * 不下载正文，以便合集流程先按文件列表设置优先级与重命名。
     *
     * @param name     任务名称
     * @param magnet   磁力链接
     * @param savePath 保存位置
     * @param tags     标签
     */
    public void downloadMagnet(String name, String magnet, String savePath, List<String> tags) {
        assertQbLogin();

        collectionAddForm(name, savePath, tags)
                .form("paused", false)
                .form("stopped", false)
                .form("stopCondition", "MetadataReceived")
                .form("urls", magnet)
                .then(res -> {
                    HttpReq.assertStatus(res);
                    String body = StrUtil.trim(res.body());
                    Assert.isTrue(!"Fails.".equalsIgnoreCase(body), "qBittorrent 添加磁力链接失败: {}", body);
                });
    }

    /**
     * 校验当前下载器为 qBittorrent 且登录正常。
     */
    private void assertQbLogin() {
        String download = CONFIG.getDownloadToolType();
        Assert.isTrue("qBittorrent".equals(download), "合集下载暂时只支持 qBittorrent");
        Assert.isTrue(TorrentUtil.login(), "下载器登录失败");
    }

    /**
     * 合集任务标签（含磁力预览标签，正式开始时移除预览标签）。
     *
     * @param ani 订阅
     * @return 标签列表
     */
    private List<String> collectionTags(Ani ani) {
        return List.of(COLLECTION_TAG, COLLECTION_PREVIEW_TAG, ani.getSubgroup());
    }

    /**
     * 构造合集任务的 qBittorrent 添加表单，种子文件与磁力链接共用相同参数。
     */
    private HttpRequest collectionAddForm(String name, String savePath, List<String> tags) {
        Integer ratioLimit = CONFIG.getRatioLimit();
        Integer seedingTimeLimit = CONFIG.getSeedingTimeLimit();
        Integer inactiveSeedingTimeLimit = CONFIG.getInactiveSeedingTimeLimit();

        Long upLimit = CONFIG.getUpLimit() * 1024;
        Long dlLimit = CONFIG.getDlLimit() * 1024;

        Boolean qbUseDownloadPath = CONFIG.getQbUseDownloadPath();

        String qbContentLayout = CONFIG.getQbContentLayout();

        return qBittorrent.postApi("/api/v2/torrents/add")
                .form("addToTopOfQueue", false)
                .form("autoTMM", false)
                .form("category", "")
                .form("contentLayout", qbContentLayout)
                .form("dlLimit", dlLimit)
                .form("firstLastPiecePrio", false)
                .form("rename", name)
                .form("savepath", savePath)
                .form("sequentialDownload", false)
                .form("skip_checking", false)
                .form("stopCondition", "None")
                .form("upLimit", upLimit)
                .form("useDownloadPath", qbUseDownloadPath)
                .form("tags", CollUtil.join(tags, ","))
                .form("ratioLimit", ratioLimit)
                .form("seedingTimeLimit", seedingTimeLimit)
                .form("inactiveSeedingTimeLimit", inactiveSeedingTimeLimit);
    }

    /**
     * 确保磁力合集任务已存在于 qBittorrent 且元数据（文件列表）就绪。
     * 任务可能已由合集预览创建，此时直接复用并等待元数据。
     *
     * @param hash     info hash
     * @param name     任务名称
     * @param magnet   磁力链接
     * @param savePath 保存位置
     * @param tags     标签
     * @return 元数据中的文件列表
     */
    private List<qBittorrentTorrentsInfo.FileEntity> ensureMagnetTask(String hash, String name, String magnet,
                                                                      String savePath, List<String> tags) {
        assertQbLogin();
        TorrentsInfo torrentsInfo = new TorrentsInfo().setHash(hash);
        qBittorrentTorrentsInfo existing = findTaskByHash(hash);
        if (existing == null) {
            downloadMagnet(name, magnet, savePath, tags);
        } else {
            // qBittorrent 中已存在同 hash 任务：仅允许复用合集流程自己创建的占位任务，
            // 避免改动或误删用户已有的下载任务
            List<String> tagList = StrUtil.split(existing.getTags(), ",", true, true);
            if (!tagList.contains(COLLECTION_TAG)) {
                throw new RuntimeException("qBittorrent 中已存在相同磁力的任务，请先在下载器中处理后再试");
            }
            addTagsQuietly(torrentsInfo, tags);
        }
        return waitMagnetMetadata(torrentsInfo);
    }

    /**
     * 按 info hash 查询 qBittorrent 任务。
     *
     * @param hash info hash
     * @return 任务信息，不存在或查询失败时返回 null
     */
    private qBittorrentTorrentsInfo findTaskByHash(String hash) {
        try {
            List<qBittorrentTorrentsInfo> tasks = qBittorrent.getApi("/api/v2/torrents/info")
                    .form("hashes", hash)
                    .thenFunction(res -> {
                        HttpReq.assertStatus(res);
                        return GsonStatic.fromJsonList(res.body(), qBittorrentTorrentsInfo.class);
                    });
            return tasks.isEmpty() ? null : tasks.get(0);
        } catch (Exception e) {
            log.warn("查询 qBittorrent 任务失败 hash={} {}", hash, e.getMessage());
            return null;
        }
    }

    /**
     * 轮询 qBittorrent 文件列表，等待磁力元数据拉取完成。
     *
     * @param torrentsInfo 种子信息
     * @return 文件列表
     */
    private List<qBittorrentTorrentsInfo.FileEntity> waitMagnetMetadata(TorrentsInfo torrentsInfo) {
        for (int i = 0; i < METADATA_WAIT_RETRY; i++) {
            ThreadUtil.sleep(METADATA_WAIT_SLEEP);
            List<qBittorrentTorrentsInfo.FileEntity> files = listFilesQuietly(torrentsInfo);
            if (CollUtil.isNotEmpty(files)) {
                // 元数据就绪后立即停止，兼容不支持 stopCondition=MetadataReceived 的旧版 qBittorrent，
                // 避免预览等待期间开始下载正文；仅操作合集流程自己的任务
                stopTorrentsIfOwned(torrentsInfo.getHash());
                return files;
            }
        }
        throw new RuntimeException("磁力链接元数据获取超时，请检查网络、Tracker 或 DHT 后重试");
    }

    /**
     * 查询任务文件列表，任务不存在（如磁力尚未加入）时返回 null。
     */
    private List<qBittorrentTorrentsInfo.FileEntity> listFilesQuietly(TorrentsInfo torrentsInfo) {
        try {
            return qBittorrent.files(torrentsInfo, false);
        } catch (Exception e) {
            log.warn("查询 qBittorrent 文件列表失败 hash={} {}", torrentsInfo.getHash(), e.getMessage());
            return null;
        }
    }

    /**
     * 停止任务（pause 接口在 qBittorrent 4.x/5.x 均可用，5.x 为 stop 的兼容别名），
     * 仅当任务带有合集标签时执行，避免误停 qBittorrent 中同 hash 的用户任务。
     */
    private void stopTorrentsIfOwned(String hash) {
        try {
            qBittorrentTorrentsInfo task = findTaskByHash(hash);
            if (task == null) {
                return;
            }
            List<String> tagList = StrUtil.split(task.getTags(), ",", true, true);
            if (!tagList.contains(COLLECTION_TAG)) {
                return;
            }
            qBittorrent.postApi("/api/v2/torrents/pause")
                    .form("hashes", hash)
                    .thenFunction(HttpResponse::isOk);
        } catch (Exception e) {
            log.warn("停止磁力合集任务失败 hash={} {}", hash, e.getMessage());
        }
    }

    /**
     * 追加标签。
     */
    private void addTagsQuietly(TorrentsInfo torrentsInfo, List<String> tags) {
        try {
            qBittorrent.postApi("/api/v2/torrents/addTags")
                    .form("hashes", torrentsInfo.getHash())
                    .form("tags", CollUtil.join(tags, ","))
                    .thenFunction(HttpResponse::isOk);
        } catch (Exception e) {
            log.warn("追加合集标签失败 hash={} {}", torrentsInfo.getHash(), e.getMessage());
        }
    }

    /**
     * 移除标签。
     */
    private void removeTagsQuietly(String hash, String tag) {
        try {
            qBittorrent.postApi("/api/v2/torrents/removeTags")
                    .form("hashes", hash)
                    .form("tags", tag)
                    .thenFunction(HttpResponse::isOk);
        } catch (Exception e) {
            log.warn("移除合集标签失败 hash={} tag={} {}", hash, tag, e.getMessage());
        }
    }

    /**
     * 取消磁力合集预览：删除由预览创建、仍带预览标签的占位任务。
     * 种子文件方式不产生占位任务；已正式开始（预览标签已移除）的任务不会被删除。
     *
     * @param collectionInfo 合集信息
     */
    public void cancelCollection(CollectionInfo collectionInfo) {
        String torrent = collectionInfo.getTorrent();
        if (!TorrentUtil.isMagnet(torrent)) {
            return;
        }
        final String hash;
        try {
            hash = TorrentUtil.parseMagnetHash(torrent);
        } catch (Exception e) {
            return;
        }
        try {
            qBittorrentTorrentsInfo task = findTaskByHash(hash);
            if (task == null) {
                return;
            }
            List<String> tagList = StrUtil.split(task.getTags(), ",", true, true);
            boolean removable = tagList.contains(COLLECTION_TAG) && tagList.contains(COLLECTION_PREVIEW_TAG);
            if (!removable) {
                return;
            }
            Boolean deleted = qBittorrent.postApi("/api/v2/torrents/delete")
                    .form("hashes", hash)
                    .form("deleteFiles", false)
                    .thenFunction(HttpResponse::isOk);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("已取消磁力合集预览任务 {}", hash);
            }
        } catch (Exception e) {
            log.warn("取消磁力合集预览失败 hash={} {}", hash, e.getMessage());
        }
    }

    /**
     * 预览
     *
     * @param collectionInfo 合集信息
     * @return 项目列表
     */
    public List<Item> preview(CollectionInfo collectionInfo) {
        String torrent = collectionInfo.getTorrent();
        Ani ani = collectionInfo.getAni();

        List<SourceFile> sourceFiles;
        if (TorrentUtil.isMagnet(torrent)) {
            // 磁力链接没有本地种子，由 qBittorrent 拉取元数据后提供文件列表
            String name = StrFormatter.format("[{}] {} 第{}季", ani.getSubgroup(), ani.getTitle(), ani.getSeason());
            String hash = TorrentUtil.parseMagnetHash(torrent);
            List<qBittorrentTorrentsInfo.FileEntity> files =
                    ensureMagnetTask(hash, name, torrent, ani.getCustomDownloadPathTemplate(), collectionTags(ani));
            sourceFiles = files.stream()
                    .map(file -> new SourceFile(normalizePath(file.getName()), file.getSize()))
                    .toList();
        } else {
            File tempFile = FileUtil.createTempFile();
            Base64.decodeToFile(torrent, tempFile);
            TorrentMetadata torrentFile;
            try {
                torrentFile = TorrentMetadata.from(tempFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            long[] lengths = torrentFile.getLengths();
            AtomicInteger index = new AtomicInteger(0);
            sourceFiles = Arrays.stream(torrentFile.getFilenames())
                    .map(filename -> new SourceFile(
                            normalizePath(CharsetUtil.convert(filename, "ISO-8859-1", CharsetUtil.UTF_8)),
                            lengths[index.getAndIncrement()]))
                    .toList();
        }

        return buildPreviewItems(ani, sourceFiles);
    }

    /**
     * 统一文件路径分隔符，并去掉结尾多余的斜杠。
     *
     * @param path 原始路径
     * @return 标准化后的路径
     */
    private String normalizePath(String path) {
        path = ReUtil.replaceAll(path, "[\\\\/]$", "");
        return path.replace("\\", "/");
    }

    /**
     * 根据文件条目执行匹配、排除与重命名规则，生成预览项目。
     *
     * @param ani         订阅
     * @param sourceFiles 种子或磁力元数据中的文件条目
     * @return 预览项目列表
     */
    private List<Item> buildPreviewItems(Ani ani, List<SourceFile> sourceFiles) {
        List<String> match = ani.getMatch();
        List<String> exclude = ani.getExclude();
        Boolean globalExclude = ani.getGlobalExclude();
        List<String> globalExcludeList = CONFIG.getExclude();

        Function<String, String> map = s -> {
            String subgroup = ReUtil.get(StringEnum.SUBGROUP_REG_STR, s, 1);
            if (StrUtil.isBlank(subgroup)) {
                return s;
            }
            if (subgroup.equals(ani.getSubgroup())) {
                return ReUtil.get(StringEnum.SUBGROUP_REG_STR, s, 2);
            }
            return "";
        };

        return sourceFiles.stream()
                .map(sourceFile -> new Item()
                        .setTitle(sourceFile.name())
                        .setLength(sourceFile.length()))
                .filter(item -> {
                    String name = item.getTitle();

                    if (name.startsWith("_____padding_file_") && name.contains("BitComet")) {
                        return false;
                    }

                    // 排除
                    if (!exclude.isEmpty()) {
                        if (exclude.stream().map(map).filter(StrUtil::isNotBlank).anyMatch(s -> ReUtil.contains(s, name))) {
                            return false;
                        }
                    }

                    // 匹配
                    if (!match.isEmpty()) {
                        if (match.stream().map(map).filter(StrUtil::isNotBlank).anyMatch(s -> !ReUtil.contains(s, name))) {
                            return false;
                        }
                    }

                    // 全局排除
                    if (globalExclude) {
                        return globalExcludeList.stream().map(map).filter(StrUtil::isNotBlank).noneMatch(s -> ReUtil.contains(s, name));
                    }
                    return true;
                })
                .map(item -> {
                    long length = item.getLength();

                    String formatSize = FileUtils.formatSize(length, true);

                    item
                            .setFormatSize(formatSize)
                            .setSubgroup(ani.getSubgroup());

                    RenameUtil.rename(ani, item);

                    String reName = item.getReName();

                    if (StrUtil.isBlank(reName)) {
                        return null;
                    }

                    String title = item.getTitle();

                    String extName = FileUtil.extName(title);

                    if (FileUtils.isSubtitleFormat(extName)) {
                        String lang = FileUtil.extName(FileUtil.mainName(title));
                        if (StrUtil.isNotBlank(lang)) {
                            reName += "." + lang;
                        }
                    }

                    reName = reName + "." + extName;

                    return item.setReName(reName)
                            .setLength(length);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 合集来源中的文件条目
     *
     * @param name   标准化后的相对路径
     * @param length 文件大小（字节）
     */
    private record SourceFile(String name, long length) {
    }

}

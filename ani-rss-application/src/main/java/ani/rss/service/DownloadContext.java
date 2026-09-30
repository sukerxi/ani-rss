package ani.rss.service;

import ani.rss.commons.FileUtils;
import ani.rss.entity.torrent.TorrentsInfo;
import ani.rss.util.other.TorrentUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次订阅处理（RSS 轮询 / 预览）内的可复用快照。
 *
 * <ul>
 *     <li>下载器任务列表：一次拉取全程复用，避免每个 RSS 条目都触发一次全量查询；
 *     实际添加下载任务后可调用 {@link #refresh()} 刷新；</li>
 *     <li>下载目录文件列表：按路径惰性缓存，避免同一目录被重复扫描。</li>
 * </ul>
 * 该对象非线程安全，仅限单次处理线程内使用。
 */
public class DownloadContext {

    private List<TorrentsInfo> torrentsInfos;

    private final Map<String, List<File>> fileListCache = new HashMap<>();

    public DownloadContext(List<TorrentsInfo> torrentsInfos) {
        this.torrentsInfos = torrentsInfos;
    }

    /**
     * 立即拉取一份下载器任务快照
     */
    public static DownloadContext snapshot() {
        return new DownloadContext(TorrentUtil.getTorrentsInfos());
    }

    /**
     * 不需要下载器任务列表的场景（如预览不校验下载任务），首次访问时才懒加载
     */
    public static DownloadContext lazy() {
        return new DownloadContext(null);
    }

    public List<TorrentsInfo> getTorrentsInfos() {
        if (torrentsInfos == null) {
            torrentsInfos = TorrentUtil.getTorrentsInfos();
        }
        return torrentsInfos;
    }

    /**
     * 添加下载任务后刷新快照，保证后续条目能看到新任务
     */
    public void refresh() {
        torrentsInfos = TorrentUtil.getTorrentsInfos();
    }

    public void removeTorrentsInfo(TorrentsInfo torrentsInfo) {
        if (torrentsInfos != null) {
            torrentsInfos.remove(torrentsInfo);
        }
    }

    /**
     * 按保存路径过滤下载器任务
     */
    public List<TorrentsInfo> findBySavePath(String savePath) {
        List<TorrentsInfo> result = new ArrayList<>();
        for (TorrentsInfo torrentsInfo : getTorrentsInfos()) {
            if (savePath.equals(torrentsInfo.getSavePath())) {
                result.add(torrentsInfo);
            }
        }
        return result;
    }

    /**
     * 目录文件列表（同一路径只扫描一次）
     */
    public List<File> listFiles(String path) {
        return fileListCache.computeIfAbsent(path, FileUtils::listFileList);
    }
}

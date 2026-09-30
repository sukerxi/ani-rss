package ani.rss.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 外部幂等 GET 响应缓存
 * <p>
 * fresh 期内直接复用本地响应; 进入 stale 期后先返回旧响应并后台刷新 (stale-while-revalidate);
 * 源站请求失败时 stale 期内继续返回旧响应 (stale-if-error); 同一 key 的并发请求只发出一次 (single-flight);
 * 支持 ETag / Last-Modified 条件请求, 源站返回 304 时复用本地 body。
 * <p>
 * 仅用于幂等 GET 请求, 禁止用于写操作、鉴权接口和 no-store 内容。
 * 手动刷新场景传 force=true 同步拉取最新响应并回填缓存。
 */
@Slf4j
public class HttpResponseCache {

    private record Entry(String body, String etag, String lastModified,
                         long expiresAt, long staleUntil) {
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    /**
     * 同步刷新中的请求, 用于 single-flight
     */
    private static final Map<String, CompletableFuture<String>> LOADING = new ConcurrentHashMap<>();

    private static final ExecutorService REFRESH_POOL = Executors.newVirtualThreadPerTaskExecutor();

    public static String get(String key, Supplier<HttpRequest> requestFactory,
                             Duration freshTtl, Duration staleTtl) {
        return get(key, requestFactory, freshTtl, staleTtl, false);
    }

    /**
     * @param key            缓存 key
     * @param requestFactory 真正发请求时新建 HttpRequest 的工厂 (条件头由本组件追加)
     * @param freshTtl       fresh 有效期, 期内不请求源站
     * @param staleTtl       stale 有效期 (相对抓取时间), 期内可后台刷新/失败兜底
     * @param force          强制同步刷新, 绕过 fresh/stale
     * @return 响应 body
     */
    public static String get(String key, Supplier<HttpRequest> requestFactory,
                             Duration freshTtl, Duration staleTtl, boolean force) {
        long now = System.currentTimeMillis();
        Entry entry = force ? null : ENTRIES.get(key);

        if (Objects.nonNull(entry)) {
            if (now < entry.expiresAt()) {
                return entry.body();
            }
            if (now < entry.staleUntil()) {
                // stale-while-revalidate: 先返回旧数据, 后台单请求刷新
                refreshAsync(key, requestFactory, entry, freshTtl, staleTtl);
                return entry.body();
            }
        }

        return refreshSync(key, requestFactory, entry, freshTtl, staleTtl);
    }

    private static void refreshAsync(String key, Supplier<HttpRequest> requestFactory, Entry stale,
                                     Duration freshTtl, Duration staleTtl) {
        if (LOADING.containsKey(key)) {
            return;
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        if (Objects.isNull(LOADING.putIfAbsent(key, future))) {
            REFRESH_POOL.submit(() -> {
                try {
                    future.complete(doRequest(key, requestFactory, stale, freshTtl, staleTtl));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    log.debug("后台刷新外部响应缓存失败 key={}, {}", key, e.getMessage());
                } finally {
                    LOADING.remove(key, future);
                }
            });
        }
    }

    private static String refreshSync(String key, Supplier<HttpRequest> requestFactory, Entry stale,
                                      Duration freshTtl, Duration staleTtl) {
        // single-flight: 同 key 并发时复用同一个刷新请求
        CompletableFuture<String> future = LOADING.computeIfAbsent(key,
                k -> CompletableFuture.supplyAsync(
                        () -> doRequest(k, requestFactory, stale, freshTtl, staleTtl), REFRESH_POOL));
        try {
            return future.join();
        } finally {
            LOADING.remove(key, future);
        }
    }

    private static String doRequest(String key, Supplier<HttpRequest> requestFactory, Entry stale,
                                    Duration freshTtl, Duration staleTtl) {
        HttpRequest request = requestFactory.get();
        if (Objects.nonNull(stale)) {
            if (StrUtil.isNotBlank(stale.etag())) {
                request.header("If-None-Match", stale.etag());
            }
            if (StrUtil.isNotBlank(stale.lastModified())) {
                request.header("If-Modified-Since", stale.lastModified());
            }
        }

        try (HttpResponse response = request.execute()) {
            int status = response.getStatus();

            if (status == 304 && Objects.nonNull(stale)) {
                // 源站确认未变化, 复用本地 body 并续期
                putEntry(key, stale.body(),
                        StrUtil.blankToDefault(response.header("ETag"), stale.etag()),
                        StrUtil.blankToDefault(response.header("Last-Modified"), stale.lastModified()),
                        freshTtl, staleTtl);
                return stale.body();
            }

            if (!response.isOk()) {
                throw new IllegalStateException("外部响应状态异常 status=" + status + ", key=" + key);
            }

            String body = response.body();
            putEntry(key, body, response.header("ETag"), response.header("Last-Modified"),
                    freshTtl, staleTtl);
            return body;
        } catch (Exception e) {
            // stale-if-error: stale 期内源站不可用时继续使用旧响应
            if (Objects.nonNull(stale) && System.currentTimeMillis() < stale.staleUntil()) {
                log.debug("外部请求失败, 复用 stale 缓存 key={}, {}", key, e.getMessage());
                return stale.body();
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
        }
    }

    private static void putEntry(String key, String body, String etag, String lastModified,
                                 Duration freshTtl, Duration staleTtl) {
        long now = System.currentTimeMillis();
        ENTRIES.put(key, new Entry(body, etag, lastModified,
                now + freshTtl.toMillis(), now + staleTtl.toMillis()));
    }
}

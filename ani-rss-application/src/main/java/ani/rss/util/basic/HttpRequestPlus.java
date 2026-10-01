package ani.rss.util.basic;

import ani.rss.commons.ExceptionUtils;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
public class HttpRequestPlus extends HttpRequest {

    /**
     * 剩余重试次数, 仅对幂等请求开启 (第三方站点抓取)
     */
    private int retryCount = 0;

    public HttpRequestPlus(UrlBuilder url) {
        super(url);
    }

    /**
     * 设置重试次数, 触发条件为限流/网关错误状态码与网络类异常
     *
     * @param retryCount 重试次数
     * @return this
     */
    public HttpRequestPlus setRetry(int retryCount) {
        this.retryCount = Math.max(0, retryCount);
        return this;
    }

    public static HttpRequestPlus of(UrlBuilder url) {
        return new HttpRequestPlus(url);
    }

    public static HttpRequestPlus of(String url) {
        // 去除分隔符重复
        url = url.replaceAll("(?<!https?:?)//", "/");
        return HttpRequestPlus.of(UrlBuilder.ofHttp(url, StandardCharsets.UTF_8));
    }

    public static HttpRequestPlus of(String url, Charset charset) {
        // 去除分隔符重复
        url = url.replaceAll("(?<!https?:?)//", "/");
        return HttpRequestPlus.of(UrlBuilder.ofHttp(url, charset));
    }

    public static HttpRequest get(String url) {
        return HttpRequestPlus.of(url).method(Method.GET);
    }

    public static HttpRequest post(String url) {
        return HttpRequestPlus.of(url).method(Method.POST);
    }

    @Override
    public HttpResponse execute(boolean isAsync) {
        String url = getUrl();
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpResponse response = super.execute(isAsync);
                if (!isAsync && attempt <= retryCount && isRetryableStatus(response.getStatus())) {
                    log.warn("url: {}, status: {}, 重试 {}/{}", url, response.getStatus(), attempt, retryCount);
                    response.close();
                    ThreadUtil.sleep(backoff(attempt));
                    continue;
                }
                return response;
            } catch (Exception e) {
                if (!isAsync && attempt <= retryCount && isRetryable(e)) {
                    log.warn("url: {}, error: {}, 重试 {}/{}", url, ExceptionUtils.getMessage(e), attempt, retryCount);
                    ThreadUtil.sleep(backoff(attempt));
                    continue;
                }
                String message = ExceptionUtils.getMessage(e);
                log.error("url: {}, error: {}", url, message);
                throw e;
            }
        }
    }

    private static long backoff(int attempt) {
        return Math.min(2000L, 500L * attempt);
    }

    /**
     * 限流与网关/服务端瞬时错误, 幂等请求可重试
     */
    private static boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || status == 500
                || status == 502 || status == 503 || status == 504;
    }

    /**
     * 连接重置/超时等网络类异常, 幂等请求可重试
     */
    private static boolean isRetryable(Throwable e) {
        Throwable cause = e;
        for (int i = 0; i < 8 && cause != null; i++) {
            if (cause instanceof IOException || cause instanceof HttpException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

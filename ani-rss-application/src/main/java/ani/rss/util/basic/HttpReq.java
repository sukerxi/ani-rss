package ani.rss.util.basic;

import ani.rss.cache.CacheUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.commons.MavenUtils;
import ani.rss.commons.URLUtils;
import ani.rss.entity.Config;
import ani.rss.entity.web.Header;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpConnection;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import cn.hutool.http.cookie.GlobalCookieManager;
import lombok.extern.slf4j.Slf4j;

import java.net.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HttpReq {


    public static final CookieManager COOKIE_MANAGER;

    static {
        COOKIE_MANAGER = new CookieManager();
        COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    }

    private static void config(HttpRequest req) {
        GlobalCookieManager.setCookieManager(COOKIE_MANAGER);

        req.timeout(1000 * 20)
                .setFollowRedirects(true);

        String ua = "ani-rss/{} (https://github.com/wushuo894/ani-rss)";
        ua = StrUtil.format(ua, MavenUtils.getVersion());

        req.header(Header.USER_AGENT, ua);
    }

    /**
     * 获取域名映射规则
     * 使用规则内容的 md5 作为缓存 key, 避免每次请求重复解析
     *
     * @return 映射规则
     */
    private static List<Pair<String, String>> domainMappingRules() {
        String domainMapping = ConfigUtil.CONFIG.getDomainMapping();
        if (StrUtil.isBlank(domainMapping)) {
            return List.of();
        }

        String key = StrFormatter.format("domainMapping:{}", SecureUtil.md5(domainMapping));

        List<Pair<String, String>> rules = CacheUtils.get(key);
        if (Objects.nonNull(rules)) {
            return rules;
        }

        rules = URLUtils.parseDomainMapping(domainMapping);

        CacheUtils.put(key, rules, TimeUnit.MINUTES.toMillis(10));

        return rules;
    }

    /**
     * 应用域名映射
     *
     * @param url 原始链接
     * @return 映射后的链接
     */
    private static String applyDomainMapping(String url) {
        List<Pair<String, String>> rules = domainMappingRules();
        if (rules.isEmpty()) {
            return url;
        }

        String mappedUrl = URLUtils.mapDomain(url, rules);
        if (!Objects.equals(url, mappedUrl)) {
            log.debug("域名映射: {} -> {}", url, mappedUrl);
        }
        return mappedUrl;
    }

    /**
     * 创建请求
     *
     * @param method 请求方式
     * @param url    原始链接
     * @return HttpRequest
     */
    private static HttpRequest of(Method method, String url) {
        HttpRequest req = HttpRequestPlus.of(applyDomainMapping(url)).method(method);
        config(req);
        // 使用原始链接判断是否需要代理, 避免映射后的域名导致 proxyList 失效
        setProxy(req, ConfigUtil.CONFIG, url);
        return req;
    }

    public static HttpRequest post(String url) {
        return of(Method.POST, url);
    }

    public static HttpRequest post(String url, Object body) {
        if (body instanceof String) {
            return post(url).body((String) body);
        }
        return post(url).body(GsonStatic.toJson(body));
    }

    public static HttpRequest get(String url) {
        return of(Method.GET, url);
    }

    public static HttpRequest put(String url) {
        return of(Method.PUT, url);
    }

    public static HttpRequest delete(String url) {
        return of(Method.DELETE, url);
    }

    /**
     * 设置代理
     *
     * @param req    HttpRequest
     * @param config 设置
     * @param url    用于判断是否需要代理的链接, 应传入映射前的原始链接
     */
    public static void setProxy(HttpRequest req, Config config, String url) {
        Boolean proxy = config.getProxy();
        if (!proxy) {
            log.debug("代理未开启 {}", url);
            return;
        }

        if (!isProxy(url)) {
            // 不进行代理
            return;
        }

        String proxyHost = config.getProxyHost();
        Integer proxyPort = config.getProxyPort();
        if (StrUtil.isBlank(proxyHost) || Objects.isNull(proxyPort)) {
            log.debug("代理参数不全 {}", url);
            return;
        }

        String proxyUsername = config.getProxyUsername();
        String proxyPassword = config.getProxyPassword();
        try {
            req.setHttpProxy(proxyHost, proxyPort);
            Authenticator.setDefault(
                    new Authenticator() {
                        @Override
                        public PasswordAuthentication getPasswordAuthentication() {
                            if (StrUtil.isAllNotBlank(proxyUsername, proxyPassword)) {
                                return new PasswordAuthentication(proxyUsername, proxyPassword.toCharArray());
                            }
                            return null;
                        }
                    }
            );
            log.debug("使用代理 {}", url);
        } catch (Exception e) {
            log.error("设置代理出现问题 {}", url);
            log.error(e.getMessage(), e);
        }
    }

    public static String getUrl(HttpResponse response) {
        URL url = ((HttpConnection) ReflectUtil.getFieldValue(response, "httpConnection")).getUrl();
        return url.toString();
    }

    public static void assertStatus(HttpResponse response) {
        boolean ok = response.isOk();
        int status = response.getStatus();
        String url = getUrl(response);
        Assert.isTrue(ok, "url: {}, status: {}", url, status);
    }

    public static void assertXml(HttpResponse response) {
        String url = getUrl(response);
        String contentType = response.header(Header.CONTENT_TYPE);
        Assert.notBlank(contentType, "ContentType 为空, {}", url);

        boolean isXML = contentType.startsWith("application/xml") ||
                contentType.startsWith("application/rss+xml") ||
                contentType.startsWith("text/xml");

        Assert.isTrue(isXML, "非 XML 链接, {} {}", url, contentType);
    }

    /**
     * 是否代理
     *
     * @param url 链接
     * @return 是否使用代理
     */
    public static Boolean isProxy(String url) {
        String host = URLUtil.url(url).getHost();

        Config config = ConfigUtil.CONFIG;
        String proxyList = config.getProxyList();

        String key = StrFormatter.format("proxyList:{}", SecureUtil.md5(proxyList));

        List<String> split = CacheUtils.get(key);

        if (Objects.isNull(split)) {
            split = StrUtil.split(proxyList, "\n", true, true);
            CacheUtils.put(key, split, TimeUnit.MINUTES.toMillis(10));
        }

        if (split.isEmpty()) {
            return false;
        }

        if (split.contains(host)) {
            return true;
        }

        for (String s : split) {
            if (host.endsWith("." + s)) {
                return true;
            }
        }
        return false;
    }

}

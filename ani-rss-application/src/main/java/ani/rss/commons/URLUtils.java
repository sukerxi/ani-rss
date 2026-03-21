package ani.rss.commons;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.lang.PatternPool;
import cn.hutool.core.net.Ipv4Util;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;

import java.net.URL;
import java.util.Comparator;
import java.util.List;

public class URLUtils {
    /**
     * 自动添加http协议
     *
     * @param urlStr 链接
     * @return 处理后的链接
     */
    public static String getUrlStr(String urlStr) {
        if (StrUtil.isBlank(urlStr)) {
            return "";
        }

        if (!ReUtil.contains("^https?://", urlStr)) {
            urlStr = StrFormatter.format("http://{}", urlStr);
        }

        if (urlStr.endsWith("/")) {
            urlStr = urlStr.substring(0, urlStr.length() - 1);
        }

        return urlStr;
    }

    /**
     * 校验url安全性
     */
    public static void verify(String s) {
        Assert.notBlank(s, "URL 为空");

        String regex = "^https?://";

        Assert.isTrue(ReUtil.contains(regex, s), "错误的链接");

        URL url = URLUtil.url(s);

        String host = url.getHost();

        Assert.isFalse(
                List.of("127.0.0.1", "localhost").contains(host),
                "禁止访问回环网络"
        );

        if (PatternPool.IPV4.matcher(host).matches()) {
            Assert.isFalse(Ipv4Util.isInnerIP(host), "禁止访问内部网络");
        }
    }

    /**
     * 解析域名映射规则
     * 每行一个, 格式: 原始域名=目标域名
     * 协议沿用原始链接
     *
     * @param domainMapping 映射规则
     * @return 解析后的规则, 按原始域名长度倒序, 保证最长(最具体)的规则优先
     */
    public static List<Pair<String, String>> parseDomainMapping(String domainMapping) {
        if (StrUtil.isBlank(domainMapping)) {
            return List.of();
        }

        return StrUtil.split(domainMapping, "\n", true, true)
                .stream()
                .map(line -> StrUtil.split(line, "=", 2, true, true))
                .filter(parts -> parts.size() == 2)
                .map(parts -> Pair.of(formatDomain(parts.get(0)), formatDomain(parts.get(1))))
                .filter(pair -> StrUtil.isAllNotBlank(pair.getKey(), pair.getValue()))
                .sorted(Comparator.comparingInt((Pair<String, String> pair) -> pair.getKey().length()).reversed())
                .toList();
    }

    /**
     * 处理域名
     * 统一为不带协议的形式, 兼容直接粘贴完整URL的情况
     *
     * @param domain 域名
     * @return 处理后的域名
     */
    private static String formatDomain(String domain) {
        domain = ReUtil.replaceAll(domain, "^https?://", "");
        return StrUtil.removeSuffix(domain, "/");
    }

    /**
     * 应用域名映射
     * 将链接中的域名替换为映射的目标域名, 协议与路径保持不变
     *
     * @param url   原始链接
     * @param rules 映射规则
     * @return 映射后的链接, 未匹配则返回原链接
     */
    public static String mapDomain(String url, List<Pair<String, String>> rules) {
        if (StrUtil.isBlank(url) || rules.isEmpty() || !ReUtil.contains("^https?://", url)) {
            return url;
        }

        URL parsedUrl = URLUtil.url(url);
        String host = parsedUrl.getHost();
        if (StrUtil.isBlank(host)) {
            return url;
        }

        String targetDomain = null;
        for (Pair<String, String> rule : rules) {
            String domain = rule.getKey();
            if (host.equals(domain) || host.endsWith("." + domain)) {
                targetDomain = rule.getValue();
                break;
            }
        }

        if (StrUtil.isBlank(targetDomain)) {
            return url;
        }

        String base = parsedUrl.getProtocol() + "://" + host;
        int port = parsedUrl.getPort();
        if (port != -1 && port != parsedUrl.getDefaultPort()) {
            base += ":" + port;
        }

        if (!url.startsWith(base)) {
            return url;
        }

        return parsedUrl.getProtocol() + "://" + targetDomain + url.substring(base.length());
    }
}

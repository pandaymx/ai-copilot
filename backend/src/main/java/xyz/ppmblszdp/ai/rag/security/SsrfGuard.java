package xyz.ppmblszdp.ai.rag.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/**
 * SSRF（服务端请求伪造）防护校验器。
 *
 * <p>
 * 在 URL 抓取前校验目标地址合法性，拦截：
 * <ul>
 * <li>非 HTTP/HTTPS 协议（如 file://、ftp://）</li>
 * <li>Loopback 地址（127.0.0.0/8、::1）</li>
 * <li>链路本地地址（169.254.0.0/16，含 AWS/云元数据 169.254.169.254）</li>
 * <li>私有网络地址（10.0.0.0/8、172.16.0.0/12、192.168.0.0/16）</li>
 * </ul>
 *
 * <p>
 * 用法：在 {@code JsoupHtmlCleaningReader} 中，{@code Jsoup.connect()} 之前调用
 * {@code SsrfGuard.validate(url)}；若 URL 命中安全规则则抛出 {@link SsrfBlockedException}，
 * Controller 应当转为 400 响应。
 */
public final class SsrfGuard {

    private static final Logger log = LoggerFactory.getLogger(SsrfGuard.class);

    /** 云元数据服务 IP（AWS EC2 / GCP / 阿里云 等），最敏感的 SSRF 靶标 */
    public static final String METADATA_IP = "169.254.169.254";

    private static final List<String> IPV4_PRIVATE_PREFIXES = List.of(
            "10.", // 10.0.0.0/8
            "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.", // 172.16.0.0/12
            "192.168.", // 192.168.0.0/16
            "127.", // 127.0.0.0/8 (Loopback)
            "169.254.", // 169.254.0.0/16 (Link-local / Metadata)
            "0.", // 0.0.0.0/8 (Current network)
            "100.64.", "100.65.", "100.66.", "100.67.",
            "100.68.", "100.69.", "100.70.", "100.71.",
            "100.72.", "100.73.", "100.74.", "100.75.",
            "100.76.", "100.77.", "100.78.", "100.79.",
            "100.80.", "100.81.", "100.82.", "100.83.",
            "100.84.", "100.85.", "100.86.", "100.87.",
            "100.88.", "100.89.", "100.90.", "100.91.",
            "100.92.", "100.93.", "100.94.", "100.95.",
            "100.96.", "100.97.", "100.98.", "100.99.",
            "100.100.", "100.101.", "100.102.", "100.103.",
            "100.104.", "100.105.", "100.106.", "100.107.",
            "100.108.", "100.109.", "100.110.", "100.111.",
            "100.112.", "100.113.", "100.114.", "100.115.",
            "100.116.", "100.117.", "100.118.", "100.119.",
            "100.120.", "100.121.", "100.122.", "100.123.",
            "100.124.", "100.125.", "100.126.", "100.127." // 100.64.0.0/10 (CGNAT)
    );

    private SsrfGuard() {
    }

    /**
     * 校验 URL 合法性。仅允许 HTTP/HTTPS 协议访问公网地址。
     *
     * @param urlString 待抓取的 URL
     * @throws SsrfBlockedException     如果 URL 命中安全规则（协议违规、内网/回环/元数据地址）
     * @throws IllegalArgumentException 如果 URL 格式不合法
     */
    public static void validate(String urlString) {
        URI uri;
        try {
            uri = URI.create(urlString.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL 格式不合法: " + urlString, e);
        }

        // 1. 协议约束：仅允许 HTTP/HTTPS
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new SsrfBlockedException("仅允许 http/https 协议，当前协议被拦截: " + uri);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少主机名: " + uri);
        }

        // 2. DNS 解析目标地址
        InetAddress resolved;
        try {
            resolved = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfBlockedException("无法解析目标主机: " + host);
        }

        // 3. 地址校验
        if (resolved.isLoopbackAddress()) {
            logSsrfBlock(uri, resolved, "回环地址");
            throw new SsrfBlockedException("禁止访问回环地址: " + resolved.getHostAddress());
        }
        if (resolved.isLinkLocalAddress()) {
            logSsrfBlock(uri, resolved, "链路本地地址/元数据地址");
            throw new SsrfBlockedException("禁止访问链路本地或元数据地址: " + resolved.getHostAddress());
        }
        if (resolved.isSiteLocalAddress()) {
            logSsrfBlock(uri, resolved, "私有网络地址");
            throw new SsrfBlockedException("禁止访问内网地址: " + resolved.getHostAddress());
        }

        // 4. 兜底：按字符串前缀遍历私有 IP 段（防止某些 DNS/配置绕过 Java API）
        String addr = resolved.getHostAddress();
        if (METADATA_IP.equals(addr)) {
            logSsrfBlock(uri, resolved, "云元数据服务地址");
            throw new SsrfBlockedException("禁止访问云元数据服务: " + addr);
        }
        for (String prefix : IPV4_PRIVATE_PREFIXES) {
            if (addr.startsWith(prefix)) {
                logSsrfBlock(uri, resolved, "私有/保留 IP 段 " + prefix + "*");
                throw new SsrfBlockedException("禁止访问保留地址段: " + addr);
            }
        }
    }

    private static void logSsrfBlock(URI uri, InetAddress resolved, String reason) {
        log.warn("[SSRF 防护] 拦截恶意请求: uri={} host={} addr={} reason={}",
                uri, uri.getHost(), resolved, reason);
    }
}

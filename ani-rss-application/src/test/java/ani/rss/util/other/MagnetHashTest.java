package ani.rss.util.other;

import cn.hutool.core.codec.Base32;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 磁力链接识别与 btih info hash 解析测试，
 * 覆盖 40 位十六进制、32 位 Base32、附加参数与非法磁力等场景。
 */
class MagnetHashTest {

    private static final String HEX_HASH = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
    private static final String BASE32_HASH = "3I42H3S6NNFQ2MSVX7XZKYAYSCX5QBYJ";

    @Test
    void isMagnet() {
        assertTrue(TorrentUtil.isMagnet("magnet:?xt=urn:btih:" + HEX_HASH));
        assertTrue(TorrentUtil.isMagnet("MAGnet:?xt=urn:btih:abc"));
        assertFalse(TorrentUtil.isMagnet(HEX_HASH));
        assertFalse(TorrentUtil.isMagnet(""));
        assertFalse(TorrentUtil.isMagnet(null));
    }

    @Test
    void parseHexHash() {
        // 小写十六进制
        String magnet = "magnet:?xt=urn:btih:" + HEX_HASH +
                "&dn=test&tr=http%3A%2F%2Ftracker.example.com%2Fannounce";
        assertEquals(HEX_HASH, TorrentUtil.parseMagnetHash(magnet));

        // 大写十六进制统一转为小写
        assertEquals(HEX_HASH, TorrentUtil.parseMagnetHash("magnet:?xt=urn:btih:" + HEX_HASH.toUpperCase()));
    }

    @Test
    void parseBase32Hash() {
        // 固定向量：SHA1("") 的 Base32 编码
        assertEquals(HEX_HASH, TorrentUtil.parseMagnetHash("magnet:?xt=urn:btih:" + BASE32_HASH));

        // Base32 十六进制两种编码应解析为同一 hash
        String randomHex = "0123456789abcdef0123456789abcdef01234567";
        String base32 = Base32.encode(HexFormat.of().parseHex(randomHex));
        assertEquals(randomHex, TorrentUtil.parseMagnetHash("magnet:?xt=urn:btih:" + base32));
    }

    @Test
    void parseInvalidMagnet() {
        // 非磁力链接
        assertThrows(IllegalArgumentException.class, () -> TorrentUtil.parseMagnetHash(HEX_HASH));
        // 空白
        assertThrows(IllegalArgumentException.class, () -> TorrentUtil.parseMagnetHash(" "));
        // 缺少 btih
        assertThrows(IllegalArgumentException.class,
                () -> TorrentUtil.parseMagnetHash("magnet:?dn=test"));
        // 纯 v2（btmh）磁力暂不支持
        assertThrows(IllegalArgumentException.class,
                () -> TorrentUtil.parseMagnetHash("magnet:?xt=urn:btmh:1220" + "a".repeat(64)));
        // hash 长度非法
        assertThrows(IllegalArgumentException.class,
                () -> TorrentUtil.parseMagnetHash("magnet:?xt=urn:btih:abc123"));
    }
}

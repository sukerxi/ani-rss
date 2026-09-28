package ani.rss.enums;

import cn.hutool.core.util.ReUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StringEnum#INFO_HASH_REG} info hash 判定回归测试，
 * 覆盖 RSS guid 误判为种子哈希的历史场景（纯数字 ID、URL、非十六进制字符）。
 */
class InfoHashRegexTest {

    @Test
    void validHash() {
        assertTrue(ReUtil.isMatch(StringEnum.INFO_HASH_REG, "a".repeat(40)));
        // 大写哈希同样合法，业务侧使用前会统一转小写
        assertTrue(ReUtil.isMatch(StringEnum.INFO_HASH_REG, "0123456789ABCDEF0123456789abcdef01234567"));
    }

    @Test
    void invalidHash() {
        assertFalse(ReUtil.isMatch(StringEnum.INFO_HASH_REG, "12345"));
        assertFalse(ReUtil.isMatch(StringEnum.INFO_HASH_REG, "https://example.com/detail/12345"));
        assertFalse(ReUtil.isMatch(StringEnum.INFO_HASH_REG, "g".repeat(40)));
        assertFalse(ReUtil.isMatch(StringEnum.INFO_HASH_REG, "a".repeat(39)));
        assertFalse(ReUtil.isMatch(StringEnum.INFO_HASH_REG, "A1B2-C3D4-E5F6"));
    }
}

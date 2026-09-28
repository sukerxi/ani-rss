package ani.rss.util.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BgmUtil#getSeasonByName(String)} 季数解析回归测试（纯字符串解析，无需 Spring 上下文）。
 */
class BgmSeasonTest {

    @Test
    void parseSeasonFormats() {
        assertEquals(2, BgmUtil.getSeasonByName("第二季"));
        assertEquals(2, BgmUtil.getSeasonByName("第二期"));
        assertEquals(3, BgmUtil.getSeasonByName("Season 3"));
        assertEquals(3, BgmUtil.getSeasonByName("3rd Season"));
        assertEquals(2, BgmUtil.getSeasonByName("S02"));
    }

    @Test
    void firstMatchHasPriority() {
        // 中文季名优先级最高，即使后面同时出现英文写法也不应被覆盖
        assertEquals(2, BgmUtil.getSeasonByName("第二季 Season 3"));
        assertEquals(2, BgmUtil.getSeasonByName("第二季 S02"));
    }

    @Test
    void noSeasonDefaultsToOne() {
        assertEquals(1, BgmUtil.getSeasonByName("普通番剧标题"));
        // 集数「第12话」不应被误判为季数
        assertEquals(1, BgmUtil.getSeasonByName("番剧 第12话"));
    }
}

package ani.rss.commons;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EpisodeParser} 集数解析回归测试。
 * 覆盖主流字幕组命名、常见书写变体、x.5 集，以及分辨率 / 年份 / 季数 / 标题数字等反误判场景。
 */
class EpisodeParserTest {

    private record Case(String title, double expected, boolean present) {
    }

    private static Case ok(String title, double episode) {
        return new Case(title, episode, true);
    }

    private static Case miss(String title) {
        return new Case(title, 0, false);
    }

    private static final List<Case> CASES = List.of(
            // —— 主流命名 / 强标记 ——
            ok("[ANi] 葬送的芙莉莲 - 05 [1080P][WEB-DL][AAC AVC][CHT].mp4", 5),
            ok("[字幕组] Frieren / 葬送のフリーレン [05][1080p][GB][MP4]", 5),
            ok("[字幕组] Anime S02E05 1080p HEVC x265", 5),
            ok("[Erai-raws] Frieren - 05 (1080p)", 5),
            miss("[Erai-raws] Frieren [1080p]"),
            ok("[喵萌奶茶屋&LoliHouse] 药屋少女的呢喃 S2 - 05 [WebRip 1080p HEVC10bit AAC]", 5),
            ok("[LoliHouse] Kusuriya no Hitorigoto S2 - 05 [WebRip 1080p HEVC10bit AAC]", 5),
            ok("[喵萌奶茶屋] 咒术回战 05 [WebRip 1080p HEVC10bit AAC]", 5),
            ok("[SnowRaws] Foo [05][BDRip 1920x1080 x264 10bit FLAC]", 5),
            // —— 书写变体 ——
            ok("[组] Show Vol.05 1080p", 5),
            ok("[组] Show VOL.05-V2 1080p", 5),
            ok("[组] Show Episode 05 1080p", 5),
            ok("[组] Show EP.05 1080p", 5),
            ok("[组] Show #05 1080p", 5),
            ok("[组] Show 第05话 1080p", 5),
            ok("[组] Show 第05話 1080p", 5),
            ok("[组] Show EP05 1080p", 5),
            ok("[组] Show - 05v2 [1080p]", 5),
            ok("[组] Show [05v2][1080p]", 5),
            ok("[组] Show [05 END][1080p]", 5),
            ok("[组] Show 2nd Season [12] [1920x1080 HEVC AAC]", 12),
            // —— 无集数 / 合集 ——
            miss("[组] H264 10bit Show [1080p] 合集"),
            miss("[ANi] Title - 1080P 全集打包"),
            miss("[组] Title - 2024 OVA [1080p]"),
            miss("[ANi] Title - 2160P 合集"),
            ok("[组] Code Geass R2 [05] x265 10bit", 5),
            // —— 特例 ——
            ok("六四位元字幕组 something ★05★ 1080p", 5),
            ok("[TOC] something 05", 5),
            ok("[SweetSub] 咒术回战 第二季 [05][WebRip 1080p HEVC10bit AAC][简繁内封]", 5),
            // —— x.5 集 ——
            ok("[组] Show - 12.5 [1080p]", 12.5),
            ok("[组] Show【12.5】[1080p]", 12.5),
            ok("[組] Show 第12話(完) [1080p]", 12),
            // —— 特典默认不订阅 ——
            miss("[组] Show - SP01 [1080p]"),
            // —— 裸数字兜底与标题干扰 ——
            ok("[ANi] 我推的孩子 -【推しの子】 - 05 [1080P].mp4", 5),
            ok("[组] Show 05v2 1080p", 5),
            ok("[组] 86 -不存在的战区- 05 [1080p]", 5),
            ok("[组] 2.43 清阴高校男子排球部 11 [1080p]", 11),
            ok("[千夏字幕组] 间谍过家家 第05话 1080p", 5),
            // —— 反误判：季数 / 标题数字 ——
            miss("[组] Show Season 2 [1080p]"),
            miss("[组] Show Part 2 AVC [1080p]"),
            miss("[组] 3月的狮子 [1080p]"),
            miss("[组] 86 -不存在的战区- [1080p]"),
            ok("[组] 5等分的新娘∬ 06 [1080p]", 6),
            ok("[组] 海贼王 1012 [1080p]", 1012),
            ok("[组] 22/7 05 [1080p]", 5),
            miss("[组] Show 第2季 [1080p]"),
            miss("[组] Show 第二季[1080p]")
    );

    @Test
    void parse() {
        int passed = 0;
        for (Case c : CASES) {
            EpisodeParser.Result result = EpisodeParser.parse(c.title());
            if (c.present()) {
                assertTrue(result.present(),
                        () -> "应识别到集数却被过滤: " + c.title());
                assertNotNull(result.source());
                assertEquals(c.expected(), result.episode(), 1e-9,
                        () -> "集数解析错误: " + c.title() + "，命中来源=" + result.source());
            } else {
                assertFalse(result.present(),
                        () -> "应过滤却识别为第 " + result.episode() + " 集（" + result.source() + "）: " + c.title());
            }
            passed++;
        }
        assertEquals(CASES.size(), passed);
    }

    @Test
    void blankInput() {
        assertFalse(EpisodeParser.parse(null).present());
        assertFalse(EpisodeParser.parse("   ").present());
    }
}

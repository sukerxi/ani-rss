package ani.rss.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 备用 RSS 补位模式
 */
@Getter
@AllArgsConstructor
public enum StandbyModeEnum {
    /**
     * 跟随全局设置（旧订阅兼容值）
     */
    DEFAULT("default"),
    /**
     * 洗版：严格按优先级补位，高优先级来源后出同集时替换低优先级版本
     */
    REPLACE("replace"),
    /**
     * 不覆盖：先到先得，同集一旦下载不再被高优先级来源替换
     */
    STICKY("sticky"),
    /**
     * 共存：同集各字幕组版本全部保留
     */
    COEXIST("coexist");

    private final String value;
}

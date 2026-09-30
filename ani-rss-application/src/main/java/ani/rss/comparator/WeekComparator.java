package ani.rss.comparator;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class WeekComparator implements Comparator<String>, Serializable {
    private static final List<String> WEEK_ORDER = List.of(
            "(星期|周)日", "(星期|周)一", "(星期|周)二", "(星期|周)三", "(星期|周)四", "(星期|周)五", "(星期|周)六"
    );

    // 每次创建时按当天星期生成，避免服务长期运行跨午夜后排序过期
    private final List<String> weekSort = getIndexList();

    public WeekComparator() {
    }

    private List<String> getIndexList() {
        List<String> weekList = new ArrayList<>();
        int dayOfWeek = DateUtil.dayOfWeek(new Date()) - 1;

        for (int i = dayOfWeek; i < WEEK_ORDER.size(); i++) {
            weekList.add(WEEK_ORDER.get(i));
        }

        for (int i = 0; i < dayOfWeek; i++) {
            weekList.add(WEEK_ORDER.get(i));
        }
        return weekList;
    }

    private int getIndexOf(String s) {
        for (int i = 0; i < weekSort.size(); i++) {
            if (ReUtil.contains(weekSort.get(i), s)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public int compare(String str1, String str2) {
        int index1 = getIndexOf(str1);
        int index2 = getIndexOf(str2);
        return Integer.compare(index1, index2);
    }
}

package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zhimu.slmp.network.model.PageVO;

/**
 * API-D36 data：分页字段与 tab_counts（pending、done、overdue），另加 overdue_banner（员工不输出）。
 */
public class DisinfectTaskPageVO extends PageVO<DisinfectTaskVO> {

    @Nullable
    private OverdueBanner overdueBanner;

    @Nullable
    public OverdueBanner getOverdueBanner() {
        return overdueBanner;
    }

    /** 逾期提醒横幅 */
    public static class OverdueBanner {

        private boolean show;
        @Nullable
        private String text;

        public boolean isShow() {
            return show;
        }

        @Nullable
        public String getText() {
            return text;
        }
    }
}

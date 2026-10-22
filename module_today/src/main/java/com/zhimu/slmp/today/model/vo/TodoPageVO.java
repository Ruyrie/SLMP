package com.zhimu.slmp.today.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.network.model.PageVO;

import java.util.Collections;
import java.util.List;

/**
 * 今天和已逾期（API-T02 data）：PageVO + tab_counts（父类）+ pen_counts（仅员工）。
 */
public class TodoPageVO extends PageVO<TodoItemVO> {

    /** 员工按栏舍计数 */
    public static class PenCount {
        private long penId;
        @Nullable
        private String penName;
        private int count;

        public long getPenId() {
            return penId;
        }

        @Nullable
        public String getPenName() {
            return penName;
        }

        public int getCount() {
            return count;
        }
    }

    @Nullable
    private List<PenCount> penCounts;

    @NonNull
    public List<PenCount> getPenCounts() {
        return penCounts == null ? Collections.emptyList() : penCounts;
    }
}

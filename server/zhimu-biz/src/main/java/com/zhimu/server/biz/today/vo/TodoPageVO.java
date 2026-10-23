package com.zhimu.server.biz.today.vo;

import com.zhimu.server.common.api.PageVO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 今天和已逾期（API-T02 data）：PageVO + tab_counts（父类）+ pen_counts（仅员工）。 */
@Getter
@Setter
public class TodoPageVO extends PageVO<TodoItemVO> {

    /** 员工按栏舍计数 */
    @Getter
    @Setter
    public static class PenCount {
        private Long penId;
        private String penName;
        private Integer count;
    }

    private List<PenCount> penCounts;

    public TodoPageVO() {
    }

    public TodoPageVO(List<TodoItemVO> list, long total, int page, int size) {
        super(list, total, page, size);
    }
}

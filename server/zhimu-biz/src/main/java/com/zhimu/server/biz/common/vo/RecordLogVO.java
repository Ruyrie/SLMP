package com.zhimu.server.biz.common.vo;

import com.zhimu.server.common.vo.UserBriefVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 记录的修改记录（API-U14，App lib_common RecordLogVO）。
 * 只含用户端日志（operator_type = USER）；由修改申请回写的带 change_request_no。
 * changes.before / after 是带单位的显示文字，field_name 为中文；op_action 固定 UPDATE / VOID / DELETE。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordLogVO {

    private Long id;
    private UserBriefVO operator;
    private String opAction;
    private String opActionText;
    @Builder.Default
    private List<Change> changes = new ArrayList<>();
    private String reason;
    private String changeRequestNo;
    private LocalDateTime createdAt;

    /** 一项字段变化 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Change {
        private String fieldName;
        private String before;
        private String after;
    }
}

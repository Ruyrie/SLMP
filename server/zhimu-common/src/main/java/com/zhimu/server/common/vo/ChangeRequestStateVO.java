package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 每条可修改记录都带：是否还能直接修改、直接修改截止时间、待审核的修改申请 ID */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRequestStateVO {
    private Boolean canEdit;
    private LocalDateTime editDeadline;
    private Long pendingRequestId;
}
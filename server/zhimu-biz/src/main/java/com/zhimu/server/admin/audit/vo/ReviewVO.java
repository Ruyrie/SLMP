package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 已处理审核的结果摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO {

    /** APPROVED / REJECTED */
    private String result;
    private String resultText;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String opinion;
    private String rejectReason;
}

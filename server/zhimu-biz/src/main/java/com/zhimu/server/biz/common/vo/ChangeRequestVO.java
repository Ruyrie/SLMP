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
 * U02 我的修改申请列表与详情（API-U03、U04，App lib_common ChangeRequestVO）。
 * 字段名严格按 App：farm_id / farm_name / field_code / recalc_summary / biz_id / batch_id / after_value / status_text / impacts / recalc_tags。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRequestVO {

    private Long id;
    private String requestNo;
    private String bizType;
    /** UI 长名，例如 投喂记录、入库单 */
    private String bizTypeText;
    private Long bizId;
    private Long farmId;
    private String farmName;
    private Long batchId;
    private String recordLocator;
    /** UPDATE / DELETE / VOID */
    private String changeType;
    private String fieldCode;
    private String fieldName;
    private String valueUnit;
    private String beforeDisplay;
    private String afterDisplay;
    /** 主修改项的新值（纯值，不含单位）；作废、删除为空 */
    private String afterValue;
    private String reason;
    @Builder.Default
    private List<String> impacts = new ArrayList<>();
    @Builder.Default
    private List<String> recalcTags = new ArrayList<>();
    /** PENDING / APPROVED / REJECTED / CANCELED */
    private String status;
    /** 始终返回：待审核 / 已通过 / 已驳回 / 已撤销 */
    private String statusText;
    private UserBriefVO submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String rejectReason;
    /** 通过后的重新计算结果：已重新计算批次收益 */
    private String recalcSummary;
}

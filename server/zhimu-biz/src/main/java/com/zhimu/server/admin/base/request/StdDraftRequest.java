package com.zhimu.server.admin.base.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ADM-D08 保存草稿的联合请求体：weight 与 feed 共用路径，按 std_type 取不同字段。
 * 具体校验在 StdAdminService 中完成（控制器不按类型做条件校验，避免另一类型的必填误报）。
 */
@Data
public class StdDraftRequest {

    // ===== weight =====
    private BigDecimal toleranceRate;
    private Integer suggestedWeighTimes;
    private String effectiveMode;
    private LocalDate effectiveDate;
    private List<StdWeightDraftRequest.Point> points = new ArrayList<>();

    // ===== feed =====
    private BigDecimal deviationRate;
    private List<FeedStdDraftRequest.Stage> stages = new ArrayList<>();

    /** 已有草稿时必填 */
    private Integer version;

    /** 暂存在草稿上的更新说明（选填，最多 100 字，发布时以发布请求为准） */
    private String updateNote;
}

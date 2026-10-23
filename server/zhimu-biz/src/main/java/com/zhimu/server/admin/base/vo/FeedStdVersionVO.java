package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 饲喂标准版本（ADM-D07 的 online / draft，ADM-D11–D13 复用）。 */
@Data
public class FeedStdVersionVO {

    private Long versionId;
    /** 草稿为空 */
    private Integer versionNo;
    private String status;
    private String statusText;
    private BigDecimal deviationRate;
    private String updateNote;
    private String publishedByName;
    private LocalDateTime publishedAt;
    /** 套用方案数，待模块06；仅线上，本批 0 */
    private Integer appliedPlanCount;
    /** 草稿乐观锁 */
    private Integer version;
    private List<FeedStdStageVO> stages = new ArrayList<>();
}

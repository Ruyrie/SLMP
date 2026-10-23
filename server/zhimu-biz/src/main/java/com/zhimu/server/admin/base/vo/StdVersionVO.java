package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 标准版本记录（ADM-D11–D13）：带该版的日龄点或阶段，供前端差异对比。 */
@Data
public class StdVersionVO {

    private Long versionId;
    private Integer versionNo;
    private String status;
    private String statusText;
    private String effectiveMode;
    private LocalDate effectiveDate;
    private String updateNote;
    private Integer rollbackFromNo;
    private String publishedByName;
    private LocalDateTime publishedAt;
    /** 标准体重版本 */
    private BigDecimal toleranceRate;
    private Integer suggestedWeighTimes;
    /** 饲喂标准版本 */
    private BigDecimal deviationRate;
    private List<StdWeightPointVO> points;
    private List<FeedStdStageVO> stages;
}

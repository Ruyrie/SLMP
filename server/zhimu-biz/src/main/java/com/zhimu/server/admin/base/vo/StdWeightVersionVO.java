package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 标准体重版本（ADM-D07 的 online / scheduled / draft，ADM-D11–D13 也复用）。 */
@Data
public class StdWeightVersionVO {

    private Long versionId;
    /** 草稿为空 */
    private Integer versionNo;
    private String status;
    private String statusText;
    private BigDecimal toleranceRate;
    private Integer suggestedWeighTimes;
    private String effectiveMode;
    private LocalDate effectiveDate;
    private LocalDateTime effectiveAt;
    private String updateNote;
    private String publishedByName;
    private LocalDateTime publishedAt;
    private LocalDateTime recalculatedAt;
    private List<StdWeightPointVO> points = new ArrayList<>();
    /** 草稿：已修改的日龄点数 */
    private Integer changedCount;
    /** 草稿：草稿乐观锁 */
    private Integer version;
}

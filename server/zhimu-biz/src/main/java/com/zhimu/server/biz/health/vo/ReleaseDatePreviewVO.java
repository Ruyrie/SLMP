package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D21 预估可出栏日期与预计用量（纯计算，不落库）。 */
@Data
public class ReleaseDatePreviewVO {

    private Boolean ready;
    private LocalDate lastDate;
    private LocalDate releaseDate;
    /** 11月6日 周五 */
    private String releaseText;
    private BigDecimal estimatedQty;
    private String usedUnit;
    private Boolean overDose;
    private Boolean categoryMismatch;
    private List<PerBatch> perBatch = new ArrayList<>();

    @Data
    public static class PerBatch {
        private Long batchId;
        private String batchNo;
        private LocalDate releaseDate;
        private BigDecimal estimatedQty;
    }
}

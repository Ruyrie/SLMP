package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-D19 用药对象下拉：BATCH 带批次，PEN 带栏舍内批次。 */
@Data
public class MedicationTargetVO {

    private Long batchId;
    private String batchNo;
    private Long penId;
    private String penName;
    private Integer currentCount;
    private Long categoryId;
    private Boolean hasBatch;
    private List<BatchItem> batches = new ArrayList<>();

    @Data
    public static class BatchItem {
        private Long batchId;
        private String batchNo;
        private Integer stockCount;
    }
}

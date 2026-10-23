package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D22 登记用药结果。 */
@Data
public class MedicationCreateResultVO {

    private List<Long> recordIds = new ArrayList<>();
    private String groupNo;
    private List<PerBatch> perBatch = new ArrayList<>();
    private String toast;

    @Data
    public static class PerBatch {
        private String batchNo;
        private LocalDate releaseDate;
    }
}

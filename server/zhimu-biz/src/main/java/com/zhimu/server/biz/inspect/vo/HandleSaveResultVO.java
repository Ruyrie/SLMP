package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** API-I22 保存处理结果。 */
@Data
public class HandleSaveResultVO {

    private long reportId;
    private long handleId;
    private String status;
    private boolean closed;
    private int culledCount;
    private DeathPrompt deathPrompt;
    private DiagnosisDetailVO.MedicationPrefill medicationPrefill;
    private int version;

    @Data
    public static class DeathPrompt {
        private long penId;
        private long batchId;
        private int culledCount;
    }
}

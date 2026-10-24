package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** R21 恢复码校验记录：记录条数、时间、结果、失败次数、剩余次数、IP 与设备。 */
@Data
public class RecoveryVerificationRecordsVO {

    private Long recoveryId;
    private Integer total = 0;
    private List<Record> records = new ArrayList<>();

    @Data
    @Builder
    public static class Record {
        /** 在当前列表中的序号，1 为最近一次 */
        private Integer seq;
        private LocalDateTime checkedAt;
        private String result;
        private String resultText;
        private String resultDesc;
        /** 本次校验后的连续失败次数 */
        private Integer failCount;
        private Integer remainingAttempts;
        private LocalDateTime lockUntil;
        private String ip;
        private String ipRegion;
        /** 提交渠道对应的设备文字；表无独立设备字段 */
        private String device;
        private String submitChannelText;
        private Long proxyAdminId;
        private String proxyAdminName;
    }
}

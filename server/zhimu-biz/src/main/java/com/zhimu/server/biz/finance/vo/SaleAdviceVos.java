package com.zhimu.server.biz.finance.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI 出栏建议（API-E33–E36）响应结构。 */
public final class SaleAdviceVos {

    private SaleAdviceVos() {
    }

    @Data
    public static class SaleAdviceVO {
        private Long id;
        private Long batchId;
        private String batchNo;
        private String penName;
        private String status;
        private String conclusion;
        private String conclusionText;
        private Boolean inWithdrawal;
        private Boolean basisInsufficient;
        private String generatedAt;
        private String windowStart;
        private String windowEnd;
        private String sellableDate;
        private Integer withdrawalDaysLeft;
        private AdviceReasons reasons;
        private List<String> risks = new ArrayList<>();
        private String basisNote;
        private String batchEndDate;
        private String sourceLabel;
    }

    @Data
    public static class AdviceReasons {
        private String withdrawal;
        private String age;
        private String weight;
        private String price;
    }
}

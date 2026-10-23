package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * API-E33–E36 AI 出栏建议（历史列表只输出 id、conclusion、batch_no、generated_at、batch_end_date）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SaleAdviceVO {

    private long id;
    private long batchId;
    private String batchNo;
    @Nullable
    private String penName;
    /** GENERATING / SUCCESS / FAILED */
    private String status;
    /** SELL_SOON / KEEP_FEEDING / NOT_SELLABLE */
    @Nullable
    private String conclusion;
    @Nullable
    private String conclusionText;
    private boolean inWithdrawal;
    private boolean basisInsufficient;
    @Nullable
    private String generatedAt;
    @Nullable
    private String windowStart;
    @Nullable
    private String windowEnd;
    @Nullable
    private String sellableDate;
    @Nullable
    private Integer withdrawalDaysLeft;
    @Nullable
    private AdviceReasons reasons;
    private List<String> risks;
    @Nullable
    private String basisNote;
    @Nullable
    private String batchEndDate;
    /** 客户端字段：测试直连时实际使用的模型（千问 / DeepSeek 备用） */
    @Nullable
    private String sourceLabel;

    public long getId() {
        return id;
    }

    public long getBatchId() {
        return batchId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    public String getStatus() {
        return status;
    }

    @Nullable
    public String getConclusion() {
        return conclusion;
    }

    @Nullable
    public String getConclusionText() {
        return conclusionText;
    }

    public boolean isInWithdrawal() {
        return inWithdrawal;
    }

    public boolean isBasisInsufficient() {
        return basisInsufficient;
    }

    @Nullable
    public String getGeneratedAt() {
        return generatedAt;
    }

    @Nullable
    public String getWindowStart() {
        return windowStart;
    }

    @Nullable
    public String getWindowEnd() {
        return windowEnd;
    }

    @Nullable
    public String getSellableDate() {
        return sellableDate;
    }

    @Nullable
    public Integer getWithdrawalDaysLeft() {
        return withdrawalDaysLeft;
    }

    @Nullable
    public AdviceReasons getReasons() {
        return reasons;
    }

    @NonNull
    public List<String> getRisks() {
        return risks == null ? java.util.Collections.emptyList() : risks;
    }

    @Nullable
    public String getBasisNote() {
        return basisNote;
    }

    @Nullable
    public String getBatchEndDate() {
        return batchEndDate;
    }

    @Nullable
    public String getSourceLabel() {
        return sourceLabel;
    }

    /** 四项理由 */
    public static class AdviceReasons {

        @Nullable
        private String withdrawal;
        @Nullable
        private String age;
        @Nullable
        private String weight;
        @Nullable
        private String price;

        @Nullable
        public String getWithdrawal() {
            return withdrawal;
        }

        @Nullable
        public String getAge() {
            return age;
        }

        @Nullable
        public String getWeight() {
            return weight;
        }

        @Nullable
        public String getPrice() {
            return price;
        }
    }
}

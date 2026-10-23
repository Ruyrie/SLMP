package com.zhimu.server.biz.common.change;

/**
 * 审核通过后来源模块回写与重算的结果。
 *
 * @param recalcCount   重算项目数
 * @param recalcSummary 重算结果摘要（"已重新计算批次收益"）
 */
public record ChangeApplyResult(Integer recalcCount, String recalcSummary) {

    public static ChangeApplyResult done(String recalcSummary) {
        return new ChangeApplyResult(null, recalcSummary);
    }
}

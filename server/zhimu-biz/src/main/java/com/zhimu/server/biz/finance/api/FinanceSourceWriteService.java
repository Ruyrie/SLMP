package com.zhimu.server.biz.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 原始业务单据在自身事务中同步自动支出；按来源唯一，不进入手工记账或批次成本链路。 */
public interface FinanceSourceWriteService {
    record InboundExpense(Long farmId, String sourceType, Long sourceId, String sourceNo,
                          BigDecimal amount, LocalDate recordDate, String detailText,
                          boolean active, Long actorId) { }

    /** 返回对应财务记录ID；未开启同步且从未记账时为空。关闭同步时保留已作废记录以供追溯。 */
    Long syncInboundExpense(InboundExpense source);

    record ReturnRefundIncome(Long farmId, Long outboundId, String outboundNo, BigDecimal amount,
                              LocalDate refundDate, String detailText, Long actorId, Long financeRecordId) { }

    /** 原单锁定事务内登记/更正实际退款，始终更新同一来源收入，不碰库存或成本。 */
    Long syncReturnRefundIncome(ReturnRefundIncome source);
}

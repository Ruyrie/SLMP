package com.zhimu.server.biz.common.change;

import com.zhimu.server.common.enums.BizType;

/**
 * 修改申请的来源记录适配接口（管理端-01 2.9.1 ChangeRequestApplier 的公共版本）。
 * <p>
 * 各业务模块（模块04–10）实现本接口并注册为 Spring Bean，按 {@link BizType} 提供表单、校验与审核回写；
 * 公共能力（biz.common）负责角色与养殖场状态、同一记录只有一条待审核、record_version 比对、原因长度、
 * 状态流转、留痕与消息，来源模块只负责本类记录的业务规则。
 * <p>
 * 本批（第 1 批）没有 17 种记录的真实实现，用测试 handler 验证整条链路；生产环境没有对应 handler 时
 * U01/U02 返回 42207，不阻止应用启动。
 */
public interface ChangeRequestHandler {

    /** 本实现负责的记录类型（可多个） */
    java.util.Set<BizType> supportedBizTypes();

    /** 来源模块（t_change_request.source_module） */
    com.zhimu.server.common.enums.SourceModule sourceModule();

    /**
     * 读取原记录（不过滤当前 X-Farm-Id，跨养殖场按记录所属 farm 判权）。
     * 记录不存在、不属于任何可见养殖场时返回 null；已删除或已作废的记录照常返回，由公共能力返回 41001。
     */
    ChangeRecord loadRecord(BizType bizType, Long bizId);

    /** 审核通过前重新读取原记录（实现方可加行锁）；默认与 {@link #loadRecord} 相同 */
    default ChangeRecord loadRecordForUpdate(BizType bizType, Long bizId) {
        return loadRecord(bizType, bizId);
    }

    /** 是否必须走修改申请；可以直接修改时返回 false（公共能力返回 42207） */
    boolean requiresRequest(ChangeRecord record);

    /** U01 表单：原记录卡、可改字段、联动规则、审核提示与作废说明 */
    ChangeForm describeForm(ChangeRecord record);

    /**
     * U02 校验新值：不合格抛 40001（field_errors 的键只用 field_code、new_value、reason）。
     * DELETE、VOID 无主修改项时返回的主修改项信息为空即可。
     */
    ChangeValidation validateChange(ChangeRecord record, ChangeForm form, com.zhimu.server.common.enums.ChangeType changeType,
                                    String fieldCode, String newValue);

    /**
     * 审核通过前校验：BLOCK 时抛 BizException（错误码由来源模块给出，例如 42201、42205）；默认通过。
     */
    default void validateForApprove(com.zhimu.server.biz.common.entity.ChangeRequest request, ChangeRecord record) {
    }

    /**
     * 审核通过后改写、删除或作废原记录，并同步重算；在同一事务内调用。
     * 实现方负责写原记录的用户端留痕（RECORD_EDIT，change_request_id 关联本申请）。
     */
    ChangeApplyResult apply(com.zhimu.server.biz.common.entity.ChangeRequest request, ChangeRecord record, Long adminId);

    /** 提交后的可选动作（例如维护业务表的待审核申请 ID）；在事务内调用 */
    default void onSubmitted(com.zhimu.server.biz.common.entity.ChangeRequest request) {
    }

    /** 离开 PENDING（通过、驳回、系统撤销）后的可选动作 */
    default void onClosed(com.zhimu.server.biz.common.entity.ChangeRequest request) {
    }
}

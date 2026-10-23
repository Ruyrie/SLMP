package com.zhimu.server.common.enums;

import lombok.Getter;

/**
 * 消息类型（05 号文档 6.2 节），每个类型归属一个消息分类 MSG_CATEGORY。
 */
@Getter
public enum MsgType implements CodeEnum {
    // 待办提醒类
    TODO_REMIND("待办提醒", MsgCategory.TODO),
    FEED_OVERDUE("饲喂逾期", MsgCategory.TODO),
    IMMUNE_MISSED("漏免提醒", MsgCategory.TODO),
    DISINFECT_OVERDUE("消毒逾期", MsgCategory.TODO),
    WITHDRAWAL_DELAYED("休药期延后", MsgCategory.TODO),
    // AI 诊断类
    AI_DIAGNOSIS("AI 诊断", MsgCategory.AI),
    // 审核结果类
    FARM_AUDIT_RESULT("注册审核结果", MsgCategory.AUDIT),
    FARM_CHANGE_RESULT("资料变更结果", MsgCategory.AUDIT),
    CHANGE_REQ_RESULT("修改申请结果", MsgCategory.AUDIT),
    RECOVERY_RESULT("账号找回结果", MsgCategory.AUDIT),
    // 成员与养殖场类
    FARM_INVITE("养殖场邀请", MsgCategory.MEMBER),
    INVITE_ACCEPTED("邀请已接受", MsgCategory.MEMBER),
    MEMBER_REMOVED("被移出养殖场", MsgCategory.MEMBER),
    MEMBER_ROLE_CHANGED("角色变更", MsgCategory.MEMBER),
    OWNER_TRANSFER("所有权转让", MsgCategory.MEMBER),
    TRANSFER_RESULT("转让结果", MsgCategory.MEMBER),
    FARM_DISABLED("养殖场已停用", MsgCategory.MEMBER),
    FARM_ENABLED("养殖场已启用", MsgCategory.MEMBER),
    FARM_DELETED("养殖场已删除", MsgCategory.MEMBER),
    FARM_FROZEN("养殖场已冻结", MsgCategory.MEMBER),
    FARM_UNFROZEN("养殖场已解冻", MsgCategory.MEMBER),
    // 批号停用类
    LOT_RECALLED("批号停用", MsgCategory.LOT),
    // 平台通知类
    NOTICE("平台通知", MsgCategory.NOTICE),
    // 其他类
    REPORT_READY("报表已生成", MsgCategory.OTHER),
    STAFF_NOTIFY("员工提醒", MsgCategory.OTHER);

    private final String text;
    private final MsgCategory category;

    MsgType(String text, MsgCategory category) {
        this.text = text;
        this.category = category;
    }
}

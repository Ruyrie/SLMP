package com.zhimu.server.common.error;

import lombok.Getter;

/**
 * 五位数字错误码（04 号文档 7.2 节）。HTTP 状态码取前三位；常量名与 App 的 ErrorCodes 一致。
 * 新增错误码先补进 04 号文档 7.2 节再加到这里。
 */
@Getter
public enum ErrorCode {

    OK(0, "ok"),

    PARAM_INVALID(40001, "参数校验失败"),
    FILE_INVALID(40002, "只支持 JPG 或 PNG 图片，单张不超过 5MB"),
    VIDEO_TOO_LARGE(40003, "视频不能超过 30 秒"),
    REASON_EMPTY(40004, "请填写原因"),

    TOKEN_EXPIRED(40101, "登录已过期，请重新登录"),
    PASSWORD_WRONG(40102, "手机号或密码不正确"),
    // App 按文案含“过期”清零倒计时，默认文案不能含“过期”；过期场景由模块01 另给文案
    SMS_CODE_WRONG(40103, "验证码不正确，请重新输入"),
    SESSION_INVALID(40104, "登录状态已失效，请重新登录"),
    RECOVERY_CODE_WRONG(40105, "恢复码不正确"),
    ADMIN_SESSION_TIMEOUT(40106, "登录已超时，请重新登录"),
    TEMP_PASSWORD_EXPIRED(40107, "临时密码已过期，请重新提交找回申请"),

    ACCOUNT_DISABLED(40301, "这个账号已被平台停用。如有疑问，请拨打客服电话"),
    NOT_FARM_MEMBER(40302, "你已不是该养殖场成员"),
    ROLE_FORBIDDEN(40303, "当前角色无权操作"),
    PEN_FORBIDDEN(40304, "你没有这个栏舍的查看权限"),
    FARM_STATUS_FORBIDDEN(40305, "养殖场当前状态不允许该操作"),
    FARM_FROZEN(40306, "养殖场已被平台冻结，暂时不能使用。原因见消息中心"),
    CHANGE_REQUEST_REQUIRED(40307, "需提交修改申请"),
    ADMIN_PERMISSION_DENIED(40308, "你没有该页面的操作权限，如需处理请联系超级管理员"),
    FORCED_STEP_REQUIRED(40309, "请先完成必要的步骤"),
    CANCEL_NOT_ALLOWED(40310, "暂不满足注销条件"),

    NOT_FOUND(40401, "记录不存在或无权查看"),

    DUPLICATE_SUBMIT(40901, "请求正在处理，请勿重复提交"),
    VERSION_CONFLICT(40902, "数据已被他人修改，请刷新后重试"),
    STATE_CHANGED(40903, "状态已变化，请刷新后重试"),
    // App 按文案含“已经注册”显示“去登录”
    PHONE_REGISTERED(40904, "该手机号已经注册，可直接登录"),
    NAME_DUPLICATE(40905, "名称重复"),
    PENDING_EXISTS(40906, "该对象已有待审核的申请"),

    RECORD_DELETED(41001, "该记录已被删除，无法查看"),

    STOCK_NOT_ENOUGH(42201, "库存不足"),
    WITHDRAWAL_PERIOD(42202, "该批次在休药期内，不能出栏"),
    BANNED_DRUG(42203, "该药品属于禁用兽药，不能使用"),
    LOT_UNAVAILABLE(42204, "该批号已过期或已停用，请换一个批号"),
    COUNT_EXCEEDED(42205, "数量超出在栏数量"),
    CAPACITY_CONFIRM(42206, "超出栏舍容量，确认后继续"),
    STATE_NOT_ALLOWED(42207, "当前状态不允许该操作"),
    PEN_DISABLED(42208, "该栏舍已停用，请先启用"),
    PEN_CATEGORY_CONFLICT(42209, "同一栏舍只能放同一品类的批次"),
    PEN_NOT_EMPTY(42210, "栏舍还有在栏批次，不能停用"),
    BATCH_ENDED(42211, "该批次已结束，不能再登记"),

    TOO_FREQUENT(42901, "操作太频繁，请稍后再试"),
    LOGIN_LOCKED(42902, "密码错误次数过多，请 30 分钟后再试或使用验证码登录"),
    RECOVERY_LOCKED(42903, "恢复码错误次数过多，请稍后再试或联系客服"),

    SERVER_ERROR(50001, "出了点问题，请稍后重试"),
    AI_UNAVAILABLE(50201, "AI 暂时无法回答，请稍后重试"),
    SERVICE_UNAVAILABLE(50301, "服务暂时不可用，请稍后重新加载"),
    AI_TIMEOUT(50401, "AI 回答超时，请重试");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** HTTP 状态码 = 错误码前三位；成功为 200 */
    public int getHttpStatus() {
        return httpStatusOf(code);
    }

    public static int httpStatusOf(int code) {
        return code == 0 ? 200 : code / 100;
    }

    public static ErrorCode of(int code) {
        for (ErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}

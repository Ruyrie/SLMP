package com.zhimu.slmp.base.error;

/**
 * 错误码常量：服务端错误码见 04 号文档 7.2 节；负数为客户端本地错误。
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // ===== 客户端本地错误 =====
    /** 网络不可用、连接失败、超时 */
    public static final int NETWORK = -1;
    /** 未知错误 */
    public static final int UNKNOWN = -2;
    /** 响应解析失败 */
    public static final int PARSE = -3;
    /** code 为 0 但需要的 data 为空 */
    public static final int EMPTY_DATA = -4;

    public static final int OK = 0;

    // ===== 400 =====
    public static final int PARAM_INVALID = 40001;
    public static final int FILE_INVALID = 40002;
    public static final int VIDEO_TOO_LARGE = 40003;
    public static final int REASON_EMPTY = 40004;

    // ===== 401 =====
    public static final int TOKEN_EXPIRED = 40101;
    public static final int PASSWORD_WRONG = 40102;
    public static final int SMS_CODE_WRONG = 40103;
    public static final int SESSION_INVALID = 40104;
    public static final int RECOVERY_CODE_WRONG = 40105;
    public static final int ADMIN_SESSION_TIMEOUT = 40106;
    public static final int TEMP_PASSWORD_EXPIRED = 40107;

    // ===== 403 =====
    public static final int ACCOUNT_DISABLED = 40301;
    public static final int NOT_FARM_MEMBER = 40302;
    public static final int ROLE_FORBIDDEN = 40303;
    public static final int PEN_FORBIDDEN = 40304;
    public static final int FARM_STATUS_FORBIDDEN = 40305;
    public static final int FARM_FROZEN = 40306;
    public static final int CHANGE_REQUEST_REQUIRED = 40307;
    public static final int ADMIN_PERMISSION_DENIED = 40308;
    public static final int FORCED_STEP_REQUIRED = 40309;
    public static final int CANCEL_NOT_ALLOWED = 40310;

    // ===== 404 / 409 / 410 =====
    public static final int NOT_FOUND = 40401;
    public static final int DUPLICATE_SUBMIT = 40901;
    public static final int VERSION_CONFLICT = 40902;
    public static final int STATE_CHANGED = 40903;
    public static final int PHONE_REGISTERED = 40904;
    public static final int NAME_DUPLICATE = 40905;
    public static final int PENDING_EXISTS = 40906;
    public static final int RECORD_DELETED = 41001;

    // ===== 422 =====
    public static final int STOCK_NOT_ENOUGH = 42201;
    public static final int WITHDRAWAL_PERIOD = 42202;
    public static final int BANNED_DRUG = 42203;
    public static final int LOT_UNAVAILABLE = 42204;
    public static final int COUNT_EXCEEDED = 42205;
    public static final int CAPACITY_CONFIRM = 42206;
    public static final int STATE_NOT_ALLOWED = 42207;
    public static final int PEN_DISABLED = 42208;
    public static final int PEN_CATEGORY_CONFLICT = 42209;
    public static final int PEN_NOT_EMPTY = 42210;
    public static final int BATCH_ENDED = 42211;

    // ===== 429 / 5xx =====
    public static final int TOO_FREQUENT = 42901;
    public static final int LOGIN_LOCKED = 42902;
    public static final int RECOVERY_LOCKED = 42903;
    public static final int SERVER_ERROR = 50001;
    public static final int AI_UNAVAILABLE = 50201;
    public static final int SERVICE_UNAVAILABLE = 50301;
    public static final int AI_TIMEOUT = 50401;

    /** 是否为“无权查看”类错误（页面切换为 U03 无权查看） */
    public static boolean isNoPermission(int code) {
        return code == NOT_FARM_MEMBER || code == ROLE_FORBIDDEN || code == PEN_FORBIDDEN;
    }

    /** 是否为“数据已变化”类错误（提示并刷新当前页面） */
    public static boolean isDataChanged(int code) {
        return code == VERSION_CONFLICT || code == STATE_CHANGED;
    }

    /** 是否由认证器与全局事件统一处理（ViewModel 不提示） */
    public static boolean isAuthHandled(int code) {
        return code == TOKEN_EXPIRED || code == SESSION_INVALID;
    }

    /** 由错误码得到 HTTP 状态码（前三位，0 为 200） */
    public static int httpStatusOf(int code) {
        if (code == OK) {
            return 200;
        }
        int c = Math.abs(code);
        while (c >= 1000) {
            c /= 10;
        }
        return c >= 100 ? c : 500;
    }
}

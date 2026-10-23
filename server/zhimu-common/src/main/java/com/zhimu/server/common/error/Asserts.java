package com.zhimu.server.common.error;

/**
 * 业务断言：条件不满足时抛出 BizException。
 */
public final class Asserts {

    private Asserts() {
    }

    public static void isTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BizException(errorCode);
        }
    }

    public static void isTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BizException(errorCode, message);
        }
    }

    public static <T> T notNull(T value, ErrorCode errorCode) {
        if (value == null) {
            throw new BizException(errorCode);
        }
        return value;
    }

    public static <T> T notNull(T value, ErrorCode errorCode, String message) {
        if (value == null) {
            throw new BizException(errorCode, message);
        }
        return value;
    }

    /** 记录不存在或不属于当前养殖场时统一返回 40401 */
    public static <T> T found(T value) {
        return notNull(value, ErrorCode.NOT_FOUND);
    }
}

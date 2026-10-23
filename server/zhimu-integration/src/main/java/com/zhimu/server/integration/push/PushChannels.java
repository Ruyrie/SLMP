package com.zhimu.server.integration.push;

/**
 * App 的三个通知渠道 ID（与 App NotificationChannels 一致）：待办提醒类与每日汇总 → TODO；
 * level 为 URGENT、IMPORTANT → URGENT；其余 → SYSTEM（模块03 服务端 2.8.4）。
 */
public final class PushChannels {

    /** 待办提醒 */
    public static final String TODO = "zm_todo";
    /** 紧急提醒 */
    public static final String URGENT = "zm_urgent";
    /** 审核与系统 */
    public static final String SYSTEM = "zm_system";

    private PushChannels() {
    }
}

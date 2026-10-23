package com.zhimu.server.integration.push;

import java.util.List;

/**
 * 推送结果。
 *
 * @param status    结果
 * @param msgIds    极光 msg_id（别名超过 1000 个时分批，每批一个）
 * @param errorCode 极光错误码（没有时为 null）
 * @param reason    失败或跳过的原因（写入推送记录用）
 */
public record PushResult(PushStatus status, List<String> msgIds, Integer errorCode, String reason) {

    public static PushResult sent(String msgId) {
        return new PushResult(PushStatus.SENT, msgId == null ? List.of() : List.of(msgId), null, null);
    }

    public static PushResult skipped(String reason) {
        return new PushResult(PushStatus.SKIPPED, List.of(), null, reason);
    }

    public boolean isSent() {
        return status == PushStatus.SENT;
    }
}

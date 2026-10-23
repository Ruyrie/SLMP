package com.zhimu.server.admin.system.request;

import java.util.List;

/** ADM-S42 标记消息已读：message_ids 与 all 二选一。 */
public record MessageReadRequest(List<Long> messageIds, Boolean all) {
}

package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ADM-S41 未读消息数；该接口标 @NoSessionRenew，不延长会话。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountVO {
    private Integer unreadCount;
}

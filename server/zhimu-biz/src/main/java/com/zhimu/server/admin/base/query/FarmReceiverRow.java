package com.zhimu.server.admin.base.query;

import lombok.Getter;
import lombok.Setter;

/** 批号停用消息接收人（养殖场所有者 / 场长）。 */
@Getter
@Setter
public class FarmReceiverRow {

    private Long farmId;
    private Long userId;
}

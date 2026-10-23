package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

/** 删除消息结果（API-T17）。 */
@Getter
@Setter
public class MessageDeleteResult {

    private String deletedAt;
    private String undoDeadline;
}

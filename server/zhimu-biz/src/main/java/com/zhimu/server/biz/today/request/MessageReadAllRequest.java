package com.zhimu.server.biz.today.request;

import lombok.Getter;
import lombok.Setter;

/** 全部标为已读（API-T14），字段与当前筛选一致，可空。 */
@Getter
@Setter
public class MessageReadAllRequest {

    private String category;
    private Long farmId;
}

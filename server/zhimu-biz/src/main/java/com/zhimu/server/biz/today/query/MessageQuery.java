package com.zhimu.server.biz.today.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-T12 消息列表。 */
@Getter
@Setter
public class MessageQuery extends PageQuery {

    private String category;
    private Long farmId;
    private Boolean unreadOnly;
}

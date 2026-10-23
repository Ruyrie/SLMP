package com.zhimu.server.biz.today.query;

import lombok.Getter;
import lombok.Setter;

/** API-T13 未读数。 */
@Getter
@Setter
public class UnreadCountQuery {

    private String category;
    private Long farmId;
}

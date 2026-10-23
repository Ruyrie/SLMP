package com.zhimu.server.biz.farm.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** F19 邀请列表：keyword 为手机号片段 */
@Getter
@Setter
public class InviteQuery extends PageQuery {
    private String keyword;
}

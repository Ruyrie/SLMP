package com.zhimu.server.biz.ai.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * API-K16 专题列表：sort = DEFAULT / RECENT_LEARNED。
 */
@Getter
@Setter
public class TopicQuery extends PageQuery {

    private String sort;
}

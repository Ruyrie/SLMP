package com.zhimu.server.biz.ai.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * API-K01 历史会话查询：keyword 匹配会话标题和该会话任一提问文字。
 */
@Getter
@Setter
public class AiSessionQuery extends PageQuery {

    private String keyword;
}

package com.zhimu.server.admin.system.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** ADM-S40 管理端消息列表：read = ALL（默认）/ UNREAD，只看本人消息，时间倒序。 */
@Getter
@Setter
public class AdminMessageQuery extends PageQuery {

    private String read = "ALL";
}

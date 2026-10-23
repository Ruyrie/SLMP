package com.zhimu.server.biz.farm.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** F16 成员列表：role = ALL / MANAGER / STAFF，keyword 匹配姓名或手机号片段 */
@Getter
@Setter
public class MemberQuery extends PageQuery {
    private String role;
    private String keyword;

    public String roleOrDefault() {
        return role == null || role.isBlank() ? "ALL" : role.trim().toUpperCase();
    }
}

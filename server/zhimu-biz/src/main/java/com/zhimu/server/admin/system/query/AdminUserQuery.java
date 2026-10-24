package com.zhimu.server.admin.system.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** ADM-S10/S14 用户账号列表：keyword 为姓名或手机号；注册时间范围 ≤1 年。 */
@Getter
@Setter
public class AdminUserQuery extends PageQuery {

    @Pattern(regexp = "ALL|NORMAL|DISABLED", message = "账号状态不正确")
    private String status;

    @Size(max = 20, message = "搜索关键词不能超过 20 个字")
    private String keyword;

    @Pattern(regexp = "OWNER|MANAGER|STAFF|NONE", message = "角色筛选不正确")
    private String role;

    private LocalDate registeredFrom;
    private LocalDate registeredTo;

    public String statusOrDefault() {
        return status == null || status.isBlank() ? "ALL" : status;
    }
}

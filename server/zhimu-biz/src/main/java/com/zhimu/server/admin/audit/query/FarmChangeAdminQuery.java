package com.zhimu.server.admin.audit.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-R06 资料变更列表：status 默认 PENDING；change_item 为 NAME / ADDRESS / QUALIFICATION。 */
@Getter
@Setter
public class FarmChangeAdminQuery extends PageQuery {

    @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "审核状态不正确")
    private String status;

    @Size(max = 30, message = "搜索关键词不能超过 30 个字")
    private String keyword;

    @Pattern(regexp = "NAME|ADDRESS|QUALIFICATION", message = "变更项不正确")
    private String changeItem;

    public String statusOrDefault() {
        return status == null || status.isBlank() ? "PENDING" : status;
    }
}

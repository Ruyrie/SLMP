package com.zhimu.server.admin.audit.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-R10 修改申请队列：status 默认 PENDING；impact 为六个影响编码之一。 */
@Getter
@Setter
public class ChangeRequestAdminQuery extends PageQuery {

    @Pattern(regexp = "PENDING|APPROVED|REJECTED|CANCELED", message = "申请状态不正确")
    private String status;

    @Size(max = 30, message = "搜索关键词不能超过 30 个字")
    private String keyword;

    @Pattern(regexp = "PEN_STOCK|BREEDING|FEEDING|INSPECTION|HEALTH|INVENTORY|SALE", message = "来源模块不正确")
    private String sourceModule;

    @Pattern(regexp = "STOCK|COST|INVENTORY|SELLABLE_DATE|INCOME|STATS", message = "影响范围不正确")
    private String impact;

    public String statusOrDefault() {
        return status == null || status.isBlank() ? "PENDING" : status;
    }
}

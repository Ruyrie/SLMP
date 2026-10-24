package com.zhimu.server.admin.audit.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-R02 注册审核列表：status 默认 PENDING；keyword 匹配养殖场名、所有者姓名或完整 11 位手机号。 */
@Getter
@Setter
public class FarmAuditAdminQuery extends PageQuery {

    @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "审核状态不正确")
    private String status;

    @Size(max = 30, message = "搜索关键词不能超过 30 个字")
    private String keyword;

    private Long categoryId;

    /** 6 位行政区划码；按前 2 位匹配省份 */
    private String provinceCode;

    @Pattern(regexp = "FIRST|RESUBMIT", message = "提交类型不正确")
    private String submitType;

    public String statusOrDefault() {
        return status == null || status.isBlank() ? "PENDING" : status;
    }
}

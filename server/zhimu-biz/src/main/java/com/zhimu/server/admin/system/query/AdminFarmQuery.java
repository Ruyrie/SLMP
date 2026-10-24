package com.zhimu.server.admin.system.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-S15/S19 养殖场列表：状态、名称/所有者、品类、省份。 */
@Getter
@Setter
public class AdminFarmQuery extends PageQuery {

    @Pattern(regexp = "ALL|NORMAL|DISABLED|FROZEN|PENDING|REJECTED", message = "养殖场状态不正确")
    private String status;

    @Size(max = 20, message = "搜索关键词不能超过 20 个字")
    private String keyword;

    private Long categoryId;

    /** 6 位行政区划码；按前 2 位匹配省份 */
    private String provinceCode;

    public String statusOrDefault() {
        return status == null || status.isBlank() ? "ALL" : status;
    }
}

package com.zhimu.server.admin.audit.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-R14 找回核实队列：tab 默认 PENDING（只含恢复码正确的申请），HANDLED 含已通过/已驳回/已关闭。 */
@Getter
@Setter
public class RecoveryAdminQuery extends PageQuery {

    @Pattern(regexp = "PENDING|HANDLED", message = "标签不正确")
    private String tab;

    @Size(max = 30, message = "搜索关键词不能超过 30 个字")
    private String keyword;

    public String tabOrDefault() {
        return tab == null || tab.isBlank() ? "PENDING" : tab;
    }
}

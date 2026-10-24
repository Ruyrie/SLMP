package com.zhimu.server.admin.base.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-D42 / D43 添加、编辑巡检项目与结果、启停用。 */
@Data
public class InspectItemSaveRequest {

    @NotBlank(message = "请填写项目名称")
    @Size(max = 6, message = "项目名称最多 6 个字")
    private String itemName;

    @Size(max = 50, message = "检查说明最多 50 个字")
    private String checkNote;

    /** 默认启用；停用需填 disable_reason */
    private Boolean enabled;

    @Size(max = 100, message = "停用原因最多 100 个字")
    private String disableReason;

    @NotEmpty(message = "请至少添加 2 个结果")
    @Size(min = 2, max = 6, message = "结果数量必须为 2–6 个")
    @Valid
    private List<Option> options = new ArrayList<>();

    @NotNull(message = "缺少版本号")
    private Integer version;

    @Data
    public static class Option {
        /** 为空表示新增结果 */
        private Long optionId;

        @NotBlank(message = "请填写结果名称")
        @Size(max = 6, message = "结果名称最多 6 个字")
        private String optionName;

        @NotNull(message = "请选择结果是否异常")
        private Boolean isAbnormal;
    }
}

package com.zhimu.server.admin.audit.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-R16/R17 找回通过、驳回：核实方式必选，处理说明 1–200 字，version 必填。 */
@Data
public class RecoveryHandleRequest {

    @NotBlank(message = "请选择核实方式")
    @Pattern(regexp = "CALLBACK_NEW_PHONE|CONTACT_FARM_OWNER|OTHER", message = "核实方式不正确")
    private String verifyMethod;

    @Size(max = 200, message = "处理说明不能超过 200 字")
    private String handleNote;

    @NotNull(message = "version 不能为空")
    private Integer version;
}

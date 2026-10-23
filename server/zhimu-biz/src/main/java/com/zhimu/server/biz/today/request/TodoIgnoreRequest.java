package com.zhimu.server.biz.today.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 忽略待办（API-T09）：原因去首尾空格后 1–100 字，长度在 Service 中按 Unicode 码点校验。 */
@Getter
@Setter
public class TodoIgnoreRequest {

    private String reason;

    @NotNull(message = "缺少版本号")
    @Min(value = 0, message = "版本号不能小于 0")
    private Integer version;
}

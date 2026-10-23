package com.zhimu.server.biz.today.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 手动标记完成（API-T08）：平台不允许空体，缺少 version 返回 40001。 */
@Getter
@Setter
public class TodoCompleteRequest {

    @NotNull(message = "缺少版本号")
    @Min(value = 0, message = "版本号不能小于 0")
    private Integer version;
}

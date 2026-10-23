package com.zhimu.server.biz.today.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 登记推送设备（API-T20）。 */
@Getter
@Setter
public class PushDeviceRequest {

    @NotBlank(message = "缺少 registration_id")
    @Size(max = 64, message = "registration_id 不能超过 64 字符")
    private String registrationId;

    @NotNull(message = "缺少 platform")
    private String platform;

    @Size(max = 40, message = "设备品牌不能超过 40 字符")
    private String deviceBrand;

    @Size(max = 40, message = "设备型号不能超过 40 字符")
    private String deviceModel;

    @Size(max = 40, message = "系统版本不能超过 40 字符")
    private String osVersion;

    @Size(max = 40, message = "App 版本不能超过 40 字符")
    private String appVersion;

    @NotNull(message = "缺少 notify_permission")
    private Boolean notifyPermission;
}

package com.zhimu.server.biz.today.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 推送设备与极光别名绑定（t_push_device）。 */
@Getter
@Setter
@TableName("t_push_device")
public class PushDevice extends BaseEntity {

    private Long userId;
    private String platform;
    private String registrationId;
    private String jpushAlias;
    private Boolean aliasBound;
    private String deviceBrand;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private Boolean notifyPermission;
    /** DEVICE_STATUS：ACTIVE / UNBOUND */
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastActiveAt;
}

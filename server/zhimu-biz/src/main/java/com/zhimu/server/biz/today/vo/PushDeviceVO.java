package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

/** 推送设备登记结果（API-T20）。 */
@Getter
@Setter
public class PushDeviceVO {

    private String registrationId;
    private String jpushAlias;
    private String status;
    private Boolean aliasBound;
    private Boolean notifyPermission;
}

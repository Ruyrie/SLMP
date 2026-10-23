package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 基本信息（FarmDetailVO.basic、F10 响应，含新 version） */
@Data
public class FarmBasicVO {
    private String contactName;
    private String contactPhone;
    private String contactPhoneMasked;
    private String intro;
    private Integer scale;
    private Integer version;
}

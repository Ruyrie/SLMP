package com.zhimu.server.biz.farm.request;

import lombok.Data;

/** 修改基本信息（API-F10）：App 每次只传当前面板的一项（null 不输出），intro 清空传空字符串 */
@Data
public class UpdateFarmBasicRequest {
    private String contactName;
    private String contactPhone;
    private String intro;
    private Integer scale;
    private Integer version;
}

package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

import java.util.List;

/** API-I04 / I05 巡检截止时间与提醒规则。 */
@Data
public class InspectionSettingVO {

    private String farmName;
    private String deadlineTime;
    private String remindTime;
    private String defaultDeadline;
    private List<String> quickOptions;
    private String minTime;
    private String maxTime;
    private List<String> ownerManagerNames;
    private String updatedByName;
    private String updatedAt;
}

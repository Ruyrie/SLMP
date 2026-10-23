package com.zhimu.server.biz.inspect.request;

import lombok.Data;

/** API-I05 修改巡检截止时间：HH:mm 整分钟，06:00–23:00。 */
@Data
public class InspectionSettingRequest {

    private String deadlineTime;
}

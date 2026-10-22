package com.zhimu.slmp.inspect.model.request;

/**
 * API-I05 修改巡检截止时间：deadline_time 为 HH:mm 整分钟，在 min_time–max_time 内；不带幂等键。
 */
public class InspectionSettingRequest {

    public String deadlineTime;

    public InspectionSettingRequest(String deadlineTime) {
        this.deadlineTime = deadlineTime;
    }
}

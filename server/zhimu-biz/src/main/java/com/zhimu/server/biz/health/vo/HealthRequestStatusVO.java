package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-D41 按请求编号查询登记结果。 */
@Data
public class HealthRequestStatusVO {

    /** DONE / NOT_FOUND */
    private String status;
    private String bizType;
    private String groupNo;
    private List<Long> bizIds = new ArrayList<>();
}

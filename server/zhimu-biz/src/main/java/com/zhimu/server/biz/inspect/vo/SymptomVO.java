package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** API-I15 症状（按批次品类）。 */
@Data
public class SymptomVO {

    private long symptomId;
    private String name;
    private int sortNo;
}

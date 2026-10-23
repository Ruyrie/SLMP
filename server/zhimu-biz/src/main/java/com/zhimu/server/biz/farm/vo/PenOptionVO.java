package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 可分配栏舍（F18） */
@Data
public class PenOptionVO {
    private Long penId;
    private String penCode;
    private String name;
    private Boolean selected;
    private Integer otherStaffCount;
}

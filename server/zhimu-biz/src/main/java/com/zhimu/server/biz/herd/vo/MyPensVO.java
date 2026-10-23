package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.util.List;

/** API-L02 我的栏舍（L02，员工）。 */
@Data
public class MyPensVO {

    private String farmName;
    private Integer penCount;
    private List<MyPenItemVO> pens;
}

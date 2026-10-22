package com.zhimu.slmp.herd.model.vo;

import java.util.Collections;
import java.util.List;

/**
 * API-L02 我的栏舍（L02，员工）。
 */
public class MyPensVO {

    private String farmName;
    private int penCount;
    private List<MyPenItemVO> pens;

    public String getFarmName() {
        return farmName;
    }

    public int getPenCount() {
        return penCount;
    }

    public List<MyPenItemVO> getPens() {
        return pens == null ? Collections.emptyList() : pens;
    }
}

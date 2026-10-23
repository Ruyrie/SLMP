package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

/** 超容量确认（42206 的 data）。 */
@Getter
@Setter
public class CapacityConfirmVO {

    private boolean needConfirm;
    private long penId;
    private String penName;
    private String unit;
    private int capacity;
    private int currentCount;
    private int afterCount;
    private int overCount;
}

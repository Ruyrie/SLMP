package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 转让页页头（TransferPageVO.farm） */
@Data
public class TransferFarmVO {
    private Long farmId;
    private String name;
    private String mainCategoryName;
    private String countUnit;
    private Integer currentStock;
}

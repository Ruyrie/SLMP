package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** API-L17 批次各栏舍在栏数（只含 stock_count 大于 0 的栏舍；员工只返回负责栏舍）。 */
@Data
public class PenStockVO {

    private Long penId;
    private String penName;
    private String firstInDate;
    private Integer stockCount;
    private Integer capacity;
    private String unitText;
}

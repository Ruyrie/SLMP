package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** 巡检模板项目与选项（一次查询平铺，按 sort_no 组装）。 */
@Data
public class InspectTemplateItemRow {
    private Long itemId;
    private String itemName;
    private Integer itemSort;
    private Long optionId;
    private String optionName;
    private Boolean optionAbnormal;
    private Integer optionSort;
}

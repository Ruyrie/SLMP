package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** API-L07（usage = OPTIONS）栏舍下拉项：只含启用栏舍；传 category_id 时其他品类在栏的栏舍 disabled = true。 */
@Data
public class PenOptionVO {

    private Long penId;
    private String penCode;
    private String penName;
    private String penType;
    private Integer stockCount;
    private Integer capacity;
    private Boolean disabled;
    private String hint;
    private String unitText;
}

package com.zhimu.server.biz.herd.request;

import lombok.Data;

/** API-L11 新增栏舍（L05）。pen_code 提交前转大写。 */
@Data
public class PenCreateRequest {
    private String penCode;
    private String penName;
    private String penType;
    private Long categoryId;
    private Integer capacity;
}

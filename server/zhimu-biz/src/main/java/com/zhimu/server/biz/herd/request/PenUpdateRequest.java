package com.zhimu.server.biz.herd.request;

import lombok.Data;

/** API-L12 编辑栏舍（L05 编辑模式）：去掉 pen_code，加 version。 */
@Data
public class PenUpdateRequest {
    private String penName;
    private String penType;
    private Long categoryId;
    private Integer capacity;
    private Integer version;
}

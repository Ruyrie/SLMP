package com.zhimu.server.biz.stock.vo;

import lombok.Data;

/** API-M42 提交结果（App {@code RequestStatusVO}）：DONE / PROCESSING / NOT_FOUND。 */
@Data
public class RequestStatusVO {

    private String status;
    private String bizType;
    private Long bizId;
}

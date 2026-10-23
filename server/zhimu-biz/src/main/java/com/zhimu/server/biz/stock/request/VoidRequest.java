package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** API-M11 / M23 作废（App {@code VoidRequest}）：只带 version。 */
@Data
public class VoidRequest {

    @NotNull(message = "数据已被他人修改，请刷新后重试")
    private Integer version;
}

package com.zhimu.server.biz.feeding.request;

import lombok.Data;

/** API-G23 修改 / 恢复称重周期：per_month 与 reset 必须且只能传一个。 */
@Data
public class WeighCycleRequest {
    private Integer perMonth;
    private Boolean reset;
    private Integer version;
}

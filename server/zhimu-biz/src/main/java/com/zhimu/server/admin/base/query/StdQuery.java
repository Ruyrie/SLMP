package com.zhimu.server.admin.base.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** API-U12 品种标准查询。 */
@Data
public class StdQuery {

    @Min(value = 1, message = "日龄必须在 1–999 之间")
    @Max(value = 999, message = "日龄必须在 1–999 之间")
    private Integer dayAge;
}

package com.zhimu.server.admin.system.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** ADM-S09 趋势指标：ACTIVE_USER / NEW_USER / PRODUCTION_RECORD。 */
@Getter
@Setter
public class TrendQuery {

    @NotBlank(message = "请选择趋势指标")
    @Pattern(regexp = "ACTIVE_USER|NEW_USER|PRODUCTION_RECORD", message = "趋势指标不正确")
    private String metric;
}

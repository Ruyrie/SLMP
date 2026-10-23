package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 数量：记账单位数量 value + unit / unit_text；有包装换算时带 pack_unit、pack_size、pack_count、rest_value。
 * 不带拼好的显示文字，由客户端拼接（与 App lib_common 的 QtyVO 一致）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QtyVO {
    private BigDecimal value;
    private String unit;
    private String unitText;
    private String packUnit;
    private BigDecimal packSize;
    private Integer packCount;
    private BigDecimal restValue;
}
package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 下拉与选择面板的选项；hint 写“库存不足”“已停用”等 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionVO {
    private String value;
    private String label;
    private Boolean disabled;
    private String hint;

    public static OptionVO of(String value, String label) {
        return OptionVO.builder().value(value).label(label).disabled(false).build();
    }
}
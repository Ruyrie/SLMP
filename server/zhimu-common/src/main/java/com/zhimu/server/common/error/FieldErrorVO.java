package com.zhimu.server.common.error;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参数错误（40001）时 data.field_errors 中的一项。field 为请求 JSON 的 snake_case 路径，例如 items[0].weight。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorVO {
    private String field;
    private String message;
}

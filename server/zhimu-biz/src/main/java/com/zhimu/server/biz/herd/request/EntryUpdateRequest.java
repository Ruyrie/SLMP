package com.zhimu.server.biz.herd.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** API-L20 修改入栏记录（L08 编辑模式）：BatchCreateRequest 的字段 + 批次 version。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EntryUpdateRequest extends BatchCreateRequest {
    private Integer version;
}

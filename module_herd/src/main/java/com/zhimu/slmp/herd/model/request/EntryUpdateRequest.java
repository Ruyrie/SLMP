package com.zhimu.slmp.herd.model.request;

/**
 * API-L20 修改入栏记录（L08 编辑模式）：BatchCreateRequest 的字段 + 批次 version（API-L19 entry.batch_version）。
 */
public class EntryUpdateRequest extends BatchCreateRequest {

    public int version;
}

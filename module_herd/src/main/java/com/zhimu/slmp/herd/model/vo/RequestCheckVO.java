package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

/**
 * API-L26 按请求编号（Idempotency-Key）查询记录是否已保存；result 按 event_type 再解析为
 * BatchEntryResultVO、TransferResultVO 或 DeathResultVO。
 */
public class RequestCheckVO {

    private boolean exists;
    private String eventType;
    private Long eventId;
    private Long batchId;
    private JsonObject result;

    public boolean isExists() { return exists; }

    /** ENTRY / TRANSFER / DEATH */
    @Nullable
    public String getEventType() { return eventType; }

    @Nullable
    public Long getEventId() { return eventId; }

    @Nullable
    public Long getBatchId() { return batchId; }

    @Nullable
    public JsonObject getResult() { return result; }
}

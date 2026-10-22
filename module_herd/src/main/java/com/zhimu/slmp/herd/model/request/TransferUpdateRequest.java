package com.zhimu.slmp.herd.model.request;

/**
 * API-L22 修改转栏记录（L10 编辑模式）：去掉 batch_id、seen_stock_count，加 version（API-L19）。
 */
public class TransferUpdateRequest {

    public long fromPenId;
    public long toPenId;
    public int quantity;
    public String eventDate;
    public String remark;
    public Boolean capacityConfirmed;
    public int version;
}

package com.zhimu.slmp.herd.model.request;

/**
 * API-L21 转栏登记（L10）。seen_stock_count 为页面显示的转出栏舍在栏数，用于服务端选择 42205 文案。
 */
public class TransferCreateRequest {

    public long batchId;
    public long fromPenId;
    public long toPenId;
    public int quantity;
    public String eventDate;
    public String remark;
    public Boolean capacityConfirmed;
    public Integer seenStockCount;
}

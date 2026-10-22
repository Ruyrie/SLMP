package com.zhimu.slmp.herd.model.request;

import java.util.List;

/**
 * API-L23 死淘登记（L11）。pen_id 多栏舍时必填；executor_user_id 员工不传；file_ids 按网格顺序。
 */
public class DeathCreateRequest {

    public long batchId;
    public Long penId;
    public int quantity;
    public String reasonCode;
    public String eventDate;
    public Long executorUserId;
    public String remark;
    public List<Long> fileIds;
    public Integer seenStockCount;
}

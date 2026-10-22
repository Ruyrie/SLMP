package com.zhimu.slmp.herd.model.request;

import java.util.List;

/**
 * API-L24 修改死淘记录（L11 编辑模式）：去掉 batch_id、seen_stock_count，加 version（API-L19）。
 */
public class DeathUpdateRequest {

    public Long penId;
    public int quantity;
    public String reasonCode;
    public String eventDate;
    public Long executorUserId;
    public String remark;
    public List<Long> fileIds;
    public int version;
}

package com.zhimu.slmp.stock.model.vo;

import java.util.List;

/** API-L06 在栏批次（status = ACTIVE，模块04 接口；M11 关联批次，待定事项 2）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockBatchOptionVO {
    public long batchId;
    public String batchNo;
    public int currentCount;
    public String unitText;
    public List<PenItem> pens;

    public static class PenItem {
        public long penId;
        public String penName;
        public int stockCount;
    }
}

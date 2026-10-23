package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import java.util.List;

/** API-M33 领用成本分摊预览（关联批次时一行“全部计入”）；字段直接公开，由 Gson 按下划线命名读写。 */
public class CostAllocationVO {
    public BigDecimal totalAmount;
    public BigDecimal unitPrice;
    public List<Row> rows;

    public static class Row {
        public long batchId;
        public String batchNo;
        public Integer headCount;
        public BigDecimal ratio;
        public BigDecimal amount;
    }
}

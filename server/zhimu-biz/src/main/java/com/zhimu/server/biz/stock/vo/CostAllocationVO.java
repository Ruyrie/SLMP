package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** API-M33 领用成本分摊预览（App {@code CostAllocationVO}）：关联批次时一行“全部计入”。 */
@Data
public class CostAllocationVO {

    @ManagerView
    private BigDecimal totalAmount;
    @ManagerView
    private BigDecimal unitPrice;
    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row {
        private long batchId;
        private String batchNo;
        private Integer headCount;
        private BigDecimal ratio;
        @ManagerView
        private BigDecimal amount;
    }
}

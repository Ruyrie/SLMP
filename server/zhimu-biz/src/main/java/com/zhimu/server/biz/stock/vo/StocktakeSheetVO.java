package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** API-M14 盘点表（App {@code StocktakeSheetVO}）：全部有结存或流水的饲料，带 stock_version。 */
@Data
public class StocktakeSheetVO {

    private String stocktakeDate;
    private UserBriefVO operator;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private long feedId;
        private String feedName;
        private String unitText;
        private BigDecimal bookQty;
        private Integer stockVersion;
        private boolean lowStock;
        @ManagerView
        private BigDecimal avgPrice;
    }
}

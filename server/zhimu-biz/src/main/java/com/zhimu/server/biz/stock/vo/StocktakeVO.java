package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 盘点单（App {@code StocktakeVO}）：API-M15 返回、M16 列表（不含 items）、M17 详情。 */
@Data
public class StocktakeVO {

    private long stocktakeId;
    private String stocktakeNo;
    private String stocktakeDate;
    private UserBriefVO operator;
    private int itemCount;
    private int gainCount;
    private int lossCount;
    @ManagerView
    private BigDecimal gainAmount;
    @ManagerView
    private BigDecimal lossAmount;
    private List<Item> items;
    private StockActionsVO actions;
    private String createdAt;
    private Integer version;

    @Data
    public static class Item {
        private long itemId;
        private long feedId;
        private String feedName;
        private String unitText;
        private BigDecimal bookQty;
        private BigDecimal actualQty;
        private BigDecimal diffQty;
        private String result;
        private String resultText;
        @ManagerView
        private BigDecimal avgPrice;
        @ManagerView
        private BigDecimal diffAmount;
    }

    public static List<Item> newItems() {
        return new ArrayList<>();
    }
}

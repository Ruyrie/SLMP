package com.zhimu.server.biz.finance.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.FarmBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 经营概览与月度汇总（API-E01、E02）响应结构。 */
public final class OverviewVos {

    private OverviewVos() {
    }

    @Data
    public static class BusinessOverviewVO {
        private String today;
        private Integer pendingSupplementCount;
        private MonthBalance monthBalance;
        private ReceivableSummary receivable;
        private PriceCard priceCard;
        private MonthSales monthSales;
        private MonthRecords monthRecords;
        private Todos todos;
    }

    @Data
    public static class MonthBalance {
        @ManagerView
        private BigDecimal balanceAmount;
        @ManagerView
        private BigDecimal income;
        @ManagerView
        private BigDecimal expenseAmount;
        @ManagerView
        private BigDecimal prevBalanceAmount;
        private BigDecimal balanceMomRate;
        private Boolean comparable;
    }

    @Data
    public static class PriceCard {
        private String categoryName;
        private String provinceName;
        @ManagerView
        private BigDecimal price;
        private String priceUnit;
        private String priceUnitText;
        private BigDecimal changeRate;
        private String direction;
        private String priceDate;
        private String compareDate;
        @ManagerView
        private BigDecimal comparePrice;
    }

    @Data
    public static class MonthSales {
        private Integer orderCount;
        @ManagerView
        private BigDecimal saleAmount;
    }

    @Data
    public static class MonthRecords {
        private Integer total;
        private Integer autoCount;
        private Integer manualCount;
    }

    @Data
    public static class Todos {
        private Integer total;
        private List<TodoItem> items = new ArrayList<>();
    }

    @Data
    public static class TodoItem {
        private Long todoId;
        private String todoType;
        private String title;
        private String subtitle;
        private String dueDate;
        private Boolean overdue;
        private String tagText;
        private String bizType;
        private Long bizId;
    }

    @Data
    public static class FinanceSummaryVO {
        private String periodStart;
        private String periodEnd;
        private Boolean isCurrentMonth;
        @ManagerView
        private BigDecimal income;
        @ManagerView
        private BigDecimal expenseAmount;
        @ManagerView
        private BigDecimal balanceAmount;
        @ManagerView
        private BigDecimal prevIncome;
        @ManagerView
        private BigDecimal prevExpenseAmount;
        @ManagerView
        private BigDecimal prevBalanceAmount;
        private Boolean incomeComparable;
        private Boolean expenseComparable;
        private Boolean balanceComparable;
        private BigDecimal incomeMomRate;
        private BigDecimal expenseMomRate;
        private BigDecimal balanceMomRate;
        private Integer recordCount;
        private Integer autoCount;
        private Integer manualCount;
        private Integer manageableFarmCount;
        private List<CategoryShareVO> expenseShares;
        private List<CategoryShareVO> incomeShares;
        private List<FarmFinanceVO> farms;
        private List<String> staffFarmNames;
    }

    @Data
    public static class CategoryShareVO {
        private Long categoryId;
        private String name;
        private String icon;
        @ManagerView
        private BigDecimal amount;
        private BigDecimal shareRate;
        private BigDecimal momRate;
        private Boolean comparable;
        private String trend;
    }

    @Data
    public static class FarmFinanceVO {
        private Long farmId;
        private String farmName;
        private String role;
        private String roleText;
        private String mainCategoryName;
        @ManagerView
        private BigDecimal income;
        @ManagerView
        private BigDecimal expenseAmount;
        @ManagerView
        private BigDecimal balanceAmount;
        private BigDecimal incomeMomRate;
        private BigDecimal expenseMomRate;
        private BigDecimal balanceMomRate;
        private Boolean incomeComparable;
        private Boolean expenseComparable;
        private Boolean balanceComparable;
        private BigDecimal incomeShareRate;
    }

    /** E01 待收款卡与 E18 合计卡共用的结构 */
    @Data
    public static class ReceivableSummary {
        @ManagerView
        private BigDecimal totalUnpaidAmount;
        private Integer orderCount;
        private Integer overdueCount;
        @ManagerView
        private BigDecimal overdueUnpaidAmount;
        private String nearestDueDate;
    }

    /** E02 合计视角的养殖场（复用 FarmBriefVO，含 farm_id/name/status/role） */
    public static List<FarmBriefVO> emptyFarms() {
        return new ArrayList<>();
    }
}

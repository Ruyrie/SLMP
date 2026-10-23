package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批号停用通知各养殖场处理进度（t_drug_lot_recall_farm）。
 * 带 farm_id，标 @FarmScoped；管理端无 FarmContext 时不会被自动过滤。
 */
@Getter
@Setter
@FarmScoped
@TableName("t_drug_lot_recall_farm")
public class DrugLotRecallFarm extends BaseEntity {

    private Long recallId;
    private Long farmId;
    private BigDecimal qtyAtRecall;
    private String qtyUnit;
    /** RECALL_SCRAP_STATUS：UNSCRAPPED / PARTIAL / SCRAPPED */
    private String scrapStatus;
    private BigDecimal scrappedQty;
    private LocalDate scrappedAt;
    private Long messageId;
}

package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;

/** 批号选项（App {@code DrugLotOptionVO}；与模块08 共用的 record 在 biz.stock.api.DrugLotOption）。 */
@Data
public class DrugLotOptionVO {

    private long lotId;
    private String lotNo;
    private String expireDate;
    private Integer daysToExpire;
    private String lotStatus;
    private String lotStatusText;
    private QtyVO stock;
    private boolean priorityOut;
    private boolean insufficient;
    private boolean canGoScrap;
    @ManagerView
    private BigDecimal avgPrice;
    private Integer stockVersion;
}

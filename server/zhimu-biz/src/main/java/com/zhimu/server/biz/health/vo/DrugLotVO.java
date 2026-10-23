package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 批号选项单项（字段与模块09 DrugLotOption / App DrugLotOptionVO 一致，均价按角色裁剪）。 */
@Data
public class DrugLotVO {

    private Long lotId;
    private String lotNo;
    private LocalDate expireDate;
    private String lotStatus;
    private String lotStatusText;
    private Integer daysToExpire;
    private QtyVO stock;
    @ManagerView
    private BigDecimal avgPrice;
    private Boolean priorityOut;
    private Boolean insufficient;
    private Boolean canGoScrap;
}

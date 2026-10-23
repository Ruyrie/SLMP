package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

/** 批号列表项（App {@code DrugLotItemVO}，API-M25）：days_to_expire 过期时为负。 */
@Data
public class DrugLotItemVO {

    private long lotId;
    private long drugId;
    private String drugName;
    private String category;
    private String categoryText;
    private String lotNo;
    private String expireDate;
    private QtyVO stock;
    private String lotStatus;
    private String lotStatusText;
    private Integer daysToExpire;
    private boolean priorityOut;
    private String statusNote;
}

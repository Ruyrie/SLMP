package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

/** 巡检项结果（t_inspection_item_result）：修改时整组替换。 */
@Getter
@Setter
@FarmScoped
@TableName("t_inspection_item_result")
public class InspectionItemResult extends AuditEntity {

    private Long farmId;
    private Long inspectionId;
    private Long itemId;
    private String itemName;
    private Long optionId;
    private String optionName;
    private Boolean optionAbnormal;
    private Integer sortNo;
}

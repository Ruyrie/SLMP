package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 逐头体重明细（t_weigh_record_item）：修改时整组替换。 */
@Getter
@Setter
@FarmScoped
@TableName("t_weigh_record_item")
public class WeighRecordItem extends BaseEntity {

    private Long farmId;
    private Long weighRecordId;
    private Integer seqNo;
    private BigDecimal weight;
}

package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 标准体重日龄点（t_std_weight_point）：下限/上限/日增重不落库，由 StdCalculator 计算。 */
@Getter
@Setter
@TableName("t_std_weight_point")
public class StdWeightPoint extends AdminDetailEntity {

    private Long versionId;
    private Integer dayAge;
    private BigDecimal stdWeight;
}

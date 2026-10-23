package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** AI 出栏建议风险条目（t_sale_advice_risk，只追加，无 updated_at）。 */
@Getter
@Setter
@TableName("t_sale_advice_risk")
public class SaleAdviceRisk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adviceId;
    private Integer sortNo;
    private String content;
    private LocalDateTime createdAt;
}

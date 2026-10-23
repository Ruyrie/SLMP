package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报表期次（t_biz_report）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_biz_report")
public class BizReport extends BaseEntity {

    private Long farmId;
    private String reportType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime generatedAt;
    private Boolean notified;
}

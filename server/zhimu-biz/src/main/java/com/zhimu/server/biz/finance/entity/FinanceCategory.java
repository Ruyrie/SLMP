package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 收支一级分类（t_finance_category，全局表，管理端-03 维护）。 */
@Getter
@Setter
@TableName("t_finance_category")
public class FinanceCategory extends AuditEntity {

    private String direction;
    private String code;
    private String name;
    private String icon;
    private Integer sortNo;
    private String status;
    private String autoSource;
    private Boolean markOnly;
    private Boolean isFallback;
    private LocalDateTime disabledAt;
    private String disableReason;

    @Version
    private Integer version;

    private Long updatedBy;
}

package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 支出子类（t_expense_subcategory，按养殖场共享）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_expense_subcategory")
public class ExpenseSubcategory extends AuditEntity {

    private Long farmId;
    private Long categoryId;
    private String name;

    @TableLogic
    private Integer deleted;

    private LocalDateTime deletedAt;
}

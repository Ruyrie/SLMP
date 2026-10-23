package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端基础数据明细表的公共字段（版本明细、字典范围、症状病症）：只有 created_by，没有 updated_by / version。
 */
@Getter
@Setter
public abstract class AdminDetailEntity extends BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
}

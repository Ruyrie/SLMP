package com.zhimu.server.framework.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 由人操作产生的表的实体基类：在 {@link BaseEntity} 基础上加 created_by（当前用户或管理员 ID，系统生成为 0）。
 */
@Getter
@Setter
@ToString(callSuper = true)
public abstract class AuditEntity extends BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
}

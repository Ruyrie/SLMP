package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端基础数据可编辑表的公共字段：created_by、updated_by、version（乐观锁）。
 * 本模块不做逻辑删除，实体不声明 deleted。
 */
@Getter
@Setter
public abstract class BaseAdminEntity extends BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    private Long updatedBy;

    @Version
    private Integer version;
}

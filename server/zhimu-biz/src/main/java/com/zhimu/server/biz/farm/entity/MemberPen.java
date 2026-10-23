package com.zhimu.server.biz.farm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 员工负责栏舍（t_member_pen，只追加，无 updated_at；调整时物理删除后重建） */
@Getter
@Setter
@FarmScoped
@TableName("t_member_pen")
public class MemberPen {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long farmId;
    private Long memberId;
    private Long userId;
    private Long penId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

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

/** 邀请预分配栏舍（t_farm_invite_pen，只追加，无 updated_at） */
@Getter
@Setter
@FarmScoped
@TableName("t_farm_invite_pen")
public class FarmInvitePen {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inviteId;
    private Long farmId;
    private Long penId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

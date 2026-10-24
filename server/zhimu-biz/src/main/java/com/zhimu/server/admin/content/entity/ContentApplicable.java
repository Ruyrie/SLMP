package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 内容适用品类（t_content_applicable）。没有记录表示适用全部品类。
 */
@Getter
@Setter
@ToString
@TableName("t_content_applicable")
public class ContentApplicable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private Long categoryId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

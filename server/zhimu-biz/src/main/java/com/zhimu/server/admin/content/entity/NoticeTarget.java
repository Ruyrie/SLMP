package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 通知接收条件（t_notice_target）：CONDITION / FARM 范围保存角色、省份、品类、养殖场。 */
@Getter
@Setter
@TableName("t_notice_target")
public class NoticeTarget {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noticeId;
    /** ROLE / PROVINCE / CATEGORY / FARM */
    private String targetType;
    private String targetValue;
    private LocalDateTime createdAt;
}

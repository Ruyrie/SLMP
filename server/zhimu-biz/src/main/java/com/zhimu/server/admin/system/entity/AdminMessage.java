package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 管理端站内消息（t_admin_message，管理端-04 2.3.5）。每个接收管理员一行，各管理端模块通过
 * AdminMessageService 写入；同一接收人同一 merge_key 只保留一条。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_admin_message")
public class AdminMessage extends AuditEntity {

    private Long adminId;
    private String msgType;
    private String level;
    private String title;
    private String summary;
    private String bizType;
    private Long bizId;
    private String routePage;
    /** JSON 对象文本：打开页面时的筛选或抽屉参数 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String routeParams;
    private String mergeKey;
    /** 合并去重重发时允许把已读重新置为未读，因此更新时始终写该列 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime readAt;

    @TableLogic
    private Integer deleted;
}

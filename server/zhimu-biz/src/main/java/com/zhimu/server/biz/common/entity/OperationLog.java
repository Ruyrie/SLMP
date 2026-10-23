package com.zhimu.server.biz.common.entity;

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
 * 操作留痕（t_operation_log，05 号 5.5 节）。只追加，没有 updated_at / deleted / version，
 * 因此不继承 BaseEntity / AuditEntity（任务书第四节）。
 */
@Getter
@Setter
@ToString
@TableName("t_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operatorType;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String opType;
    private String opAction;
    private String result;
    private String bizType;
    private Long bizId;
    private String targetName;
    private Long farmId;
    private String searchText;
    private String reason;
    private String extraText;
    private String notifyText;
    /** JSON 文本：字段编码 -> {name 中文名, display 显示文本} */
    private String beforeJson;
    /** JSON 文本：同 before_json 结构 */
    private String afterJson;
    private Long changeRequestId;
    private String clientType;
    private String userAgent;
    private String ip;
    private String ipRegion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

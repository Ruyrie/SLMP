package com.zhimu.server.biz.today.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 待办处理记录（t_todo_handle_log）：完成、忽略与撤销完成的流水。 */
@Getter
@Setter
@FarmScoped
@TableName("t_todo_handle_log")
public class TodoHandleLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long todoId;
    private Long farmId;
    /** COMPLETE / IGNORE / REVERT */
    private String action;
    /** COMPLETE_WAY / IGNORE_WAY */
    private String way;
    private Long operatorId;
    private String reason;
    private Boolean overdueDone;
    private LocalDateTime createdAt;
}

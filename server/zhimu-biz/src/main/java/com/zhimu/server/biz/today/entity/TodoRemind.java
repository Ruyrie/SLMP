package com.zhimu.server.biz.today.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 待办提醒计划（t_todo_remind）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_todo_remind")
public class TodoRemind extends BaseEntity {

    private Long todoId;
    private Long farmId;
    /** REMIND_STAGE */
    private String remindStage;
    private String cycleKey;
    /** REMIND_WAY */
    private String remindWay;
    private LocalDateTime remindAt;
    private String receiverScope;
    private String msgType;
    /** REMIND_STATUS */
    private String status;
    private String cancelReason;
    private LocalDateTime sentAt;
    private Integer receiverCount;
}

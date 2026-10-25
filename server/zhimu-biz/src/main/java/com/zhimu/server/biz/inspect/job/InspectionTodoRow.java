package com.zhimu.server.biz.inspect.job;

import lombok.Data;

import java.time.LocalDate;

/** 巡检待办（t_todo，todo_type = INSPECTION）的简要信息。 */
@Data
public class InspectionTodoRow {

    private Long id;
    private Long penId;
    private String dedupKey;
    private String status;
    private LocalDate dueDate;
}

package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** ADM-C55 创建知识补充任务。 */
@Data
public class KnowledgeTaskCreateRequest {

    @NotBlank(message = "请填写任务标题")
    @Size(max = 40, message = "任务标题不能超过 40 字")
    private String title;

    /** AI_QA / CORRECTION / MANUAL */
    @NotBlank(message = "请选择来源")
    private String sourceType;

    private Long sourceMessageId;

    /** NEW_ENTRY / SUPPLEMENT */
    @NotBlank(message = "请选择补充方式")
    private String mode;

    private Long targetKnowledgeId;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    @NotNull(message = "请选择指派给")
    private Long assigneeId;

    @NotNull(message = "请选择期望完成日期")
    private LocalDate dueDate;

    @NotBlank(message = "请填写补充要点")
    @Size(max = 200, message = "补充要点不能超过 200 字")
    private String points;

    @Min(value = 0, message = "相似问题数不能小于 0")
    private Integer similarCount;
}

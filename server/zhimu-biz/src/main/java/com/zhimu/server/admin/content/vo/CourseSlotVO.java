package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 一档转码进度（课程列表 / 抽屉的两行进度）。 */
@Data
public class CourseSlotVO {

    private String status;
    private BigDecimal progress;
}

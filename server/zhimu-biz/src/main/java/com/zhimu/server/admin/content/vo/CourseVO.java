package com.zhimu.server.admin.content.vo;

import lombok.Data;

/** 课程写接口返回（ADM-C26/C27/C30–C33）。 */
@Data
public class CourseVO {

    private Long id;
    private String status;
    private String transcodeStatus;
    private Integer shiftedCount;
    private Integer version;
}

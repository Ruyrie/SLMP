package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 课程列表行（ADM-C23）。 */
@Data
public class CourseRowVO {

    private Long id;
    private String title;
    private String coverUrl;
    private Integer durationSec;
    private Long categoryId;
    private String categoryName;
    private Long topicId;
    private String topicName;
    private Integer sectionNo;
    /** UPLOADING / READY / FAILED；未绑定视频为空 */
    private String uploadStatus;
    private String transcodeStatus;
    private String transcodeStatusText;
    private CourseSlotVO p720;
    private CourseSlotVO p480;
    private String status;
    private String statusText;
    private Integer playCount;
    private BigDecimal finishRate;
    private LocalDateTime updatedAt;
    private String updatedByName;
    private Boolean canOnShelf;
}

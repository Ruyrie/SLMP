package com.zhimu.server.admin.content.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 上传 / 编辑抽屉（ADM-C28）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseDetailVO extends CourseRowVO {

    private String intro;
    private Long knowledgeId;
    private String knowledgeTitle;
    private List<Long> applicableCategoryIds;
    private VideoInfoVO video;
    private String transcodeError;
    private Long coverFileId;
    private String coverSource;
    private List<CoverCandidateVO> coverCandidates;
    private LocalDateTime onShelfAt;
    private String onShelfByName;
    private LocalDateTime offShelfAt;
    private String offReason;
    private Integer version;
}

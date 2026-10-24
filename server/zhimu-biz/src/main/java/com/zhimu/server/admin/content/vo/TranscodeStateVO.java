package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 转码进度轮询（ADM-C29 / ADM-C30 / ADM-S37）。 */
@Data
public class TranscodeStateVO {

    private Long courseId;
    private Long fileId;
    private String uploadStatus;
    private String transcodeStatus;
    private CourseSlotVO p720;
    private CourseSlotVO p480;
    private String transcodeError;
    private Boolean coversReady;
    private List<CoverCandidateVO> coverCandidates = new ArrayList<>();
}

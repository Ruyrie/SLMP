package com.zhimu.server.biz.ai.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * API-K17 专题详情。action：START / CONTINUE / REVIEW。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TopicDetailVO extends TopicVO {

    private String intro;
    private String action;
    /** 分享地址：USER_WEB_BASE_URL + Web 用户端养殖课堂专题页 */
    private String shareUrl;
    private List<CourseCardVO> courses = new ArrayList<>();
    private Resume resume;

    @Data
    public static class Resume {
        private Long courseId;
        private int sectionNo;
        private int positionSec;
    }
}

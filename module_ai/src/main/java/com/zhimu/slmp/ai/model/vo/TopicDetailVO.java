package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-K17 专题详情（K07，K05“本专题”）。action：START / CONTINUE / REVIEW；resume 为续学位置（没看过时为 null）。
 */
public class TopicDetailVO extends TopicVO {

    public static final String ACTION_START = "START";
    public static final String ACTION_CONTINUE = "CONTINUE";
    public static final String ACTION_REVIEW = "REVIEW";

    private String intro;
    private String action;
    private List<CourseCardVO> courses;
    private Resume resume;
    /** 网页版专题完整地址（服务端按 USER_WEB_BASE_URL 拼出）；为空时只分享标题 */
    private String shareUrl;

    /** 续学位置 */
    public static class Resume {
        private long courseId;
        private int sectionNo;
        private int positionSec;

        public long getCourseId() {
            return courseId;
        }

        public int getSectionNo() {
            return sectionNo;
        }

        public int getPositionSec() {
            return positionSec;
        }

        public void setCourseId(long courseId) {
            this.courseId = courseId;
        }

        public void setSectionNo(int sectionNo) {
            this.sectionNo = sectionNo;
        }

        public void setPositionSec(int positionSec) {
            this.positionSec = positionSec;
        }
    }

    @Nullable
    public String getIntro() {
        return intro;
    }

    @Nullable
    public String getAction() {
        return action;
    }

    @NonNull
    public List<CourseCardVO> getCourses() {
        return courses == null ? Collections.emptyList() : courses;
    }

    @Nullable
    public Resume getResume() {
        return resume;
    }

    public void setIntro(@Nullable String intro) {
        this.intro = intro;
    }

    public void setAction(@Nullable String action) {
        this.action = action;
    }

    public void setCourses(@Nullable List<CourseCardVO> courses) {
        this.courses = courses;
    }

    public void setResume(@Nullable Resume resume) {
        this.resume = resume;
    }

    @Nullable
    public String getShareUrl() {
        return shareUrl;
    }

    public void setShareUrl(@Nullable String shareUrl) {
        this.shareUrl = shareUrl;
    }
}

package com.zhimu.slmp.ai.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * K05“问 AI”带入 K01 的课程背景：提问时带 context_course_id，输入栏上方显示“正在问：课程标题”。
 */
public final class CourseContext {

    private final long courseId;
    @NonNull
    private final String title;

    public CourseContext(long courseId, @NonNull String title) {
        this.courseId = courseId;
        this.title = title;
    }

    public long getCourseId() {
        return courseId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CourseContext)) {
            return false;
        }
        CourseContext that = (CourseContext) o;
        return courseId == that.courseId && title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId, title);
    }
}

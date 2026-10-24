package com.zhimu.server.admin.content.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-C37 新建专题 / ADM-C38 编辑专题与节次。 */
@Data
public class CourseTopicSaveRequest {

    @NotBlank(message = "请填写专题名称")
    @Size(max = 20, message = "专题名称不能超过 20 字")
    private String name;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    @Size(max = 200, message = "专题简介不能超过 200 字")
    private String intro;

    private Long coverFileId;
    private Integer sortNo;

    @Valid
    private List<TopicCourseRequest> courses = new ArrayList<>();

    /** ADM-C38 必填 */
    private Integer version;

    @Data
    public static class TopicCourseRequest {
        @NotNull(message = "请选择课程")
        private Long courseId;
        @Min(value = 1, message = "加入专题时请填写节次")
        private Integer sectionNo;
    }
}

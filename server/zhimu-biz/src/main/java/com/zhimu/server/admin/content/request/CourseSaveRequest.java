package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** ADM-C26 新建课程 / ADM-C27 编辑课程。 */
@Data
public class CourseSaveRequest {

    /** DRAFT / ON_SHELF */
    private String submitAction;

    @Size(max = 30, message = "标题不能超过 30 字")
    private String title;

    private Long categoryId;
    private Long topicId;
    private Integer sectionNo;
    @Size(max = 10, message = "适用品类最多 10 个")
    private List<Long> applicableCategoryIds;
    private Long knowledgeId;

    @Size(max = 200, message = "简介不能超过 200 字")
    private String intro;

    private Long videoFileId;
    /** AUTO / UPLOAD */
    private String coverSource;
    private Long coverFileId;

    /** ADM-C27 必填 */
    private Integer version;
}

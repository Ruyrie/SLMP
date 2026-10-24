package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** ADM-C46 新建条目 / ADM-C47 保存草稿。 */
@Data
public class KnowledgeSaveRequest {

    @Size(max = 40, message = "标题不能超过 40 字")
    private String title;

    private Long categoryId;

    /** PLATFORM / DISEASE；C46 必填，编辑时不可改 */
    private String sourceType;

    private Boolean isMajorEpidemic;
    /** STANDARD / ARTICLE；不传按来源取默认 */
    private String displayType;
    private List<Long> applicableCategoryIds;
    @Size(max = 8, message = "关键词最多 8 个")
    private List<String> keywords;

    private String content;
    private Long sourceTaskId;

    /** C46：DRAFT / PUBLISH；C47 忽略 */
    private String submitAction;

    @Size(max = 100, message = "修改说明不能超过 100 字")
    private String changeNote;

    /** ADM-C47 必填 */
    private Integer version;
}

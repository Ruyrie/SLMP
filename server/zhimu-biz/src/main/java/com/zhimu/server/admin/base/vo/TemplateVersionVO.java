package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 模板版本记录（ADM-D21 / D23 / D46 / D48）：items 为免疫项目或巡检项目。 */
@Getter
@Setter
public class TemplateVersionVO<T> {

    private Long versionId;
    private Integer versionNo;
    /** VERSION_STATUS */
    private String status;
    private String statusText;
    private String changeNote;
    private Integer itemCount;
    private String publishedByName;
    private LocalDateTime publishedAt;
    private List<T> items = new ArrayList<>();
}

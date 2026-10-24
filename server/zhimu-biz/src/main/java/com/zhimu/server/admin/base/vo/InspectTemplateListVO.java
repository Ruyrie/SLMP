package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** ADM-D39 巡检模板列表：未建模板的启用品类 template_id 为空。 */
@Getter
@Setter
public class InspectTemplateListVO {

    private Long categoryId;
    private String categoryName;
    private Long templateId;
    private String templateName;
    private Integer itemCount;
    private Integer onlineVersionNo;
    private Boolean hasDraft;
}

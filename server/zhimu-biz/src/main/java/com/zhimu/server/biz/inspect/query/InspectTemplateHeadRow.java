package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** 已发布巡检模板头（t_inspect_template + t_inspect_template_version）。 */
@Data
public class InspectTemplateHeadRow {
    private Long templateId;
    private Integer versionNo;
    private Long versionId;
}

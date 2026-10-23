package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** 品种当前已发布的免疫模板与版本（t_immune_template + t_immune_template_version）。 */
@Getter
@Setter
public class ImmuneTemplateRow {
    private Long templateId;
    private Long breedId;
    private String name;
    private Integer onlineVersionNo;
    private Long versionId;
    private Integer versionNo;
}

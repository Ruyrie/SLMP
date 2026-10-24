package com.zhimu.server.admin.content.mapper;

import lombok.Data;

/** 养殖场简要信息（通知接收范围回填、饲料场下拉）。 */
@Data
public class FarmBriefRow {
    private Long farmId;
    private String name;
    private String adcode;
    private Long mainCategoryId;
    private String auditStatus;
    private Boolean disabled;
    private Boolean frozen;
}

package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

/** 巡检模板未发布修改摘要。 */
@Getter
@Setter
public class InspectDraftChangeVO {

    /** ITEM_CHANGE */
    private String changeType;
    private String itemName;
    /** 变化字段名，例如 项目名称、可选结果、启用；新增 / 删除时为空 */
    private String fieldName;
    private String before;
    private String after;
}

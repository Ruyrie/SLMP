package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 巡检项目（t_inspect_item）：按模板版本，已发布版本不再修改。 */
@Getter
@Setter
@TableName("t_inspect_item")
public class InspectItem extends AdminDetailEntity {

    private Long templateId;
    private Long versionId;
    private Long sourceItemId;
    /** ITEM_CHANGE：NONE / ADDED / MODIFIED / DELETED */
    private String changeType;
    private Integer sortNo;
    private String itemName;
    private String checkNote;
    private Boolean enabled;
    private String disableReason;
}

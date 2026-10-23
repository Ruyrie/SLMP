package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 巡检模板（t_inspect_template）：每个品类一份。 */
@Getter
@Setter
@TableName("t_inspect_template")
public class InspectTemplate extends BaseAdminEntity {

    private Long categoryId;
    private String name;
    private Integer onlineVersionNo;
    private Boolean hasDraft;
}

package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 巡检结果选项（t_inspect_option）：第一个结果必须为正常。 */
@Getter
@Setter
@TableName("t_inspect_option")
public class InspectOption extends AdminDetailEntity {

    private Long itemId;
    private Integer sortNo;
    private String optionName;
    private Boolean isAbnormal;
}

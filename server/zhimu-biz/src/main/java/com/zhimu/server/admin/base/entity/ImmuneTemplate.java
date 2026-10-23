package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 免疫程序模板（t_immune_template）：每个品种一份，线上版本号与草稿标记。 */
@Getter
@Setter
@TableName("t_immune_template")
public class ImmuneTemplate extends BaseAdminEntity {

    private Long breedId;
    private String name;
    /** 线上版本号；从未发布为空 */
    private Integer onlineVersionNo;
    /** 有未发布修改：1 是 */
    private Boolean hasDraft;
    /** 线上版本含按日期项目：1 是 */
    private Boolean hasByDate;
    private Long copiedFromId;
}

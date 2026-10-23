package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 通用字典项（t_dict_item）：以 dict_type 区分 8 类字典。 */
@Getter
@Setter
@TableName("t_dict_item")
public class DictItem extends BaseAdminEntity {

    private String dictType;
    private String code;
    private String name;
    private String description;
    private String icon;
    private Boolean remarkRequired;
    private Boolean isFallback;
    private Boolean isSystem;
    private Integer sortNo;
    /** BASE_STATUS：ENABLED / DISABLED */
    private String status;
    private String disableReason;
    private LocalDateTime disabledAt;
    private Long disabledBy;
}

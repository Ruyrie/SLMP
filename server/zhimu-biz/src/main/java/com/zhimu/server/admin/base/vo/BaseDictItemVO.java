package com.zhimu.server.admin.base.vo;

import lombok.Data;

/** API-U10 字典项（只含启用项），字段与 App lib_common DictItemVO 完全一致。 */
@Data
public class BaseDictItemVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    /** JSON 键 remark_required */
    private Boolean remarkRequired;
    /** JSON 键 is_system（App 字段 isSystem） */
    private Boolean isSystem;
    private Integer sortNo;
}

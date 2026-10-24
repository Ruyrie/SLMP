package com.zhimu.server.admin.base.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-D30 禁用药清单筛选（名称或别名匹配）。 */
@Getter
@Setter
public class BannedDrugQuery extends PageQuery {

    @Size(max = 40, message = "搜索关键字最多 40 个字")
    private String keyword;

    /** BANNED_CLASS */
    private String bannedClass;
}

package com.zhimu.server.admin.base.query;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * ADM-D14 免疫模板列表筛选。返回按品类分组，不分页（左侧模板选择器）。
 * breed_id / category_id 用于详情页刷新单行；status 取 CONFIGURED / UNCONFIGURED / DRAFT。
 */
@Getter
@Setter
public class ImmuneTemplateQuery {

    @Size(max = 20, message = "搜索关键字最多 20 个字")
    private String keyword;

    private Long breedId;

    private Long categoryId;

    /** CONFIGURED 已配置 / UNCONFIGURED 未配置 / DRAFT 有未发布修改；为空返回全部 */
    private String status;
}

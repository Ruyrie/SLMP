package com.zhimu.server.admin.base.api;

import com.zhimu.server.admin.base.vo.BaseCategoryVO;
import com.zhimu.server.admin.base.vo.CategoryNodeVO;
import com.zhimu.server.admin.base.vo.CategoryVO;

import java.util.List;

/** 品类查询（管理端 D01 与 U09；模块02 等通过 BaseDataQueryService 取简要信息）。 */
public interface CategoryQueryService {

    /** ADM-D01 品类品种树；keyword 匹配品种名称或说明，命中品种的品类才返回 */
    List<CategoryNodeVO> tree(String keyword);

    /** API-U09 启用的品类与启用品种（含 has_*） */
    List<BaseCategoryVO> enabledTree();

    /** 品类详情；不存在返回 null */
    CategoryVO get(Long categoryId);

    /** 品类实体简要信息；不存在返回 null */
    CategoryInfo info(Long categoryId);
}

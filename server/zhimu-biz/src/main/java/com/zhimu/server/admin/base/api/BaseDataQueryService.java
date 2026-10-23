package com.zhimu.server.admin.base.api;

import java.util.List;

/**
 * 基础数据查询（管理端-03 实现，第 2 批）。在此之前由 admin.base.support.TemporaryBaseDataQueryService 提供示例品类，
 * 第 2 批的初始化数据必须沿用相同的品类 ID。
 */
public interface BaseDataQueryService {

    /** 不存在返回 null（含已停用） */
    CategoryInfo getCategory(Long categoryId);

    /** 启用的品类 */
    List<CategoryInfo> listEnabledCategories();
}
package com.zhimu.server.admin.base.support;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 临时实现：管理端-03（第 2 批）实现基础数据前，给模块02 的 F02/F05/F09 提供示例品类。
 * 品类 ID 与 App Mock、05 号统一示例数据一致（101 育肥猪、102 种猪、201 肉鸡、301 肉牛），
 * 第 2 批初始化数据必须沿用这些 ID。管理端-03 落地后删除本类。
 */
@Service
public class TemporaryBaseDataQueryService implements BaseDataQueryService {

    private static final List<CategoryInfo> CATEGORIES = List.of(
            new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD", "头", false, true),
            new CategoryInfo(102L, "种猪", "PIG", "猪", "HEAD", "头", true, true),
            new CategoryInfo(201L, "肉鸡", "POULTRY", "禽", "BIRD", "只", false, true),
            new CategoryInfo(301L, "肉牛", "CATTLE", "牛", "HEAD", "头", false, true));

    @Override
    public CategoryInfo getCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        for (CategoryInfo category : CATEGORIES) {
            if (category.id().equals(categoryId)) {
                return category;
            }
        }
        return null;
    }

    @Override
    public List<CategoryInfo> listEnabledCategories() {
        return CATEGORIES;
    }
}

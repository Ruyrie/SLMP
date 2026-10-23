package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.support.BaseDataReadSupport;
import com.zhimu.server.admin.base.vo.BaseCategoryBreedVO;
import com.zhimu.server.admin.base.vo.BaseCategoryVO;
import com.zhimu.server.admin.base.vo.CategoryNodeVO;
import com.zhimu.server.admin.base.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 品类查询：ADM-D01 树、API-U09 启用树，并实现第 1 批的 BaseDataQueryService（两个方法签名不变），
 * 替换 TemporaryBaseDataQueryService，供模块02 F02/F05/F09/A15 使用。
 */
@Service
@RequiredArgsConstructor
public class CategoryQueryServiceImpl implements CategoryQueryService, BaseDataQueryService {

    private final CategoryMapper categoryMapper;
    private final BreedMapper breedMapper;
    private final BaseDataReadSupport support;

    @Override
    public List<CategoryNodeVO> tree(String keyword) {
        String search = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<Category> categories = support.categories();
        Map<Long, List<Breed>> breeds = support.breedsByCategory();
        Set<Long> weight = support.publishedWeightBreedIds();
        Set<Long> feed = support.publishedFeedBreedIds();
        List<CategoryNodeVO> result = new ArrayList<>();
        for (Category category : categories) {
            List<Breed> categoryBreeds = breeds.getOrDefault(category.getId(), List.of());
            List<Breed> matched = new ArrayList<>();
            for (Breed breed : categoryBreeds) {
                if (search == null || contains(breed.getName(), search) || contains(breed.getDescription(), search)) {
                    matched.add(breed);
                }
            }
            if (search != null && matched.isEmpty()) {
                continue;
            }
            CategoryNodeVO node = AdminBaseConvert.categoryNode(category, categoryBreeds.size());
            for (Breed breed : matched) {
                node.getBreeds().add(AdminBaseConvert.breedNode(breed, weight.contains(breed.getId()),
                        feed.contains(breed.getId()), support.hasImmuneTemplate(breed.getId())));
            }
            result.add(node);
        }
        return result;
    }

    @Override
    public List<BaseCategoryVO> enabledTree() {
        List<Category> categories = support.categories();
        Map<Long, List<Breed>> breeds = support.breedsByCategory();
        Set<Long> weight = support.publishedWeightBreedIds();
        Set<Long> feed = support.publishedFeedBreedIds();
        List<BaseCategoryVO> result = new ArrayList<>();
        for (Category category : categories) {
            if (!"ENABLED".equals(category.getStatus())) {
                continue;
            }
            BaseCategoryVO vo = AdminBaseConvert.baseCategory(category);
            for (Breed breed : breeds.getOrDefault(category.getId(), List.of())) {
                if (!"ENABLED".equals(breed.getStatus())) {
                    continue;
                }
                BaseCategoryBreedVO breedVO = AdminBaseConvert.baseBreed(breed, weight.contains(breed.getId()),
                        feed.contains(breed.getId()), support.hasImmuneTemplate(breed.getId()));
                vo.getBreeds().add(breedVO);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public CategoryVO get(Long categoryId) {
        Category category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null) {
            return null;
        }
        CategoryVO vo = AdminBaseConvert.category(category);
        vo.setBreedCount(Math.toIntExact(breedMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Breed>lambdaQuery()
                        .eq(Breed::getCategoryId, categoryId))));
        return vo;
    }

    @Override
    public CategoryInfo info(Long categoryId) {
        Category category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        return category == null ? null : toInfo(category);
    }

    @Override
    public CategoryInfo getCategory(Long categoryId) {
        return info(categoryId);
    }

    @Override
    public List<CategoryInfo> listEnabledCategories() {
        Map<Long, CategoryInfo> result = new LinkedHashMap<>();
        for (Category category : support.categories()) {
            if ("ENABLED".equals(category.getStatus())) {
                result.put(category.getId(), toInfo(category));
            }
        }
        return new ArrayList<>(result.values());
    }

    private static CategoryInfo toInfo(Category category) {
        return new CategoryInfo(category.getId(), category.getName(), category.getSpecies(),
                AdminBaseConvert.speciesText(category.getSpecies()), category.getCountUnit(),
                AdminBaseConvert.countUnitText(category.getCountUnit()),
                Boolean.TRUE.equals(category.getIsBreeding()), "ENABLED".equals(category.getStatus()));
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }
}

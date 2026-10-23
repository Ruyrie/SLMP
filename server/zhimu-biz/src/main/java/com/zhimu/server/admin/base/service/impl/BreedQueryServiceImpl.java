package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.support.BaseDataReadSupport;
import com.zhimu.server.admin.base.vo.BreedDetailVO;
import com.zhimu.server.admin.base.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 品种查询：ADM-D04 详情与供 3B/模块04 使用的简要信息。 */
@Service
@RequiredArgsConstructor
public class BreedQueryServiceImpl implements BreedQueryService {

    private final BreedMapper breedMapper;
    private final CategoryMapper categoryMapper;
    private final BaseDataReadSupport support;

    @Override
    public BreedDetailVO detail(Long breedId) {
        Breed breed = breedId == null ? null : breedMapper.selectById(breedId);
        if (breed == null) {
            return null;
        }
        Category category = categoryMapper.selectById(breed.getCategoryId());
        CategoryVO categoryVO = category == null ? null : AdminBaseConvert.category(category);
        Breed offspring = breed.getOffspringBreedId() == null ? null
                : breedMapper.selectById(breed.getOffspringBreedId());
        BreedDetailVO vo = AdminBaseConvert.breedDetail(breed, category, categoryVO, offspring);
        vo.setTabs(tabs(breed, category));
        return vo;
    }

    @Override
    public BreedInfo info(Long breedId) {
        Breed breed = breedId == null ? null : breedMapper.selectById(breedId);
        return breed == null ? null : new BreedInfo(breed.getId(), breed.getCategoryId(), breed.getName(),
                breed.getGestationDays(), breed.getIncubationDays(), breed.getPregnancyCheckDays(),
                breed.getOffspringBreedId(), "ENABLED".equals(breed.getStatus()));
    }

    @Override
    public List<BreedInfo> listEnabled(Long categoryId) {
        List<BreedInfo> result = new ArrayList<>();
        if (categoryId == null) {
            return result;
        }
        for (Breed breed : support.breeds()) {
            if (categoryId.equals(breed.getCategoryId()) && "ENABLED".equals(breed.getStatus())) {
                result.add(new BreedInfo(breed.getId(), breed.getCategoryId(), breed.getName(),
                        breed.getGestationDays(), breed.getIncubationDays(), breed.getPregnancyCheckDays(),
                        breed.getOffspringBreedId(), true));
            }
        }
        return result;
    }

    /** BREED_TAB 顺序：WEIGHT、FEED、IMMUNE_INSPECT、BREEDING、INCUBATION、IMMUNE。 */
    private List<String> tabs(Breed breed, Category category) {
        Set<Long> weight = support.publishedWeightBreedIds();
        Set<Long> feed = support.publishedFeedBreedIds();
        List<String> tabs = new ArrayList<>();
        if (weight.contains(breed.getId())) {
            tabs.add("WEIGHT");
        }
        if (feed.contains(breed.getId())) {
            tabs.add("FEED");
        }
        if (category != null && support.inspectCategoryIds().contains(category.getId())) {
            tabs.add("IMMUNE_INSPECT");
        }
        if (breed.getGestationDays() != null || (category != null && Boolean.TRUE.equals(category.getIsBreeding()))) {
            tabs.add("BREEDING");
        }
        if (breed.getIncubationDays() != null) {
            tabs.add("INCUBATION");
        }
        if (support.hasImmuneTemplate(breed.getId())) {
            tabs.add("IMMUNE");
        }
        return tabs;
    }
}

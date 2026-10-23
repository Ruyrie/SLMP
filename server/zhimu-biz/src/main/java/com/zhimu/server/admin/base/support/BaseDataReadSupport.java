package com.zhimu.server.admin.base.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.entity.FeedStdVersion;
import com.zhimu.server.admin.base.entity.ImmuneTemplate;
import com.zhimu.server.admin.base.entity.InspectTemplate;
import com.zhimu.server.admin.base.entity.StdWeightVersion;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemScopeMapper;
import com.zhimu.server.admin.base.mapper.FeedStdVersionMapper;
import com.zhimu.server.admin.base.mapper.ImmuneTemplateMapper;
import com.zhimu.server.admin.base.mapper.InspectTemplateMapper;
import com.zhimu.server.admin.base.mapper.StdWeightVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基础数据只读装配：品类/品种排序、已发布标准存在性、巡检模板存在性、字典适用范围。
 * 只查本模块的表；供 4 个查询服务与管理端 Service 复用，避免 N+1。
 */
@Component
@RequiredArgsConstructor
public class BaseDataReadSupport {

    private final CategoryMapper categoryMapper;
    private final BreedMapper breedMapper;
    private final StdWeightVersionMapper stdWeightVersionMapper;
    private final FeedStdVersionMapper feedStdVersionMapper;
    private final InspectTemplateMapper inspectTemplateMapper;
    private final ImmuneTemplateMapper immuneTemplateMapper;
    private final DictItemScopeMapper dictItemScopeMapper;

    public List<Category> categories() {
        List<Category> list = new ArrayList<>(categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSortNo).orderByAsc(Category::getId)));
        list.sort(Comparator.comparing(Category::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Category::getId));
        return list;
    }

    public List<Breed> breeds() {
        List<Breed> list = new ArrayList<>(breedMapper.selectList(Wrappers.<Breed>lambdaQuery()
                .orderByAsc(Breed::getSortNo).orderByAsc(Breed::getId)));
        list.sort(Comparator.comparing(Breed::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Breed::getId));
        return list;
    }

    public Map<Long, List<Breed>> breedsByCategory() {
        Map<Long, List<Breed>> map = new HashMap<>();
        for (Breed breed : breeds()) {
            map.computeIfAbsent(breed.getCategoryId(), key -> new ArrayList<>()).add(breed);
        }
        return map;
    }

    public Set<Long> publishedWeightBreedIds() {
        Set<Long> ids = new HashSet<>();
        for (StdWeightVersion version : stdWeightVersionMapper.selectList(Wrappers.<StdWeightVersion>lambdaQuery()
                .eq(StdWeightVersion::getStatus, "PUBLISHED"))) {
            ids.add(version.getBreedId());
        }
        return ids;
    }

    public Set<Long> publishedFeedBreedIds() {
        Set<Long> ids = new HashSet<>();
        for (FeedStdVersion version : feedStdVersionMapper.selectList(Wrappers.<FeedStdVersion>lambdaQuery()
                .eq(FeedStdVersion::getStatus, "PUBLISHED"))) {
            ids.add(version.getBreedId());
        }
        return ids;
    }

    public Set<Long> inspectCategoryIds() {
        Set<Long> ids = new HashSet<>();
        for (InspectTemplate template : inspectTemplateMapper.selectList(Wrappers.lambdaQuery())) {
            ids.add(template.getCategoryId());
        }
        return ids;
    }

    /** 是否有已发布免疫模板：t_immune_template.online_version_no 非空（V1_23_1 由 3B 提供）。 */
    public boolean hasImmuneTemplate(Long breedId) {
        if (breedId == null) {
            return false;
        }
        return immuneTemplateMapper.selectCount(Wrappers.<ImmuneTemplate>lambdaQuery()
                .eq(ImmuneTemplate::getBreedId, breedId)
                .isNotNull(ImmuneTemplate::getOnlineVersionNo)) > 0;
    }

    public Map<Long, List<DictItemScope>> scopesByItem(Collection<Long> itemIds) {
        Map<Long, List<DictItemScope>> map = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return map;
        }
        for (DictItemScope scope : dictItemScopeMapper.selectList(Wrappers.<DictItemScope>lambdaQuery()
                .in(DictItemScope::getDictItemId, itemIds)
                .orderByAsc(DictItemScope::getId))) {
            map.computeIfAbsent(scope.getDictItemId(), key -> new ArrayList<>()).add(scope);
        }
        return map;
    }
}

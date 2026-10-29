package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.event.BaseDataChangedEvent;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.DictItemScopeMapper;
import com.zhimu.server.admin.base.query.DictItemQuery;
import com.zhimu.server.admin.base.request.DictItemSaveRequest;
import com.zhimu.server.admin.base.request.DictStatusRequest;
import com.zhimu.server.admin.base.request.ItemOrderRequest;
import com.zhimu.server.admin.base.service.DictAdminService;
import com.zhimu.server.admin.base.support.BaseDataReadSupport;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.system.support.AdminDiffs;
import com.zhimu.server.admin.base.support.DictScopes;
import com.zhimu.server.admin.base.support.DictTypes;
import com.zhimu.server.admin.base.vo.DictItemVO;
import com.zhimu.server.admin.base.vo.DictScopeVO;
import com.zhimu.server.admin.base.vo.DictTypeVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.Species;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ADM-D49–D54 字典维护：t_dict_item 8 类 + 收入 / 支出分类（t_finance_category，见 FinanceCategoryDictSupport）。 */
@Service
@RequiredArgsConstructor
public class DictAdminServiceImpl implements DictAdminService {

    private final DictItemMapper dictItemMapper;
    private final DictItemScopeMapper scopeMapper;
    private final CategoryMapper categoryMapper;
    private final BaseDataReadSupport support;
    private final BaseLogs logs;
    private final ApplicationEventPublisher eventPublisher;
    private final FinanceCategoryDictSupport finance;

    @Override
    public List<DictTypeVO> types() {
        Map<String, Integer> counts = new HashMap<>();
        for (DictItem item : dictItemMapper.selectList(Wrappers.lambdaQuery())) {
            counts.merge(item.getDictType(), 1, Integer::sum);
        }
        List<DictTypeVO> result = new ArrayList<>();
        for (String type : DictTypes.ALL) {
            DictTypeVO vo = new DictTypeVO();
            vo.setDictType(type);
            vo.setName(DictTypes.displayName(type));
            vo.setIcon(DictTypes.icon(type));
            vo.setItemCount(counts.getOrDefault(type, 0));
            vo.setScopeMode(DictTypes.scopeMode(type));
            vo.setPlatformOnly(false);
            result.add(vo);
        }
        // 收入 / 支出一级分类（t_finance_category）：平台统一维护，养殖户只能在支出分类下加子类
        Map<String, Integer> financeCounts = finance.counts();
        for (String type : DictTypes.FINANCE) {
            DictTypeVO vo = new DictTypeVO();
            vo.setDictType(type);
            vo.setName(DictTypes.displayName(type));
            vo.setIcon(DictTypes.icon(type));
            vo.setItemCount(financeCounts.getOrDefault(type, 0));
            vo.setScopeMode("NONE");
            vo.setPlatformOnly(true);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<DictItemVO> items(String dictType, DictItemQuery query) {
        String type = requireType(dictType);
        if (DictTypes.isFinance(type)) {
            return finance.items(type);
        }
        String species = query == null ? null : normalizeSpecies(query.getSpecies());
        Long categoryId = query == null ? null : query.getCategoryId();
        Category category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (categoryId != null && category == null) {
            throw BaseErrors.notFound();
        }
        if (category != null) {
            species = category.getSpecies();
        }
        List<DictItem> items = dictItemMapper.selectList(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .orderByAsc(DictItem::getSortNo)
                .orderByAsc(DictItem::getId));
        Map<Long, List<DictItemScope>> scopes = support.scopesByItem(items.stream().map(DictItem::getId).toList());
        Map<Long, String> categoryNames = categoryNames();
        List<DictItemVO> result = new ArrayList<>();
        for (DictItem item : items) {
            List<DictItemScope> itemScopes = scopes.getOrDefault(item.getId(), List.of());
            if ((species != null || categoryId != null) && !DictScopes.matches(itemScopes, species, categoryId)) {
                continue;
            }
            result.add(AdminBaseConvert.dictItem(item, itemScopes, categoryNames));
        }
        return result;
    }

    @Override
    @Transactional
    public DictItemVO create(String dictType, DictItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        String type = requireType(dictType);
        if (DictTypes.isFinance(type)) {
            return finance.create(type, request);
        }
        String name = validateName(type, request.getName());
        List<ScopeValue> scopes = validateScopes(type, request.getScopes());
        requireNameUnique(type, name, scopes, null);
        LocalDateTime now = BeijingTime.now();
        DictItem item = new DictItem();
        item.setDictType(type);
        item.setCode(generateCode(type));
        item.setName(name);
        item.setDescription(trimToNull(request.getDescription()));
        item.setIcon(trimToNull(request.getIcon()));
        item.setRemarkRequired(Boolean.TRUE.equals(request.getRemarkRequired()));
        item.setIsFallback(false);
        item.setIsSystem(false);
        item.setSortNo(request.getSortNo() == null ? nextSortNo(type) : request.getSortNo());
        item.setStatus("ENABLED");
        item.setUpdatedBy(adminId);
        item.setVersion(0);
        dictItemMapper.insert(item);
        replaceScopes(item.getId(), scopes);
        DictItemVO vo = toVO(item);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction("CREATE").bizType("DICT_ITEM").bizId(item.getId())
                .targetName(item.getName()).searchText(item.getName())
                .after(vo).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.dict(type)));
        return vo;
    }

    @Override
    @Transactional
    public DictItemVO update(String dictType, Long id, DictItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        String type = requireType(dictType);
        if (DictTypes.isFinance(type)) {
            return finance.update(type, id, request);
        }
        DictItem item = requireItem(type, id);
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), item.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String name = validateName(type, request.getName());
        List<ScopeValue> scopes = validateScopes(type, request.getScopes());
        requireNameUnique(type, name, scopes, id);
        DictItemVO before = toVO(item);
        LocalDateTime now = BeijingTime.now();
        int rows = dictItemMapper.update(new DictItem(), Wrappers.<DictItem>lambdaUpdate()
                .eq(DictItem::getId, id)
                .eq(DictItem::getVersion, item.getVersion())
                .set(DictItem::getName, name)
                .set(DictItem::getDescription, trimToNull(request.getDescription()))
                .set(DictItem::getIcon, trimToNull(request.getIcon()))
                .set(DictItem::getRemarkRequired, Boolean.TRUE.equals(request.getRemarkRequired()))
                .set(DictItem::getSortNo, request.getSortNo() == null ? item.getSortNo() : request.getSortNo())
                .set(DictItem::getUpdatedBy, adminId)
                .set(DictItem::getUpdatedAt, now)
                .set(DictItem::getVersion, item.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        replaceScopes(id, scopes);
        DictItem updated = dictItemMapper.selectById(id);
        DictItemVO vo = toVO(updated);
        // 只记有变化的字段（不再整份写 VO：编号、版本号、使用次数等不是修改内容）
        AdminDiffs.Diff diff = AdminDiffs.diff()
                .field("name", "名称", before.getName(), vo.getName())
                .field("description", "说明", before.getDescription(), vo.getDescription())
                .field("icon", "图标", before.getIcon(), vo.getIcon())
                .field("remark_required", "需填写备注", Boolean.TRUE.equals(before.getRemarkRequired()),
                        Boolean.TRUE.equals(vo.getRemarkRequired()))
                .field("scopes", "适用范围", scopeText(before), scopeText(vo))
                .field("sort_no", "排序", before.getSortNo(), vo.getSortNo());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction("UPDATE").bizType("DICT_ITEM").bizId(id)
                .targetName(name).searchText(name).before(diff.before()).after(diff.after()).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.dict(type)));
        return vo;
    }

    @Override
    @Transactional
    public void order(String dictType, ItemOrderRequest request) {
        AdminContext.requireAdminId();
        String type = requireType(dictType);
        if (DictTypes.isFinance(type)) {
            finance.order(type, request);
            return;
        }
        List<DictItem> items = dictItemMapper.selectList(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .in(DictItem::getId, request.getItemIds()));
        if (items.size() != request.getItemIds().size()) {
            throw BaseErrors.notFound();
        }
        if (DictTypes.SYMPTOM.equals(type)) {
            if (request.getCategoryId() == null) {
                throw BaseErrors.field("category_id", "请选择症状适用品类");
            }
            Map<Long, List<DictItemScope>> scopes = support.scopesByItem(request.getItemIds());
            for (Long itemId : request.getItemIds()) {
                boolean inCategory = (scopes.getOrDefault(itemId, List.of())).stream()
                        .anyMatch(scope -> DictScopes.CATEGORY.equals(scope.getScopeType())
                                && Objects.equals(scope.getCategoryId(), request.getCategoryId()));
                if (!inCategory) {
                    throw BaseErrors.field("item_ids", "所选症状不属于该品类");
                }
            }
        }
        int sort = 0;
        for (Long id : request.getItemIds()) {
            dictItemMapper.update(new DictItem(), Wrappers.<DictItem>lambdaUpdate()
                    .eq(DictItem::getId, id)
                    .eq(DictItem::getDictType, type)
                    .set(DictItem::getSortNo, ++sort));
        }
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction("SORT").bizType("DICT_ITEM")
                .targetName(DictTypes.displayName(type)).searchText(type)
                .after(Map.of("item_ids", request.getItemIds())).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.dict(type)));
    }

    @Override
    @Transactional
    public DictItemVO changeStatus(String dictType, Long id, DictStatusRequest request) {
        Long adminId = AdminContext.requireAdminId();
        String type = requireType(dictType);
        if (DictTypes.isFinance(type)) {
            return finance.changeStatus(type, id, request);
        }
        DictItem item = requireItem(type, id);
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), item.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw BaseErrors.field("status", "请选择正确的状态");
        }
        LocalDateTime now = BeijingTime.now();
        String action;
        if ("DISABLED".equals(status)) {
            if ("DISABLED".equals(item.getStatus())) {
                throw BaseErrors.stateChanged("该字典项已停用");
            }
            if (Boolean.TRUE.equals(item.getIsFallback())) {
                throw BaseErrors.notAllowed("‘" + item.getName() + "’是兜底选项，不能停用");
            }
            if (Boolean.TRUE.equals(item.getIsSystem())) {
                throw BaseErrors.notAllowed("‘" + item.getName() + "’是系统选项，不能停用");
            }
            if (!StringUtils.hasText(request.getReason())) {
                throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_DISABLE);
            }
            int rows = dictItemMapper.update(new DictItem(), Wrappers.<DictItem>lambdaUpdate()
                    .eq(DictItem::getId, id)
                    .eq(DictItem::getVersion, item.getVersion())
                    .set(DictItem::getStatus, "DISABLED")
                    .set(DictItem::getDisableReason, request.getReason())
                    .set(DictItem::getDisabledAt, now)
                    .set(DictItem::getDisabledBy, adminId)
                    .set(DictItem::getUpdatedBy, adminId)
                    .set(DictItem::getUpdatedAt, now)
                    .set(DictItem::getVersion, item.getVersion() + 1));
            if (rows == 0) {
                throw BaseErrors.versionConflict();
            }
            action = "DISABLE";
        } else {
            if ("ENABLED".equals(item.getStatus())) {
                throw BaseErrors.stateChanged("该字典项已启用");
            }
            int rows = dictItemMapper.update(new DictItem(), Wrappers.<DictItem>lambdaUpdate()
                    .eq(DictItem::getId, id)
                    .eq(DictItem::getVersion, item.getVersion())
                    .set(DictItem::getStatus, "ENABLED")
                    .set(DictItem::getDisableReason, null)
                    .set(DictItem::getDisabledAt, null)
                    .set(DictItem::getDisabledBy, null)
                    .set(DictItem::getUpdatedBy, adminId)
                    .set(DictItem::getUpdatedAt, now)
                    .set(DictItem::getVersion, item.getVersion() + 1));
            if (rows == 0) {
                throw BaseErrors.versionConflict();
            }
            action = "ENABLE";
        }
        DictItem updated = dictItemMapper.selectById(id);
        DictItemVO vo = toVO(updated);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction(action).bizType("DICT_ITEM").bizId(id)
                .targetName(updated.getName()).searchText(updated.getName()).reason(request.getReason())
                .after(Map.of("status", status)).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.dict(type)));
        return vo;
    }

    // ==================== 校验与工具 ====================

    private String requireType(String dictType) {
        String type = DictTypes.normalize(dictType);
        if (!DictTypes.isKnown(type) && !DictTypes.isFinance(type)) {
            throw BaseErrors.notFound();
        }
        return type;
    }

    private DictItem requireItem(String type, Long id) {
        DictItem item = id == null ? null : dictItemMapper.selectById(id);
        if (item == null || !type.equals(item.getDictType())) {
            throw BaseErrors.notFound();
        }
        return item;
    }

    private String validateName(String type, String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw BaseErrors.field("name", "请填写名称");
        }
        String name = rawName.trim();
        int max = DictTypes.DEATH_REASON.equals(type) ? 6 : 10;
        if (name.length() > max) {
            throw BaseErrors.field("name", "名称最多 " + max + " 个字");
        }
        return name;
    }

    private List<ScopeValue> validateScopes(String type, List<DictItemSaveRequest.Scope> rawScopes) {
        List<DictItemSaveRequest.Scope> scopes = rawScopes == null ? List.of() : rawScopes;
        if (DictTypes.SPECIES_SCOPED.contains(type)) {
            if (scopes.isEmpty()) {
                throw BaseErrors.field("scopes", "请选择适用畜种");
            }
            List<ScopeValue> result = new ArrayList<>();
            for (DictItemSaveRequest.Scope scope : scopes) {
                String scopeType = scope.getScopeType() == null ? "" : scope.getScopeType().toUpperCase();
                if (!DictScopes.SPECIES.equals(scopeType)) {
                    throw BaseErrors.field("scopes", "请选择适用畜种");
                }
                String species;
                try {
                    species = Species.valueOf(scope.getSpecies().trim().toUpperCase()).name();
                } catch (RuntimeException e) {
                    throw BaseErrors.field("scopes", "请选择正确的畜种");
                }
                result.add(new ScopeValue(DictScopes.SPECIES, species, 0L));
            }
            return dedupe(result);
        }
        if (DictTypes.CATEGORY_SCOPED.contains(type)) {
            if (scopes.isEmpty()) {
                throw BaseErrors.field("scopes", "请选择适用品类");
            }
            List<ScopeValue> result = new ArrayList<>();
            for (DictItemSaveRequest.Scope scope : scopes) {
                String scopeType = scope.getScopeType() == null ? "" : scope.getScopeType().toUpperCase();
                if (!DictScopes.CATEGORY.equals(scopeType) || scope.getCategoryId() == null) {
                    throw BaseErrors.field("scopes", "请选择适用品类");
                }
                Category category = categoryMapper.selectById(scope.getCategoryId());
                if (category == null) {
                    throw BaseErrors.field("scopes", "请选择适用品类");
                }
                result.add(new ScopeValue(DictScopes.CATEGORY, "", category.getId()));
            }
            return dedupe(result);
        }
        if (!scopes.isEmpty()) {
            throw BaseErrors.field("scopes", "该字典不支持适用范围");
        }
        return List.of();
    }

    private List<ScopeValue> dedupe(List<ScopeValue> scopes) {
        Set<ScopeValue> set = new LinkedHashSet<>(scopes);
        return new ArrayList<>(set);
    }

    private void replaceScopes(Long itemId, List<ScopeValue> scopes) {
        scopeMapper.delete(Wrappers.<DictItemScope>lambdaQuery().eq(DictItemScope::getDictItemId, itemId));
        for (ScopeValue scope : scopes) {
            DictItemScope entity = new DictItemScope();
            entity.setDictItemId(itemId);
            entity.setScopeType(scope.scopeType());
            entity.setSpecies(scope.species());
            entity.setCategoryId(scope.categoryId());
            scopeMapper.insert(entity);
        }
    }

    private void requireNameUnique(String type, String name, List<ScopeValue> scopes, Long excludeId) {
        List<DictItem> sameName = dictItemMapper.selectList(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .eq(DictItem::getName, name));
        if (sameName.isEmpty()) {
            return;
        }
        Map<Long, List<DictItemScope>> existingScopes = support.scopesByItem(
                sameName.stream().map(DictItem::getId).toList());
        for (DictItem item : sameName) {
            if (Objects.equals(item.getId(), excludeId)) {
                continue;
            }
            List<DictItemScope> theirs = existingScopes.getOrDefault(item.getId(), List.of());
            if (overlaps(scopes, theirs)) {
                throw BaseErrors.duplicate("已有“" + name + "”");
            }
        }
    }

    private static boolean overlaps(List<ScopeValue> scopes, List<DictItemScope> theirs) {
        if (scopes.isEmpty() || theirs.isEmpty()) {
            // 无范围表示全部品类，与任何范围都重叠
            return true;
        }
        for (ScopeValue scope : scopes) {
            for (DictItemScope other : theirs) {
                if (Objects.equals(scope.scopeType(), other.getScopeType())
                        && Objects.equals(scope.species(), other.getSpecies())
                        && Objects.equals(scope.categoryId(), other.getCategoryId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String generateCode(String type) {
        String candidate;
        do {
            candidate = "C" + Long.toString(System.nanoTime(), 36).toUpperCase();
        } while (dictItemMapper.selectCount(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type).eq(DictItem::getCode, candidate)) > 0);
        return candidate;
    }

    private int nextSortNo(String type) {
        List<DictItem> list = dictItemMapper.selectList(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .orderByDesc(DictItem::getSortNo).last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getSortNo() == null ? 1 : list.get(0).getSortNo() + 1;
    }

    private DictItemVO toVO(DictItem item) {
        Map<Long, List<DictItemScope>> scopes = support.scopesByItem(List.of(item.getId()));
        return AdminBaseConvert.dictItem(item, scopes.getOrDefault(item.getId(), List.of()), categoryNames());
    }

    private Map<Long, String> categoryNames() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Category category : categoryMapper.selectList(Wrappers.lambdaQuery())) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    private static String normalizeSpecies(String species) {
        if (!StringUtils.hasText(species)) {
            return null;
        }
        return species.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 留痕里的适用范围：品类写名称，畜种写中文，没有范围记“全部” */
    private static String scopeText(DictItemVO vo) {
        List<String> parts = new ArrayList<>();
        for (DictScopeVO scope : vo.getScopes() == null ? List.<DictScopeVO>of() : vo.getScopes()) {
            if (scope.getCategoryName() != null) {
                parts.add(scope.getCategoryName());
            } else if (scope.getSpecies() != null) {
                parts.add(AdminBaseConvert.speciesText(scope.getSpecies()));
            }
        }
        return parts.isEmpty() ? "全部" : String.join("、", parts);
    }

    private record ScopeValue(String scopeType, String species, Long categoryId) {
    }
}

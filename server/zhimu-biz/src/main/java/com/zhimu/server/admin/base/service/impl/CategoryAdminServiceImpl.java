package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.event.BaseDataChangedEvent;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.request.CategorySaveRequest;
import com.zhimu.server.admin.base.service.CategoryAdminService;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.system.support.AdminDiffs;
import com.zhimu.server.admin.base.vo.CategoryVO;
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
import java.util.List;
import java.util.Objects;

/** ADM-D02 / D03 品类维护。 */
@Service
@RequiredArgsConstructor
public class CategoryAdminServiceImpl implements CategoryAdminService {

    private final CategoryMapper categoryMapper;
    private final BreedMapper breedMapper;
    private final BaseLogs logs;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CategoryVO create(CategorySaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        String name = request.getName().trim();
        String species = species(request.getSpecies());
        boolean breeding = Boolean.TRUE.equals(request.getIsBreeding());
        if (breeding && Species.POULTRY.name().equals(species)) {
            throw BaseErrors.field("is_breeding", "禽类不能设为种畜品类");
        }
        requireNameUnique(name, null);
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED";
        checkStatusReason(status, request.getDisableReason());
        LocalDateTime now = BeijingTime.now();
        Category category = new Category();
        category.setName(name);
        category.setSpecies(species);
        category.setCountUnit(countUnit(species));
        category.setIsBreeding(breeding);
        category.setSortNo(request.getSortNo() == null ? nextSortNo() : request.getSortNo());
        category.setStatus(status);
        category.setDisableReason("DISABLED".equals(status) ? request.getDisableReason() : null);
        category.setUpdatedBy(adminId);
        category.setVersion(0);
        categoryMapper.insert(category);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_CATEGORY").opAction("CREATE").bizType("CATEGORY").bizId(category.getId())
                .targetName(category.getName()).searchText(category.getName())
                .after(AdminBaseConvert.category(category)).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.category()));
        return withBreedCount(category);
    }

    @Override
    @Transactional
    public CategoryVO update(Long id, CategorySaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Category category = id == null ? null : categoryMapper.selectById(id);
        if (category == null) {
            throw BaseErrors.notFound();
        }
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), category.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String name = request.getName().trim();
        String species = species(request.getSpecies());
        if (!species.equals(category.getSpecies()) && hasBreeds(id)) {
            throw BaseErrors.field("species", "该品类已有品种，不能修改畜种");
        }
        boolean breeding = request.getIsBreeding() == null
                ? Boolean.TRUE.equals(category.getIsBreeding()) : request.getIsBreeding();
        if (breeding && Species.POULTRY.name().equals(species)) {
            throw BaseErrors.field("is_breeding", "禽类不能设为种畜品类");
        }
        requireNameUnique(name, id);
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : category.getStatus();
        String reason = request.getDisableReason();
        if ("DISABLED".equals(status) && !StringUtils.hasText(reason)) {
            reason = category.getDisableReason();
        }
        checkStatusReason(status, reason);
        LocalDateTime now = BeijingTime.now();
        int rows = categoryMapper.update(new Category(), Wrappers.<Category>lambdaUpdate()
                .eq(Category::getId, id)
                .eq(Category::getVersion, category.getVersion())
                .set(Category::getName, name)
                .set(Category::getSpecies, species)
                .set(Category::getCountUnit, countUnit(species))
                .set(Category::getIsBreeding, breeding)
                .set(Category::getSortNo, request.getSortNo() == null ? category.getSortNo() : request.getSortNo())
                .set(Category::getStatus, status)
                .set(Category::getDisableReason, "DISABLED".equals(status) ? reason : null)
                .set(Category::getUpdatedBy, adminId)
                .set(Category::getUpdatedAt, now)
                .set(Category::getVersion, category.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        String action = "DISABLED".equals(status) && !"DISABLED".equals(category.getStatus()) ? "DISABLE"
                : "ENABLED".equals(status) && !"ENABLED".equals(category.getStatus()) ? "ENABLE" : "UPDATE";
        // 只记有变化的字段，编码写中文
        AdminDiffs.Diff diff = AdminDiffs.diff()
                .field("name", "名称", category.getName(), name)
                .field("species", "畜种", AdminBaseConvert.speciesText(category.getSpecies()),
                        AdminBaseConvert.speciesText(species))
                .field("is_breeding", "种畜品类", Boolean.TRUE.equals(category.getIsBreeding()), breeding)
                .field("sort_no", "排序", category.getSortNo(),
                        request.getSortNo() == null ? category.getSortNo() : request.getSortNo())
                .field("status", "状态", AdminBaseConvert.statusText(category.getStatus()),
                        AdminBaseConvert.statusText(status))
                .field("disable_reason", "停用原因", category.getDisableReason(),
                        "DISABLED".equals(status) ? reason : null);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_CATEGORY").opAction(action).bizType("CATEGORY").bizId(id)
                .targetName(name).searchText(name).reason(reason)
                .before(diff.before()).after(diff.after()).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.category()));
        category.setName(name);
        category.setSpecies(species);
        category.setCountUnit(countUnit(species));
        category.setIsBreeding(breeding);
        category.setSortNo(request.getSortNo() == null ? category.getSortNo() : request.getSortNo());
        category.setStatus(status);
        category.setDisableReason("DISABLED".equals(status) ? reason : null);
        category.setVersion(category.getVersion() + 1);
        category.setUpdatedAt(now);
        return withBreedCount(category);
    }

    private CategoryVO withBreedCount(Category category) {
        CategoryVO vo = AdminBaseConvert.category(category);
        vo.setBreedCount(hasBreeds(category.getId())
                ? Math.toIntExact(breedMapper.selectCount(Wrappers.<Breed>lambdaQuery()
                .eq(Breed::getCategoryId, category.getId()))) : 0);
        return vo;
    }

    private boolean hasBreeds(Long categoryId) {
        return breedMapper.selectCount(Wrappers.<Breed>lambdaQuery()
                .eq(Breed::getCategoryId, categoryId)) > 0;
    }

    private void requireNameUnique(String name, Long excludeId) {
        List<Category> existing = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getName, name));
        for (Category category : existing) {
            if (!Objects.equals(category.getId(), excludeId)) {
                throw BaseErrors.duplicate("已有品类“" + name + "”");
            }
        }
    }

    private int nextSortNo() {
        List<Category> list = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByDesc(Category::getSortNo).last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getSortNo() == null ? 1 : list.get(0).getSortNo() + 1;
    }

    private static String species(String value) {
        try {
            return Species.valueOf(value.trim().toUpperCase()).name();
        } catch (RuntimeException e) {
            throw BaseErrors.field("species", "请选择正确的畜种");
        }
    }

    private static String countUnit(String species) {
        return Species.POULTRY.name().equals(species) ? "BIRD" : "HEAD";
    }

    private static void checkStatusReason(String status, String reason) {
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw BaseErrors.field("status", "请选择正确的启用状态");
        }
        if ("DISABLED".equals(status) && !StringUtils.hasText(reason)) {
            throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_DISABLE);
        }
        if (reason != null && reason.length() > 100) {
            throw BaseErrors.field("disable_reason", "停用原因最多 100 个字");
        }
    }
}

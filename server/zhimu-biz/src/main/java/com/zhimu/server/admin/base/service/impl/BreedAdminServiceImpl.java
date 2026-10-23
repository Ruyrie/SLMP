package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.event.BaseDataChangedEvent;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.request.BreedSaveRequest;
import com.zhimu.server.admin.base.service.BreedAdminService;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.vo.BreedVO;
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

/** ADM-D05 / D06 品种维护：繁育参数、后代默认品种、启停。 */
@Service
@RequiredArgsConstructor
public class BreedAdminServiceImpl implements BreedAdminService {

    private final BreedMapper breedMapper;
    private final CategoryMapper categoryMapper;
    private final BaseLogs logs;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BreedVO create(BreedSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Category category = requireEnabledCategory(request.getCategoryId());
        Values values = validate(request, category, null);
        requireNameUnique(category, request.getName().trim(), null);
        LocalDateTime now = BeijingTime.now();
        Breed breed = new Breed();
        breed.setCategoryId(category.getId());
        breed.setName(request.getName().trim());
        breed.setDescription(trimToNull(request.getDescription()));
        breed.setGestationDays(values.gestationDays);
        breed.setIncubationDays(values.incubationDays);
        breed.setPregnancyCheckDays(values.pregnancyCheckDays);
        breed.setOffspringBreedId(values.offspringBreedId);
        breed.setSortNo(request.getSortNo() == null ? nextSortNo(category.getId()) : request.getSortNo());
        breed.setStatus(values.status);
        breed.setDisableReason(values.disableReason);
        breed.setUpdatedBy(adminId);
        breed.setVersion(0);
        breedMapper.insert(breed);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_BREED").opAction("CREATE").bizType("BREED").bizId(breed.getId())
                .targetName(breed.getName()).searchText(breed.getName())
                .after(AdminBaseConvert.breed(breed, category)).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.category()));
        return AdminBaseConvert.breed(breed, category);
    }

    @Override
    @Transactional
    public BreedVO update(Long id, BreedSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Breed breed = id == null ? null : breedMapper.selectById(id);
        if (breed == null) {
            throw BaseErrors.notFound();
        }
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), breed.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        Category oldCategory = categoryMapper.selectById(breed.getCategoryId());
        Category category = requireEnabledCategory(request.getCategoryId());
        Values values = validate(request, category, breed);
        requireNameUnique(category, request.getName().trim(), id);
        boolean sensitiveChange = !Objects.equals(breed.getGestationDays(), values.gestationDays)
                || !Objects.equals(breed.getIncubationDays(), values.incubationDays)
                || !Objects.equals(breed.getPregnancyCheckDays(), values.pregnancyCheckDays);
        boolean disabling = "DISABLED".equals(values.status) && !"DISABLED".equals(breed.getStatus());
        if ((sensitiveChange || disabling) && !StringUtils.hasText(request.getChangeReason())) {
            throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_CHANGE);
        }
        LocalDateTime now = BeijingTime.now();
        int rows = breedMapper.update(new Breed(), Wrappers.<Breed>lambdaUpdate()
                .eq(Breed::getId, id)
                .eq(Breed::getVersion, breed.getVersion())
                .set(Breed::getCategoryId, category.getId())
                .set(Breed::getName, request.getName().trim())
                .set(Breed::getDescription, trimToNull(request.getDescription()))
                .set(Breed::getGestationDays, values.gestationDays)
                .set(Breed::getIncubationDays, values.incubationDays)
                .set(Breed::getPregnancyCheckDays, values.pregnancyCheckDays)
                .set(Breed::getOffspringBreedId, values.offspringBreedId)
                .set(Breed::getSortNo, request.getSortNo() == null ? breed.getSortNo() : request.getSortNo())
                .set(Breed::getStatus, values.status)
                .set(Breed::getDisableReason, values.disableReason)
                .set(Breed::getUpdatedBy, adminId)
                .set(Breed::getUpdatedAt, now)
                .set(Breed::getVersion, breed.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        String action = disabling ? "DISABLE"
                : "ENABLED".equals(values.status) && !"ENABLED".equals(breed.getStatus()) ? "ENABLE" : "UPDATE";
        logs.record(OperationLogEntry.builder()
                .opType("BASE_BREED").opAction(action).bizType("BREED").bizId(id)
                .targetName(request.getName().trim()).searchText(request.getName().trim())
                .reason(request.getChangeReason())
                .before(AdminBaseConvert.breed(breed, oldCategory)).build());
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.category()));
        breed.setCategoryId(category.getId());
        breed.setName(request.getName().trim());
        breed.setDescription(trimToNull(request.getDescription()));
        breed.setGestationDays(values.gestationDays);
        breed.setIncubationDays(values.incubationDays);
        breed.setPregnancyCheckDays(values.pregnancyCheckDays);
        breed.setOffspringBreedId(values.offspringBreedId);
        breed.setSortNo(request.getSortNo() == null ? breed.getSortNo() : request.getSortNo());
        breed.setStatus(values.status);
        breed.setDisableReason(values.disableReason);
        breed.setVersion(breed.getVersion() + 1);
        breed.setUpdatedAt(now);
        return AdminBaseConvert.breed(breed, category);
    }

    private Values validate(BreedSaveRequest request, Category category, Breed existing) {
        boolean poultry = Species.POULTRY.name().equals(category.getSpecies());
        Integer gestation = request.getGestationDays();
        Integer incubation = request.getIncubationDays();
        if (poultry) {
            if (gestation != null) {
                throw BaseErrors.field("gestation_days", "禽类不能填写妊娠期");
            }
            if (incubation == null) {
                throw BaseErrors.field("incubation_days", "请填写孵化期");
            }
            if (request.getPregnancyCheckDays() != null) {
                throw BaseErrors.field("pregnancy_check_days", "禽类不能填写待妊检天数");
            }
        } else {
            if (incubation != null) {
                throw BaseErrors.field("incubation_days", "非禽类不能填写孵化期");
            }
            if (Boolean.TRUE.equals(category.getIsBreeding()) && gestation == null) {
                throw BaseErrors.field("gestation_days", "请填写妊娠期");
            }
        }
        Integer pregnancy = request.getPregnancyCheckDays();
        if (gestation == null) {
            pregnancy = null;
        } else if (pregnancy == null) {
            pregnancy = existing != null && existing.getPregnancyCheckDays() != null
                    ? existing.getPregnancyCheckDays() : 21;
        }
        if (gestation != null && pregnancy != null && pregnancy >= gestation) {
            throw BaseErrors.field("pregnancy_check_days", "待妊检天数必须小于妊娠期");
        }
        Long offspringId = request.getOffspringBreedId();
        if (offspringId != null) {
            if (gestation == null && incubation == null) {
                throw BaseErrors.field("offspring_breed_id", "只有填写妊娠期或孵化期后才能设置后代默认品种");
            }
            Breed offspring = breedMapper.selectById(offspringId);
            if (offspring == null) {
                throw BaseErrors.field("offspring_breed_id", "后代默认品种不存在");
            }
            if (!"ENABLED".equals(offspring.getStatus())) {
                throw BaseErrors.field("offspring_breed_id", "后代默认品种须为启用品种");
            }
            Category offspringCategory = categoryMapper.selectById(offspring.getCategoryId());
            if (offspringCategory == null || !Objects.equals(offspringCategory.getSpecies(), category.getSpecies())) {
                throw BaseErrors.field("offspring_breed_id", "后代默认品种须与本品种同畜种");
            }
        }
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus()
                : existing == null ? "ENABLED" : existing.getStatus();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw BaseErrors.field("status", "请选择正确的启用状态");
        }
        String reason = StringUtils.hasText(request.getDisableReason())
                ? request.getDisableReason() : request.getChangeReason();
        if ("DISABLED".equals(status) && !StringUtils.hasText(reason)) {
            if (existing != null && "DISABLED".equals(existing.getStatus())) {
                reason = existing.getDisableReason();
            }
            if (!StringUtils.hasText(reason)) {
                throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_CHANGE);
            }
        }
        if ("ENABLED".equals(status)) {
            reason = null;
        }
        return new Values(gestation, incubation, pregnancy, offspringId, status, trimToNull(reason));
    }

    private Category requireEnabledCategory(Long categoryId) {
        Category category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null) {
            throw BaseErrors.field("category_id", "请选择所属品类");
        }
        if (!"ENABLED".equals(category.getStatus())) {
            throw BaseErrors.field("category_id", "请选择启用的品类");
        }
        return category;
    }

    private void requireNameUnique(Category category, String name, Long excludeId) {
        List<Breed> existing = breedMapper.selectList(Wrappers.<Breed>lambdaQuery()
                .eq(Breed::getCategoryId, category.getId())
                .eq(Breed::getName, name));
        for (Breed breed : existing) {
            if (!Objects.equals(breed.getId(), excludeId)) {
                throw BaseErrors.duplicate(category.getName() + "下已有品种“" + name + "”");
            }
        }
    }

    private int nextSortNo(Long categoryId) {
        List<Breed> list = breedMapper.selectList(Wrappers.<Breed>lambdaQuery()
                .eq(Breed::getCategoryId, categoryId)
                .orderByDesc(Breed::getSortNo).last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getSortNo() == null ? 1 : list.get(0).getSortNo() + 1;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Values(Integer gestationDays, Integer incubationDays, Integer pregnancyCheckDays,
                          Long offspringBreedId, String status, String disableReason) {
    }
}

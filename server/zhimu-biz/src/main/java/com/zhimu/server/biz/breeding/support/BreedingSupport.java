package com.zhimu.server.biz.breeding.support;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingRefMapper;
import com.zhimu.server.biz.breeding.vo.CapacityConfirmVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.CountUnit;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 模块05 共用的读取与校验：种畜 / 周期 / 栏舍 / 品种 / 批次 / 账号。
 * 模块之间只调用对方 api 包的 Service（模块04 的 PenQueryService、BatchQueryService，
 * 管理端-03 的 BreedQueryService、CategoryQueryService，模块01 的 AccountQueryService）。
 */
@Component
@RequiredArgsConstructor
public class BreedingSupport {

    private final BreedingAnimalMapper animalMapper;
    private final BreedingCycleMapper cycleMapper;
    private final BreedingRefMapper refMapper;
    private final PenQueryService penQueryService;
    private final BatchQueryService batchQueryService;
    private final BreedQueryService breedQueryService;
    private final CategoryQueryService categoryQueryService;
    private final AccountQueryService accountQueryService;

    public FarmContext.FarmMember viewer() {
        return FarmContext.require();
    }

    public Long farmId() {
        return FarmContext.requireFarmId();
    }

    public boolean managerView() {
        return FarmContext.require().isManagerView();
    }

    /** 按 ID 取本场种畜；不存在或跨场一律 40401 */
    public BreedingAnimal requireAnimal(Long animalId) {
        if (animalId == null) {
            throw BreedingErrors.notFound();
        }
        BreedingAnimal animal = animalMapper.selectById(animalId);
        if (animal == null) {
            throw BreedingErrors.notFound();
        }
        return animal;
    }

    public BreedingCycle requireCycle(Long cycleId) {
        if (cycleId == null) {
            throw BreedingErrors.notFound();
        }
        BreedingCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw BreedingErrors.notFound();
        }
        return cycle;
    }

    public BreedingCycle currentCycle(BreedingAnimal animal) {
        return animal.getCurrentCycleId() == null ? null : cycleMapper.selectById(animal.getCurrentCycleId());
    }

    public PenInfo pen(Long penId) {
        if (penId == null) {
            return null;
        }
        return penQueryService.getPens(List.of(penId)).get(penId);
    }

    public Map<Long, PenInfo> pens(Collection<Long> penIds) {
        if (penIds == null || penIds.isEmpty()) {
            return Map.of();
        }
        return penQueryService.getPens(penIds);
    }

    public String penName(Long penId) {
        PenInfo pen = pen(penId);
        return pen == null ? null : pen.penName();
    }

    public BreedQueryService.BreedInfo breed(Long breedId) {
        return breedId == null ? null : breedQueryService.info(breedId);
    }

    public Map<Long, BreedQueryService.BreedInfo> breeds(Collection<Long> breedIds) {
        Map<Long, BreedQueryService.BreedInfo> result = new LinkedHashMap<>();
        if (breedIds == null) {
            return result;
        }
        for (Long breedId : breedIds) {
            if (breedId != null && !result.containsKey(breedId)) {
                result.put(breedId, breedQueryService.info(breedId));
            }
        }
        return result;
    }

    public String breedName(Long breedId) {
        BreedQueryService.BreedInfo info = breed(breedId);
        return info == null ? null : info.name();
    }

    public CategoryInfo categoryOfBreed(Long breedId) {
        BreedQueryService.BreedInfo info = breed(breedId);
        if (info == null || info.categoryId() == null) {
            return null;
        }
        return categoryQueryService.info(info.categoryId());
    }

    /** 按品类 ID 取（孵化校验禽类用） */
    public CategoryInfo categoryQueryInfo(Long categoryId) {
        return categoryId == null ? null : categoryQueryService.info(categoryId);
    }

    public String unitOfBreed(Long breedId) {
        CategoryInfo category = categoryOfBreed(breedId);
        return unitText(category == null ? null : category.countUnit());
    }

    public static String unitText(String countUnit) {
        if (CountUnit.BIRD.name().equals(countUnit)) {
            return CountUnit.BIRD.getText();
        }
        return CountUnit.HEAD.getText();
    }

    public List<BatchInfo> activeBatches() {
        return batchQueryService.listActive(farmId());
    }

    public BatchInfo batchOrNull(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            return batchQueryService.requireBatch(batchId);
        } catch (BizException ex) {
            if (ex.getCode() == ErrorCode.NOT_FOUND.getCode()) {
                return null;
            }
            throw ex;
        }
    }

    public String batchNo(Long batchId) {
        BatchInfo batch = batchOrNull(batchId);
        return batch == null ? null : batch.batchNo();
    }

    /** 目标栏舍当前批次存栏（模块04 口径：只计在养批次） */
    public int penCurrentCount(Long penId) {
        if (penId == null) {
            return 0;
        }
        int total = 0;
        for (BatchInfo batch : activeBatches()) {
            if (batch.penIds() != null && batch.penIds().contains(penId)) {
                total += batch.currentCount() == null ? 0 : batch.currentCount();
            }
        }
        return total;
    }

    /** 种用批次所在栏舍：优先入栏栏舍，否则取当前有存栏的第一个栏舍 */
    public Long primaryPenOfBatch(BatchInfo batch) {
        if (batch == null) {
            return null;
        }
        if (batch.entryPenId() != null) {
            return batch.entryPenId();
        }
        if (batch.penIds() != null && !batch.penIds().isEmpty()) {
            return batch.penIds().get(0);
        }
        return null;
    }

    public UserBriefVO user(Long userId) {
        if (userId == null) {
            return null;
        }
        return users(List.of(userId)).get(userId);
    }

    /** 批量取登记人等账号姓名与在本场的角色（一次查询；不在本场时只回姓名） */
    public Map<Long, UserBriefVO> users(Collection<Long> userIds) {
        Map<Long, UserBriefVO> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return result;
        }
        Map<Long, AccountBrief> briefs = accountQueryService.listBriefs(distinct);
        if (briefs == null || briefs.isEmpty()) {
            return result;
        }
        Map<Long, String> roles = memberRoles();
        for (Long userId : distinct) {
            AccountBrief brief = briefs.get(userId);
            if (brief == null) {
                continue;
            }
            String role = roles.get(userId);
            result.put(userId, UserBriefVO.builder()
                    .userId(brief.userId())
                    .name(brief.name())
                    .role(role)
                    .roleText(roleText(role))
                    .build());
        }
        return result;
    }

    /** 本场在场成员的角色（App 的 registrant.role / role_text 按农场成员关系回显） */
    public Map<Long, String> memberRoles() {
        Map<Long, String> roles = new LinkedHashMap<>();
        Long farmId = FarmContext.farmId();
        if (farmId == null) {
            return roles;
        }
        List<BreedingMemberRow> rows = refMapper.memberRoles(farmId);
        if (rows == null) {
            return roles;
        }
        for (BreedingMemberRow row : rows) {
            if (row.getUserId() != null) {
                roles.putIfAbsent(row.getUserId(), row.getRole());
            }
        }
        return roles;
    }

    public static String roleText(String role) {
        if ("OWNER".equals(role)) {
            return "所有者";
        }
        if ("MANAGER".equals(role)) {
            return "场长";
        }
        if ("STAFF".equals(role)) {
            return "员工";
        }
        return null;
    }

    /**
     * 超容量校验（产仔、出雏共用）：现有批次存栏 + 新增数量 > 容量且未确认时返回 42206。
     * 栏舍不存在时跳过（模块04 的 PenQueryService 尚未覆盖该栏舍）。
     */
    public void checkCapacity(Long penId, String penName, String unit, int adding, boolean confirmed) {
        if (penId == null || adding <= 0) {
            return;
        }
        PenInfo pen = pen(penId);
        if (pen == null) {
            return;
        }
        int capacity = pen.capacity() == null ? 0 : pen.capacity();
        int current = penCurrentCount(penId);
        int after = current + adding;
        if (after <= capacity || confirmed) {
            return;
        }
        String name = penName == null ? pen.penName() : penName;
        String unitText = unit == null ? "头" : unit;
        int over = after - capacity;
        String message = "入栏后超过栏舍容量。" + name + "容量 " + formatNumber(capacity) + " " + unitText
                + "，现有 " + formatNumber(current) + " " + unitText
                + "，入栏后共 " + formatNumber(after) + " " + unitText
                + "，超出 " + formatNumber(over) + " " + unitText + "。";
        CapacityConfirmVO data = new CapacityConfirmVO();
        data.setNeedConfirm(true);
        data.setPenId(penId);
        data.setPenName(name);
        data.setUnit(unitText);
        data.setCapacity(capacity);
        data.setCurrentCount(current);
        data.setAfterCount(after);
        data.setOverCount(over);
        throw new BizException(ErrorCode.CAPACITY_CONFIRM, message, data);
    }

    public static String formatNumber(int value) {
        return NumberFormat.getIntegerInstance(Locale.CHINA).format(value);
    }
}

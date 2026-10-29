package com.zhimu.server.biz.herd.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.health.api.WithdrawalQueryService;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块04 查询公共支撑：栏舍/批次/品种/品类/人员名称、日龄与阶段、比率与单位。
 * 跨模块只调用 {@link FarmLookup}、{@link AccountQueryService}、{@link CategoryQueryService}、{@link BreedQueryService}
 * 等已提供的查询入口，不直接访问对方 Mapper。
 */
@Component
@RequiredArgsConstructor
public class HerdSupport {

    private final PenMapper penMapper;
    private final BatchMapper batchMapper;
    private final BatchPenMapper batchPenMapper;
    private final FarmLookup farmLookup;
    private final AccountQueryService accountQueryService;
    private final CategoryQueryService categoryQueryService;
    private final BreedQueryService breedQueryService;
    private final WithdrawalQueryService withdrawalQueryService;

    public FarmContext.FarmMember context() {
        return FarmContext.require();
    }

    public Long farmId() {
        return context().farmId();
    }

    public String farmName() {
        return context().farmName();
    }

    public Farm farm(Long farmId) {
        return farmLookup.requireFarm(farmId);
    }

    /** 模块08：本场休药中的批次 → 可出栏日期（休药结束日晚于今天） */
    public Map<Long, LocalDate> withdrawalReleaseDates(Long farmId) {
        Map<Long, LocalDate> dates = farmId == null ? null : withdrawalQueryService.releaseDatesOfFarm(farmId);
        return dates == null ? Map.of() : dates;
    }

    /** 休药剩余天数（今天到可出栏日期），不在休药期返回 null */
    public static Integer withdrawalDaysLeft(LocalDate releaseDate) {
        if (releaseDate == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(BeijingTime.today(), releaseDate);
        return days > 0 ? (int) days : null;
    }

    /** 本场全部栏舍，按编号升序 */
    public List<Pen> pensOfFarm(Long farmId) {
        return penMapper.selectList(Wrappers.<Pen>lambdaQuery()
                .eq(Pen::getFarmId, farmId)
                .orderByAsc(Pen::getPenCode));
    }

    public List<Pen> pensByIds(Collection<Long> penIds) {
        if (penIds == null || penIds.isEmpty()) {
            return List.of();
        }
        return penMapper.selectList(Wrappers.<Pen>lambdaQuery().in(Pen::getId, penIds).orderByAsc(Pen::getPenCode));
    }

    public List<Pen> pensVisibleTo(FarmContext.FarmMember member) {
        if (member.isStaff()) {
            return pensByIds(member.penIds());
        }
        return pensOfFarm(member.farmId());
    }

    public Map<Long, Pen> penMap(Collection<Long> penIds) {
        Map<Long, Pen> map = new LinkedHashMap<>();
        for (Pen pen : pensByIds(penIds)) {
            map.put(pen.getId(), pen);
        }
        return map;
    }

    public List<Batch> activeBatches(Long farmId) {
        return batchMapper.selectList(Wrappers.<Batch>lambdaQuery()
                .eq(Batch::getFarmId, farmId)
                .eq(Batch::getStatus, "ACTIVE")
                .orderByAsc(Batch::getId));
    }

    public List<Batch> batches(Long farmId, String status) {
        return batchMapper.selectList(Wrappers.<Batch>lambdaQuery()
                .eq(Batch::getFarmId, farmId)
                .eq(status != null && !"ALL".equalsIgnoreCase(status), Batch::getStatus, status)
                .orderByAsc(Batch::getId));
    }

    public long countBatches(Long farmId, String status) {
        return batchMapper.selectCount(Wrappers.<Batch>lambdaQuery()
                .eq(Batch::getFarmId, farmId)
                .eq(status != null && !"ALL".equalsIgnoreCase(status), Batch::getStatus, status));
    }

    public List<BatchPen> positivePensOfBatch(Long batchId) {
        return batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getBatchId, batchId)
                .gt(BatchPen::getStockCount, 0)
                .orderByAsc(BatchPen::getPenId));
    }

    public List<BatchPen> pensOfBatch(Long batchId) {
        return batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getBatchId, batchId)
                .orderByDesc(BatchPen::getStockCount)
                .orderByAsc(BatchPen::getPenId));
    }

    /** 某栏舍内有在栏数的批次分布行 */
    public List<BatchPen> positivePensOfBatchByPen(Long farmId, Long penId) {
        return batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getFarmId, farmId)
                .eq(BatchPen::getPenId, penId)
                .gt(BatchPen::getStockCount, 0)
                .orderByAsc(BatchPen::getId));
    }

    public Set<Long> penIdsOfBatch(Long batchId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (BatchPen row : positivePensOfBatch(batchId)) {
            ids.add(row.getPenId());
        }
        return ids;
    }

    /** 员工可见：当前在栏栏舍与负责栏舍有交集；已结束按最后所在栏舍 */
    public boolean visibleToStaff(Batch batch, FarmContext.FarmMember member) {
        if (!member.isStaff()) {
            return true;
        }
        if (member.penIds() == null || member.penIds().isEmpty()) {
            return false;
        }
        Set<Long> current = penIdsOfBatch(batch.getId());
        if (!current.isEmpty()) {
            return current.stream().anyMatch(member.penIds()::contains);
        }
        BatchPen last = lastPenOfBatch(batch.getId());
        return last != null && member.penIds().contains(last.getPenId());
    }

    public BatchPen lastPenOfBatch(Long batchId) {
        List<BatchPen> rows = batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getBatchId, batchId)
                .isNotNull(BatchPen::getLastOutDate)
                .orderByDesc(BatchPen::getLastOutDate)
                .orderByDesc(BatchPen::getId)
                .last("LIMIT 1"));
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        List<BatchPen> all = pensOfBatch(batchId);
        return all.isEmpty() ? null : all.get(0);
    }

    /** 基准日：在养取今天，已结束取结束日期 */
    public LocalDate baseDate(Batch batch) {
        if ("ENDED".equals(batch.getStatus()) && batch.getEndDate() != null) {
            return batch.getEndDate();
        }
        return BeijingTime.today();
    }

    public int dayAge(Batch batch) {
        int age = batch.getEntryAgeDays() == null ? 0 : batch.getEntryAgeDays();
        if (batch.getEntryDate() == null) {
            return age;
        }
        return age + (int) ChronoUnit.DAYS.between(batch.getEntryDate(), baseDate(batch));
    }

    public int daysRaised(Batch batch) {
        if (batch.getEntryDate() == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(batch.getEntryDate(), baseDate(batch));
    }

    /** 生长阶段：饲喂标准阶段表未接入前，按日龄的示例口径（猪 ≤70 天保育期，其后育肥期） */
    public String stageName(Batch batch) {
        CategoryInfo category = category(batch.getCategoryId());
        String species = category == null ? "PIG" : category.species();
        int age = dayAge(batch);
        if ("POULTRY".equals(species)) {
            if (age <= 14) {
                return "育雏期";
            }
            return age <= 35 ? "育成期" : "育肥期";
        }
        return age <= 70 ? "保育期" : "育肥期";
    }

    /** 阶段简称用于 breed_title：保育期 → 保育猪、育肥期 → 育肥猪；禽类/牛/羊用品种名 */
    public String breedTitle(Batch batch) {
        String breedName = breedName(batch.getBreedId());
        CategoryInfo category = category(batch.getCategoryId());
        if (category == null || !"PIG".equals(category.species())) {
            return breedName;
        }
        String stage = stageName(batch);
        return breedName + switch (stage) {
            case "保育期" -> "保育猪";
            case "育肥期" -> "育肥猪";
            default -> "";
        };
    }

    public String categoryName(Long categoryId) {
        CategoryInfo info = category(categoryId);
        return info == null ? "" : info.name();
    }

    public String breedName(Long breedId) {
        BreedQueryService.BreedInfo info = breed(breedId);
        return info == null ? "" : info.name();
    }

    public CategoryInfo category(Long categoryId) {
        return categoryId == null ? null : categoryQueryService.info(categoryId);
    }

    public BreedQueryService.BreedInfo breed(Long breedId) {
        return breedId == null ? null : breedQueryService.info(breedId);
    }

    public String farmUnit(Long farmId) {
        Farm farm = farmLookup.findFarm(farmId);
        if (farm == null) {
            return "HEAD";
        }
        CategoryInfo info = category(farm.getMainCategoryId());
        return info == null || info.countUnit() == null ? "HEAD" : info.countUnit();
    }

    public String unitText(String unit) {
        return "BIRD".equals(unit) ? "只" : "头";
    }

    /** 批次的数量单位文字：按批次品类（头 / 只），取不到时按养殖场主品类 */
    public String batchUnitText(Batch batch) {
        if (batch == null) {
            return "头";
        }
        CategoryInfo info = category(batch.getCategoryId());
        if (info != null && info.countUnitText() != null && !info.countUnitText().isBlank()) {
            return info.countUnitText();
        }
        return unitText(farmUnit(batch.getFarmId()));
    }

    /** 批次数量文字（千分位 + 品类单位），例：4,600 只 */
    public String countText(Batch batch, int count) {
        return groupDigits(count) + " " + batchUnitText(batch);
    }

    /** 千分位：4600 → 4,600 */
    public static String groupDigits(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    /** 死亡率：四位小数，分母为 0 返回 null */
    public static BigDecimal mortality(Integer deathCount, Integer entryCount) {
        if (entryCount == null || entryCount <= 0) {
            return null;
        }
        int death = deathCount == null ? 0 : deathCount;
        return BigDecimal.valueOf(death).divide(BigDecimal.valueOf(entryCount), 4, RoundingMode.HALF_UP);
    }

    /** 占用率等比率：四位小数，分母为 0 返回 null */
    public static BigDecimal rate(Integer numerator, Integer denominator) {
        if (denominator == null || denominator <= 0) {
            return null;
        }
        int value = numerator == null ? 0 : numerator;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    public String penTypeText(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "NURSERY" -> "保育舍";
            case "FATTENING" -> "育肥舍";
            case "SOW" -> "母猪舍";
            case "FARROWING" -> "产房";
            case "BROODING" -> "育雏舍";
            case "BROILER" -> "肉鸡舍";
            case "BREEDER" -> "种鸡舍";
            default -> code;
        };
    }

    /** 阶段简称（用于品类占比分组）：保育期 → 保育猪、育肥期 → 育肥猪；其他畜种用阶段名 */
    public String stageShortName(Batch batch) {
        CategoryInfo category = category(batch.getCategoryId());
        String stage = stageName(batch);
        if (category == null || !"PIG".equals(category.species())) {
            return stage;
        }
        return switch (stage) {
            case "保育期" -> "保育猪";
            case "育肥期" -> "育肥猪";
            default -> stage;
        };
    }

    /** 批次列表项（模块04 内部与品类视图共用） */
    public com.zhimu.server.biz.herd.vo.BatchListItemVO batchItem(Batch batch,
                                                                  List<BatchPen> penRows,
                                                                  Map<Long, Pen> penMap) {
        com.zhimu.server.biz.herd.vo.BatchListItemVO item = new com.zhimu.server.biz.herd.vo.BatchListItemVO();
        item.setBatchId(batch.getId());
        item.setBreedId(batch.getBreedId());
        item.setBatchNo(batch.getBatchNo());
        item.setBreedName(breedName(batch.getBreedId()));
        item.setCategoryName(categoryName(batch.getCategoryId()));
        item.setBreedTitle(breedTitle(batch));
        item.setEntryDate(BeijingTime.format(batch.getEntryDate()));
        item.setPurpose(batch.getPurpose());
        item.setSource(batch.getSource());
        item.setStatus(batch.getStatus());
        List<com.zhimu.server.biz.herd.vo.BatchPenItem> pens = new ArrayList<>();
        for (BatchPen row : penRows) {
            Pen pen = penMap.get(row.getPenId());
            com.zhimu.server.biz.herd.vo.BatchPenItem penItem = new com.zhimu.server.biz.herd.vo.BatchPenItem();
            penItem.setPenId(row.getPenId());
            penItem.setPenName(pen == null ? null : pen.getPenName());
            penItem.setStockCount(row.getStockCount());
            pens.add(penItem);
        }
        item.setPens(pens);
        item.setEntryAgeDays(batch.getEntryAgeDays());
        item.setDayAge(dayAge(batch));
        item.setCurrentCount(batch.getCurrentCount());
        item.setMortalityRate(mortality(batch.getDeathCount(), batch.getEntryCount()));
        item.setInWithdrawal("ACTIVE".equals(batch.getStatus())
                && withdrawalReleaseDates(batch.getFarmId()).containsKey(batch.getId()));
        item.setStageName(stageName(batch));
        item.setEndDate(BeijingTime.format(batch.getEndDate()));
        String unit = farmUnit(batch.getFarmId());
        item.setUnit(unit);
        item.setUnitText(unitText(unit));
        return item;
    }

    public String roleText(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "OWNER" -> "所有者";
            case "MANAGER" -> "场长";
            case "STAFF" -> "员工";
            default -> role;
        };
    }

    public AccountBrief account(Long userId) {
        return userId == null ? null : accountQueryService.getBrief(userId);
    }

    public String userName(Long userId) {
        AccountBrief brief = account(userId);
        return brief == null ? null : brief.name();
    }

    public Map<Long, String> userNames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        Map<Long, AccountBrief> briefs = accountQueryService.listBriefs(new ArrayList<>(userIds));
        briefs.forEach((id, brief) -> names.put(id, brief.name()));
        return names;
    }

    public UserBriefVO userBrief(Long userId, String role) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = account(userId);
        if (brief == null) {
            return null;
        }
        return UserBriefVO.builder()
                .userId(userId)
                .name(brief.name())
                .role(role)
                .roleText(roleText(role))
                .build();
    }

    /** 用户头像临时地址（栏舍负责员工等少量成员显示用），没有头像为 null */
    public String avatarUrl(Long userId) {
        return userId == null ? null : accountQueryService.avatarUrl(userId);
    }

    public static boolean isManager(FarmContext.FarmMember member) {
        return member.role() == FarmRole.OWNER || member.role() == FarmRole.MANAGER;
    }
}

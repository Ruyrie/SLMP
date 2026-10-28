package com.zhimu.server.biz.herd.service.impl;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.query.PenQuery;
import com.zhimu.server.biz.herd.request.PenCreateRequest;
import com.zhimu.server.biz.herd.request.PenStatusRequest;
import com.zhimu.server.biz.herd.request.PenUpdateRequest;
import com.zhimu.server.biz.herd.service.PenService;
import com.zhimu.server.biz.herd.support.HerdErrors;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.vo.ExecutorOptionVO;
import com.zhimu.server.biz.herd.vo.PenDetailVO;
import com.zhimu.server.biz.herd.vo.PenFormOptionsVO;
import com.zhimu.server.biz.herd.vo.PenOptionVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.OptionVO;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** API-L07、L08、L10、L25 查询实现。 */
@Service
@RequiredArgsConstructor
public class PenServiceImpl implements PenService {

    private static final Pattern PEN_CODE = Pattern.compile("^[A-Za-z0-9-]{1,10}$");

    private static final List<OptionVO> PIG_PEN_TYPES = List.of(
            OptionVO.of("NURSERY", "保育舍"),
            OptionVO.of("FATTENING", "育肥舍"),
            OptionVO.of("SOW", "母猪舍"),
            OptionVO.of("FARROWING", "产房"));

    private static final List<OptionVO> POULTRY_PEN_TYPES = List.of(
            OptionVO.of("BROODING", "育雏舍"),
            OptionVO.of("BROILER", "肉鸡舍"),
            OptionVO.of("BREEDER", "种鸡舍"));

    private static final List<OptionVO> RUMINANT_PEN_TYPES = List.of(
            OptionVO.of("FATTENING", "育肥舍"),
            OptionVO.of("SOW", "母畜舍"),
            OptionVO.of("FARROWING", "产房"));

    private final HerdSupport support;
    private final FarmLookup farmLookup;
    private final BaseDataQueryService baseDataQueryService;
    private final PenMapper penMapper;
    private final OperationLogRecorder operationLogRecorder;

    @Override
    public PenFormOptionsVO formOptions() {
        FarmContext.FarmMember member = support.context();
        Farm farm = support.farm(member.farmId());
        CategoryInfo main = support.category(farm.getMainCategoryId());
        String species = main == null ? null : main.species();

        PenFormOptionsVO vo = new PenFormOptionsVO();
        vo.setPenTypes(penTypesOf(species));
        List<PenFormOptionsVO.CategoryOption> categories = new ArrayList<>();
        for (CategoryInfo category : baseDataQueryService.listEnabledCategories()) {
            if (species != null && !species.equals(category.species())) {
                continue;
            }
            PenFormOptionsVO.CategoryOption option = new PenFormOptionsVO.CategoryOption();
            option.setCategoryId(category.id());
            option.setCategoryName(category.name());
            option.setCountUnit(category.countUnit());
            option.setCountUnitText(category.countUnitText());
            option.setIsBreeding(category.isBreeding());
            categories.add(option);
        }
        vo.setCategories(categories);
        return vo;
    }

    @Override
    public List<PenOptionVO> options(PenQuery query) {
        FarmContext.FarmMember member = support.context();
        String keyword = trim(query.getKeyword());
        Map<Long, List<Long>> penCategories = activeCategoriesByPen(member.farmId());
        String unit = support.farmUnit(member.farmId());

        List<PenOptionVO> result = new ArrayList<>();
        // 员工只返回自己负责的栏舍（App 健康模块消毒登记等页面以员工身份调用 usage = OPTIONS）
        for (Pen pen : support.pensVisibleTo(member)) {
            if (!"ENABLED".equals(pen.getStatus())) {
                continue;
            }
            if (query.getExcludePenId() != null && query.getExcludePenId().equals(pen.getId())) {
                continue;
            }
            if (query.getPenType() != null && !query.getPenType().isBlank()
                    && !query.getPenType().equals(pen.getPenType())) {
                continue;
            }
            if (keyword != null && !contains(pen.getPenName(), keyword) && !contains(pen.getPenCode(), keyword)) {
                continue;
            }
            PenOptionVO vo = new PenOptionVO();
            vo.setPenId(pen.getId());
            vo.setPenCode(pen.getPenCode());
            vo.setPenName(pen.getPenName());
            vo.setPenType(pen.getPenType());
            vo.setStockCount(pen.getStockCount());
            vo.setCapacity(pen.getCapacity());
            vo.setUnitText(support.unitText(unit));
            boolean conflict = query.getCategoryId() != null
                    && penCategories.getOrDefault(pen.getId(), List.of()).stream()
                    .anyMatch(id -> !query.getCategoryId().equals(id));
            vo.setDisabled(conflict);
            if (conflict) {
                vo.setHint("已有其他品类批次在栏");
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public PenDetailVO detail(Long penId) {
        FarmContext.FarmMember member = support.context();
        Pen pen = requirePen(member.farmId(), penId);
        return toDetail(pen, member);
    }

    @Override
    public List<ExecutorOptionVO> executors(Long penId) {
        FarmContext.FarmMember member = support.context();
        requirePen(member.farmId(), penId);
        Map<Long, FarmMember> members = new HashMap<>();
        for (FarmMember row : farmLookup.activeMembers(member.farmId())) {
            members.put(row.getId(), row);
        }
        Set<Long> userIds = new LinkedHashSet<>();
        List<ExecutorOptionVO> result = new ArrayList<>();
        ExecutorOptionVO me = new ExecutorOptionVO();
        me.setUserId(member.userId());
        String myName = support.userName(member.userId());
        me.setName(myName);
        me.setDisplayName(myName + "（我）");
        me.setRole(member.role().name());
        me.setIsMe(true);
        result.add(me);
        userIds.add(member.userId());
        for (MemberPen row : farmLookup.pensOfFarm(member.farmId())) {
            if (!penId.equals(row.getPenId()) || userIds.contains(row.getUserId())) {
                continue;
            }
            FarmMember staffMember = members.get(row.getMemberId());
            if (staffMember == null) {
                continue;
            }
            String name = support.userName(row.getUserId());
            if (name == null) {
                continue;
            }
            ExecutorOptionVO option = new ExecutorOptionVO();
            option.setUserId(row.getUserId());
            option.setName(name);
            option.setDisplayName(name);
            option.setRole(staffMember.getRole());
            option.setIsMe(false);
            result.add(option);
            userIds.add(row.getUserId());
        }
        return result;
    }

    /** 当前养殖场的栏舍，否则 40401 */
    public Pen requirePen(Long farmId, Long penId) {
        if (penId == null) {
            throw com.zhimu.server.biz.herd.support.HerdErrors.notFound();
        }
        Pen pen = support.pensOfFarm(farmId).stream()
                .filter(row -> penId.equals(row.getId()))
                .findFirst()
                .orElseThrow(com.zhimu.server.biz.herd.support.HerdErrors::notFound);
        FarmContext.FarmMember member = support.context();
        if (member.isStaff() && !member.canAccessPen(penId)) {
            throw com.zhimu.server.biz.herd.support.HerdErrors.penForbidden();
        }
        return pen;
    }

    /** 栏舍详情用于新增 / 修改 / 停用 / 启用后的返回（写接口复用） */
    public PenDetailVO toDetail(Pen pen, FarmContext.FarmMember member) {
        String unit = support.farmUnit(pen.getFarmId());
        CategoryInfo category = support.category(pen.getCategoryId());
        PenDetailVO vo = new PenDetailVO();
        vo.setPenId(pen.getId());
        vo.setCategoryId(pen.getCategoryId());
        vo.setPenCode(pen.getPenCode());
        vo.setPenName(pen.getPenName());
        vo.setPenType(pen.getPenType());
        vo.setPenTypeText(support.penTypeText(pen.getPenType()));
        vo.setCategoryName(category == null ? "" : category.name());
        vo.setCapacity(pen.getCapacity());
        vo.setStockCount(pen.getStockCount());
        vo.setRemainingCapacity(Math.max(0, pen.getCapacity() - pen.getStockCount()));
        vo.setOverCapacityCount(Math.max(0, pen.getStockCount() - pen.getCapacity()));
        vo.setVersion(pen.getVersion());
        vo.setOccupancyRate(HerdSupport.rate(pen.getStockCount(), pen.getCapacity()));
        vo.setStatus(pen.getStatus());
        boolean breeding = category != null && Boolean.TRUE.equals(category.isBreeding());
        vo.setIsBreedingPen(breeding);
        String displayStatus = displayStatus(pen, breeding);
        vo.setDisplayStatus(displayStatus);
        vo.setDisplayStatusText(switch (displayStatus) {
            case PenDetailVO.STATUS_DISABLED -> "已停用";
            case PenDetailVO.STATUS_EMPTY -> "空栏";
            default -> "在用";
        });
        List<PenDetailVO.PenBatchItem> batches = batchesInPen(pen);
        vo.setBatches(batches);
        boolean canDisable = "ENABLED".equals(pen.getStatus()) && pen.getStockCount() != null && pen.getStockCount() == 0;
        vo.setCanDisable(canDisable);
        vo.setCanEnable(PenDetailVO.STATUS_DISABLED.equals(pen.getStatus()));
        if (!canDisable && "ENABLED".equals(pen.getStatus())) {
            int count = batches.size();
            if (count > 0) {
                vo.setDisableBlockReason("栏舍里还有 " + count + " 个在栏批次，全部转出或出栏后才能停用");
            }
        }
        if (pen.getDisabledAt() != null) {
            vo.setDisabledAt(BeijingTime.format(pen.getDisabledAt()));
        }
        vo.setStaff(staffOfPen(pen));
        vo.setUnitText(support.unitText(unit));
        return vo;
    }

    private List<PenDetailVO.PenBatchItem> batchesInPen(Pen pen) {
        List<PenDetailVO.PenBatchItem> items = new ArrayList<>();
        Map<Long, Batch> batches = new LinkedHashMap<>();
        for (Batch batch : support.activeBatches(pen.getFarmId())) {
            batches.put(batch.getId(), batch);
        }
        for (BatchPen row : support.positivePensOfBatchByPen(pen.getFarmId(), pen.getId())) {
            Batch batch = batches.get(row.getBatchId());
            if (batch == null) {
                continue;
            }
            PenDetailVO.PenBatchItem item = new PenDetailVO.PenBatchItem();
            item.setBatchId(batch.getId());
            item.setBatchNo(batch.getBatchNo());
            item.setBreedTitle(support.breedTitle(batch));
            item.setDayAge(support.dayAge(batch));
            item.setQtyInPen(row.getStockCount());
            item.setInWithdrawal(support.withdrawalReleaseDates(pen.getFarmId()).containsKey(batch.getId()));
            items.add(item);
        }
        return items;
    }

    private List<PenDetailVO.PenStaff> staffOfPen(Pen pen) {
        Map<Long, FarmMember> members = new LinkedHashMap<>();
        for (FarmMember row : farmLookup.activeMembers(pen.getFarmId())) {
            members.put(row.getId(), row);
        }
        List<PenDetailVO.PenStaff> result = new ArrayList<>();
        for (MemberPen row : farmLookup.pensOfFarm(pen.getFarmId())) {
            if (!pen.getId().equals(row.getPenId())) {
                continue;
            }
            FarmMember staffMember = members.get(row.getMemberId());
            if (staffMember == null) {
                continue;
            }
            UserBriefVO brief = support.userBrief(row.getUserId(), staffMember.getRole());
            if (brief != null) {
                brief.setAvatarUrl(support.avatarUrl(row.getUserId()));
                result.add(PenDetailVO.PenStaff.of(brief, staffMember.getId()));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public PenDetailVO create(PenCreateRequest request) {
        FarmContext.FarmMember member = support.context();
        String code = request.getPenCode() == null ? "" : request.getPenCode().trim().toUpperCase();
        if (code.isEmpty()) {
            throw HerdErrors.fieldInvalid("pen_code", "请填写栏舍编号");
        }
        if (!PEN_CODE.matcher(code).matches()) {
            throw HerdErrors.fieldInvalid("pen_code", "栏舍编号最多 10 位，只能包含字母、数字和短横线");
        }
        if (penMapper.countByCode(member.farmId(), code) > 0) {
            throw HerdErrors.fieldInvalid("pen_code", "本场已有编号为 " + code + " 的栏舍");
        }
        Validated validated = validate(request.getPenName(), request.getPenType(), request.getCategoryId(),
                request.getCapacity());
        Pen pen = new Pen();
        pen.setFarmId(member.farmId());
        pen.setPenCode(code);
        pen.setPenName(validated.name());
        pen.setPenType(validated.penType());
        pen.setCategoryId(request.getCategoryId());
        pen.setCapacity(request.getCapacity());
        pen.setStockCount(0);
        pen.setStatus(PenDetailVO.STATUS_ENABLED);
        pen.setVersion(0);
        pen.setUpdatedBy(member.userId());
        penMapper.insert(pen);
        Map<String, Object> created = penSnapshot(pen);
        created.put("pen_code", logField("栏舍编号", code));
        logPen("CREATE", pen, null, created);
        return toDetail(pen, member);
    }

    @Override
    @Transactional
    public PenDetailVO update(Long penId, PenUpdateRequest request) {
        FarmContext.FarmMember member = support.context();
        Pen pen = requirePen(member.farmId(), penId);
        requireVersion(pen.getVersion(), request.getVersion());
        Validated validated = validate(request.getPenName(), request.getPenType(), request.getCategoryId(),
                request.getCapacity());
        if (Objects.equals(pen.getPenName(), validated.name())
                && Objects.equals(pen.getPenType(), validated.penType())
                && Objects.equals(pen.getCategoryId(), request.getCategoryId())
                && Objects.equals(pen.getCapacity(), request.getCapacity())) {
            return toDetail(pen, member);
        }
        Map<String, Object> before = penSnapshot(pen);
        pen.setPenName(validated.name());
        pen.setPenType(validated.penType());
        pen.setCategoryId(request.getCategoryId());
        pen.setCapacity(request.getCapacity());
        pen.setUpdatedBy(member.userId());
        if (penMapper.updateById(pen) == 0) {
            throw HerdErrors.versionConflict();
        }
        logPen("UPDATE", pen, before, penSnapshot(pen));
        return toDetail(pen, member);
    }

    @Override
    @Transactional
    public PenDetailVO disable(Long penId, PenStatusRequest request) {
        FarmContext.FarmMember member = support.context();
        Pen pen = requirePen(member.farmId(), penId);
        requireVersion(pen.getVersion(), request.getVersion());
        if (PenDetailVO.STATUS_DISABLED.equals(pen.getStatus())) {
            throw HerdErrors.stateChanged("栏舍状态已变化，列表已刷新");
        }
        int activeBatches = support.positivePensOfBatchByPen(member.farmId(), penId).size();
        if ((pen.getStockCount() != null && pen.getStockCount() > 0) || activeBatches > 0) {
            throw HerdErrors.penNotEmpty(Math.max(activeBatches, 1));
        }
        pen.setStatus(PenDetailVO.STATUS_DISABLED);
        pen.setDisabledAt(BeijingTime.now());
        pen.setDisabledBy(member.userId());
        pen.setUpdatedBy(member.userId());
        if (penMapper.updateById(pen) == 0) {
            throw HerdErrors.versionConflict();
        }
        logPen("DISABLE", pen, Map.of("status", logField("栏舍状态", "启用")),
                Map.of("status", logField("栏舍状态", "停用")));
        return toDetail(pen, member);
    }

    @Override
    @Transactional
    public PenDetailVO enable(Long penId, PenStatusRequest request) {
        FarmContext.FarmMember member = support.context();
        Pen pen = requirePen(member.farmId(), penId);
        requireVersion(pen.getVersion(), request.getVersion());
        if (PenDetailVO.STATUS_ENABLED.equals(pen.getStatus())) {
            throw HerdErrors.stateChanged("栏舍状态已变化，列表已刷新");
        }
        pen.setStatus(PenDetailVO.STATUS_ENABLED);
        pen.setDisabledAt(null);
        pen.setDisabledBy(null);
        pen.setUpdatedBy(member.userId());
        if (penMapper.updateById(pen) == 0) {
            throw HerdErrors.versionConflict();
        }
        logPen("ENABLE", pen, Map.of("status", logField("栏舍状态", "停用")),
                Map.of("status", logField("栏舍状态", "启用")));
        return toDetail(pen, member);
    }

    private static List<OptionVO> penTypesOf(String species) {
        if (species == null) {
            return PIG_PEN_TYPES;
        }
        return switch (species) {
            case "POULTRY" -> POULTRY_PEN_TYPES;
            case "CATTLE", "SHEEP" -> RUMINANT_PEN_TYPES;
            default -> PIG_PEN_TYPES;
        };
    }

    private record Validated(String name, String penType) {
    }

    private Validated validate(String penName, String penType, Long categoryId, Integer capacity) {
        String name = penName == null ? "" : penName.trim();
        if (name.isEmpty()) {
            throw HerdErrors.fieldInvalid("pen_name", "请填写栏舍名称");
        }
        if (name.codePointCount(0, name.length()) > 20) {
            throw HerdErrors.fieldInvalid("pen_name", "栏舍名称最多 20 个字");
        }
        String type = penType == null ? "" : penType.trim().toUpperCase();
        if (type.isEmpty()) {
            throw HerdErrors.fieldInvalid("pen_type", "请选择栏舍类型");
        }
        boolean known = penTypesOf(null).stream().anyMatch(option -> option.getValue().equals(type))
                || PIG_PEN_TYPES.stream().anyMatch(option -> option.getValue().equals(type))
                || POULTRY_PEN_TYPES.stream().anyMatch(option -> option.getValue().equals(type))
                || RUMINANT_PEN_TYPES.stream().anyMatch(option -> option.getValue().equals(type));
        if (!known) {
            throw HerdErrors.fieldInvalid("pen_type", "请选择栏舍类型");
        }
        if (categoryId == null) {
            throw HerdErrors.fieldInvalid("category_id", "请选择适用品类");
        }
        CategoryInfo category = support.category(categoryId);
        if (category == null || !category.enabled()) {
            throw HerdErrors.fieldInvalid("category_id", "请选择适用品类");
        }
        if (capacity == null || capacity < 1 || capacity > 999_999) {
            throw HerdErrors.fieldInvalid("capacity", "容量需为大于 0 的整数");
        }
        return new Validated(name, type);
    }

    private static void requireVersion(Integer current, Integer requested) {
        if (requested == null || !requested.equals(current)) {
            throw HerdErrors.versionConflict();
        }
    }

    /** 对称记录可编辑字段；显示文字随操作保存，避免后续字典变更改写历史。 */
    private Map<String, Object> penSnapshot(Pen pen) {
        CategoryInfo category = support.category(pen.getCategoryId());
        String unit = category == null ? null : category.countUnitText();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pen_name", logField("栏舍名称", pen.getPenName()));
        snapshot.put("pen_type", logField("栏舍类型", support.penTypeText(pen.getPenType())));
        snapshot.put("category_id", logField("适用品类", category == null ? "未知品类" : category.name()));
        snapshot.put("capacity", logField("容量", pen.getCapacity() == null ? null
                : pen.getCapacity() + (unit == null || unit.isBlank() ? "" : " " + unit)));
        return snapshot;
    }

    private static Map<String, Object> logField(String name, String display) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("display", display);
        return field;
    }

    private void logPen(String action, Pen pen, Object before, Object after) {
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .opType("PEN")
                .opAction(action)
                .bizType("PEN")
                .bizId(pen.getId())
                .targetName(pen.getPenName())
                .farmId(pen.getFarmId())
                .before(before)
                .after(after)
                .build());
    }

    private Map<Long, List<Long>> activeCategoriesByPen(Long farmId) {
        Map<Long, Batch> batches = new LinkedHashMap<>();
        for (Batch batch : support.activeBatches(farmId)) {
            batches.put(batch.getId(), batch);
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (Batch batch : batches.values()) {
            for (BatchPen row : support.positivePensOfBatch(batch.getId())) {
                result.computeIfAbsent(row.getPenId(), key -> new ArrayList<>()).add(batch.getCategoryId());
            }
        }
        return result;
    }

    private static String displayStatus(Pen pen, boolean breeding) {
        if ("DISABLED".equals(pen.getStatus())) {
            return PenDetailVO.STATUS_DISABLED;
        }
        if (breeding) {
            return "IN_USE";
        }
        return pen.getStockCount() != null && pen.getStockCount() == 0 ? PenDetailVO.STATUS_EMPTY : "IN_USE";
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }
}

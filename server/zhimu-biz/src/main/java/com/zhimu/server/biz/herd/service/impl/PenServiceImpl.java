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
import com.zhimu.server.biz.herd.query.PenQuery;
import com.zhimu.server.biz.herd.service.PenService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** API-L07、L08、L10、L25 查询实现。 */
@Service
@RequiredArgsConstructor
public class PenServiceImpl implements PenService {

    private static final List<OptionVO> PEN_TYPES = List.of(
            OptionVO.of("NURSERY", "保育舍"),
            OptionVO.of("FATTENING", "育肥舍"),
            OptionVO.of("SOW", "母猪舍"),
            OptionVO.of("FARROWING", "产房"),
            OptionVO.of("BROODING", "育雏舍"),
            OptionVO.of("BROILER", "肉鸡舍"),
            OptionVO.of("BREEDER", "种鸡舍"));

    private final HerdSupport support;
    private final FarmLookup farmLookup;
    private final BaseDataQueryService baseDataQueryService;

    @Override
    public PenFormOptionsVO formOptions() {
        FarmContext.FarmMember member = support.context();
        Farm farm = support.farm(member.farmId());
        CategoryInfo main = support.category(farm.getMainCategoryId());
        String species = main == null ? null : main.species();

        PenFormOptionsVO vo = new PenFormOptionsVO();
        vo.setPenTypes(PEN_TYPES);
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
        for (Pen pen : support.pensOfFarm(member.farmId())) {
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
            item.setInWithdrawal(false);
            items.add(item);
        }
        return items;
    }

    private List<UserBriefVO> staffOfPen(Pen pen) {
        Map<Long, FarmMember> members = new LinkedHashMap<>();
        for (FarmMember row : farmLookup.activeMembers(pen.getFarmId())) {
            members.put(row.getId(), row);
        }
        List<UserBriefVO> result = new ArrayList<>();
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
                result.add(brief);
            }
        }
        return result;
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

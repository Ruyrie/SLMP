package com.zhimu.server.biz.health.support;

import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.query.BatchPenStockRow;
import com.zhimu.server.biz.health.query.FarmMemberRow;
import com.zhimu.server.biz.health.query.PenResponsibleRow;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 模块08 的公共支撑：当前养殖场与角色、批次与栏舍访问（员工 40304）、成员与负责员工姓名、字典名称。
 */
@Component
@RequiredArgsConstructor
public class HealthSupport {

    private final HealthReferenceMapper referenceMapper;
    private final BatchQueryService batchQueryService;
    private final PenQueryService penQueryService;
    private final DictQueryService dictQueryService;
    private final CategoryQueryService categoryQueryService;
    private final com.zhimu.server.biz.stock.api.DrugStockPort drugStockPort;

    public FarmContext.FarmMember member() {
        return FarmContext.require();
    }

    public Long farmId() {
        return FarmContext.requireFarmId();
    }

    public Long userId() {
        return UserContext.requireUserId();
    }

    public boolean managerView() {
        FarmContext.FarmMember member = FarmContext.get();
        return member == null || member.isManagerView();
    }

    public boolean staffView() {
        return FarmContext.isStaff();
    }

    /** 所有者、场长专用操作，员工返回 40303 */
    public void requireManager(String message) {
        if (!managerView()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, message);
        }
    }

    public LocalDate today() {
        return BeijingTime.today();
    }

    // ===== 成员与负责员工 =====

    public List<FarmMemberRow> members() {
        return referenceMapper.selectMembers(farmId());
    }

    public Map<Long, String> memberNames() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (FarmMemberRow row : members()) {
            if (row.getUserId() != null) {
                map.put(row.getUserId(), row.getName());
            }
        }
        return map;
    }

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        String name = memberNames().get(userId);
        return name == null ? referenceMapper.selectUserName(userId) : name;
    }

    public List<PenResponsibleRow> penResponsibles() {
        return referenceMapper.selectPenResponsibles(farmId());
    }

    /** 栏舍负责员工的用户 ID（去重、保持顺序） */
    public List<Long> penResponsibleUserIds(Long penId) {
        List<Long> ids = new ArrayList<>();
        for (PenResponsibleRow row : penResponsibles()) {
            if (row.getPenId() != null && row.getPenId().equals(penId) && row.getUserId() != null
                    && !ids.contains(row.getUserId())) {
                ids.add(row.getUserId());
            }
        }
        return ids;
    }

    /** 栏舍负责员工姓名，例如“李明”；没有负责员工返回 null */
    public String penResponsibleNames(Long penId) {
        List<String> names = new ArrayList<>();
        for (Long id : penResponsibleUserIds(penId)) {
            String name = userName(id);
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join("、", names);
    }

    /** 执行人显示文字：负责员工；没有负责员工时为“你”与场长 / 所有者 */
    public String executorText(Long penId) {
        String names = penResponsibleNames(penId);
        if (names != null) {
            return names;
        }
        List<String> others = new ArrayList<>();
        Long self = UserContext.userId();
        for (FarmMemberRow row : members()) {
            boolean manager = "OWNER".equals(row.getRole()) || "MANAGER".equals(row.getRole());
            if (manager && row.getUserId() != null && !row.getUserId().equals(self)
                    && row.getName() != null && !others.contains(row.getName())) {
                others.add(row.getName());
            }
        }
        return others.isEmpty() ? "你" : "你和" + String.join("、", others);
    }

    public boolean noPenStaff(Long penId) {
        return penResponsibleUserIds(penId).isEmpty();
    }

    /** 无负责员工时为默认执行人：不返回，由前端选本人 */
    public Long defaultExecutorId(Long penId) {
        List<Long> ids = penResponsibleUserIds(penId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 计划负责人文字：“李明负责” / “所有者和场长负责” */
    public String responsibleText(Long penId) {
        String names = penResponsibleNames(penId);
        return names == null ? "所有者和场长负责" : names + "负责";
    }

    // ===== 批次与栏舍 =====

    public BatchInfo requireBatch(Long batchId) {
        return batchQueryService.requireBatch(batchId);
    }

    /** 批次须存在、在养，且属于当前养殖场 */
    public BatchInfo requireActiveBatch(Long batchId) {
        BatchInfo batch = batchQueryService.requireBatch(batchId);
        if (!batch.active()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "批次已结束，未执行的计划已自动取消。");
        }
        return batch;
    }

    /** 员工须负责批次的任一在栏栏舍，否则 40304 */
    public void checkBatchAccess(BatchInfo batch) {
        FarmContext.FarmMember member = member();
        if (!member.isStaff()) {
            return;
        }
        for (Long penId : batch.penIds()) {
            if (member.canAccessPen(penId)) {
                return;
            }
        }
        throw new BizException(ErrorCode.PEN_FORBIDDEN, "你没有这个栏舍的查看权限");
    }

    public void checkPenAccess(Long penId) {
        FarmContext.checkPen(penId);
    }

    public PenInfo requirePen(Long penId) {
        PenInfo pen = penQueryService.getPens(List.of(penId)).get(penId);
        if (pen == null || !pen.farmId().equals(farmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return pen;
    }

    public Map<Long, PenInfo> pens(Collection<Long> penIds) {
        if (penIds == null || penIds.isEmpty()) {
            return Map.of();
        }
        return penQueryService.getPens(new LinkedHashSet<>(penIds));
    }

    public List<PenInfo> enabledPens() {
        List<PenInfo> pens = new ArrayList<>();
        for (PenInfo pen : penQueryService.listPens(farmId(), false)) {
            if (pen.enabled()) {
                pens.add(pen);
            }
        }
        return pens;
    }

    /** 本场启用中的栏舍 */
    public boolean penEnabled(Long penId) {
        PenInfo pen = penQueryService.getPens(List.of(penId)).get(penId);
        return pen != null && pen.enabled() && pen.farmId().equals(farmId());
    }

    public List<BatchPenStockRow> activeBatchPens() {
        return referenceMapper.selectActiveBatchPens(farmId());
    }

    /** 主栏舍：在栏数最多的栏舍，相同取先入栏；没有存栏时取入栏栏舍 */
    public Long mainPenId(BatchInfo batch) {
        Long best = null;
        int bestStock = -1;
        for (BatchPenStockRow row : activeBatchPens()) {
            if (row.getBatchId() == null || !row.getBatchId().equals(batch.batchId())) {
                continue;
            }
            int stock = row.getStockCount() == null ? 0 : row.getStockCount();
            if (stock > bestStock) {
                bestStock = stock;
                best = row.getPenId();
            }
        }
        if (best != null) {
            return best;
        }
        if (batch.penIds() != null && !batch.penIds().isEmpty()) {
            return batch.penIds().get(0);
        }
        return batch.entryPenId();
    }

    /** 批次在某栏舍的在栏数 */
    public int batchPenStock(Long batchId, Long penId) {
        BatchPenStockRow row = referenceMapper.selectBatchPenStock(batchId, penId);
        return row == null || row.getStockCount() == null ? 0 : row.getStockCount();
    }

    /** 批次在某一栏舍内的在栏批次（按栏舍登记用药） */
    public List<BatchPenStockRow> activeBatchesOfPen(Long penId) {
        List<BatchPenStockRow> rows = new ArrayList<>();
        for (BatchPenStockRow row : activeBatchPens()) {
            if (row.getPenId() != null && row.getPenId().equals(penId)) {
                rows.add(row);
            }
        }
        return rows;
    }

    public String batchNo(Long batchId) {
        if (batchId == null) {
            return null;
        }
        return batchQueryService.requireBatch(batchId).batchNo();
    }

    public String penName(Long penId) {
        if (penId == null) {
            return null;
        }
        PenInfo pen = penQueryService.getPens(List.of(penId)).get(penId);
        return pen == null ? referenceMapper.selectPenName(penId) : pen.penName();
    }

    // ===== 基础数据 =====

    public String dictName(String dictType, String code) {
        if (code == null) {
            return null;
        }
        String name = dictQueryService.nameOf(dictType, code);
        return name == null ? code : name;
    }

    public CategoryInfo category(Long categoryId) {
        return categoryId == null ? null : categoryQueryService.info(categoryId);
    }

    /** 品类对应畜种（PIG / POULTRY / CATTLE / SHEEP），取不到返回 PIG */
    public String species(Long categoryId) {
        CategoryInfo info = category(categoryId);
        return info == null || info.species() == null ? "PIG" : info.species();
    }

    // ===== 库存批号 =====

    /** 可选批号；模块09 抛出的业务异常原样上抛 */
    public List<com.zhimu.server.biz.stock.api.DrugLotOption> lotsFor(Long drugId, String purpose) {
        if (drugId == null) {
            return List.of();
        }
        List<com.zhimu.server.biz.stock.api.DrugLotOption> lots =
                drugStockPort.lotsFor(farmId(), drugId, purpose, null);
        return lots == null ? List.of() : lots;
    }

    public com.zhimu.server.biz.stock.api.DrugLotOption findLot(
            List<com.zhimu.server.biz.stock.api.DrugLotOption> lots, Long lotId) {
        if (lotId == null) {
            return null;
        }
        for (com.zhimu.server.biz.stock.api.DrugLotOption lot : lots) {
            if (lotId.equals(lot.lotId())) {
                return lot;
            }
        }
        return null;
    }

    /** 批号不可用时的错误：过期 / 停用 42204，结存不足 42201 */
    public void assertLotUsable(com.zhimu.server.biz.stock.api.DrugLotOption lot, BigDecimal needQty,
                                String drugName, String unitText) {
        if (lot == null) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE, "该批号已过期，请换一个批号");
        }
        String status = lot.lotStatus();
        if ("EXPIRED".equals(status)) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE, "该批号已过期，请换一个批号");
        }
        if ("RECALLED".equals(status) || "BANNED".equals(status)) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE, "该批号已被平台停用，请换一个批号");
        }
        BigDecimal stockQty = lot.stock() == null ? null : lot.stock().getValue();
        if (needQty != null && stockQty != null && stockQty.compareTo(needQty) < 0) {
            String unit = unitText == null ? "" : unitText;
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "批号 " + lot.lotNo() + " 结存 " + HealthFormat.numberText(stockQty) + " " + unit
                            + "，不够本次 " + HealthFormat.numberText(needQty) + " " + unit);
        }
    }

    /** 没有可用批号时的提示（按角色） */
    public String noLotText(String drugName) {
        String name = drugName == null ? "该药品" : drugName;
        return staffView()
                ? name + "没有可用的库存批号。" + HealthCodes.STAFF_NO_LOT_SUFFIX
                : name + "没有可用的库存批号。请先登记入库。";
    }
}

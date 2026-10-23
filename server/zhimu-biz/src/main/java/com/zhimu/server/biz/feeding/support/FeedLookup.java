package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.mapper.FeedRefMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块06 的跨模块只读查询与权限校验：
 * 批次（模块04 BatchQueryService）、栏舍（PenQueryService）、品种（BreedQueryService）、
 * 用户姓名（模块01 AccountQueryService）、饲料（FeedStockPort）、成员/负责栏舍（FeedRefMapper）。
 */
@Component
@RequiredArgsConstructor
public class FeedLookup {

    private final BatchQueryService batchQueryService;
    private final PenQueryService penQueryService;
    private final BreedQueryService breedQueryService;
    private final AccountQueryService accountQueryService;
    private final FeedStockPort feedStockPort;
    private final FeedRefMapper feedRefMapper;

    public FarmContext.FarmMember context() {
        return FarmContext.require();
    }

    public Long farmId() {
        return FarmContext.requireFarmId();
    }

    /** 批次存在且属于当前养殖场，否则 40401 */
    public BatchInfo requireOwnedBatch(Long batchId) {
        if (batchId == null) {
            throw FeedErrors.notFound();
        }
        BatchInfo batch;
        try {
            batch = batchQueryService.requireBatch(batchId);
        } catch (RuntimeException e) {
            throw FeedErrors.notFound();
        }
        if (batch == null || !farmId().equals(batch.farmId())) {
            throw FeedErrors.notFound();
        }
        return batch;
    }

    /** 批次在养，否则 40903（批次已结束） */
    public BatchInfo requireActiveBatch(Long batchId) {
        BatchInfo batch = requireOwnedBatch(batchId);
        if (!batch.active()) {
            throw FeedErrors.stateChanged("批次已结束，不能修改饲喂方案");
        }
        return batch;
    }

    /** 员工必须有该批次任一栏舍的权限，否则 40304 */
    public void checkBatchScope(BatchInfo batch) {
        if (!FarmContext.isStaff()) {
            return;
        }
        Set<Long> pens = context().penIds();
        if (pens == null || batch.penIds() == null || batch.penIds().stream().noneMatch(pens::contains)) {
            throw FeedErrors.penForbidden();
        }
    }

    /** 员工必须有该栏舍的权限，否则 40304 */
    public void checkPenScope(Long penId) {
        if (FarmContext.isStaff() && !context().canAccessPen(penId)) {
            throw FeedErrors.penForbidden();
        }
    }

    /** 养殖场在养批次；无数据返回空列表 */
    public List<BatchInfo> requireActiveBatches(Long farmId) {
        List<BatchInfo> list = batchQueryService.listActive(farmId);
        return list == null ? List.of() : list;
    }

    /** 有在养批次的养殖场 ID；定时任务没有当前养殖场上下文时使用 */
    public List<Long> activeFarmIds() {
        List<Long> ids = feedRefMapper.activeFarmIds();
        return ids == null ? List.of() : ids;
    }

    /** 养殖场批次（含已结束），用于称重批次选择与对比批次；不按 X-Farm-Id 二次过滤 */
    public List<FeedBatchRow> farmBatches(boolean includeEnded) {
        List<FeedBatchRow> rows = feedRefMapper.farmBatches(farmId(), includeEnded ? 1 : 0);
        return rows == null ? List.of() : rows;
    }

    public FeedBatchRow batchBasics(Long batchId) {
        return batchId == null ? null : feedRefMapper.batchBasics(batchId);
    }

    public java.math.BigDecimal entryAvgWeight(Long batchId) {
        FeedBatchRow row = batchBasics(batchId);
        return row == null ? null : row.getEntryAvgWeight();
    }

    public List<Long> batchPenIds(Long batchId) {
        List<Long> ids = feedRefMapper.batchPenIds(batchId);
        return ids == null ? List.of() : ids;
    }

    /** 员工是否能看到这些栏舍中的任一栏舍 */
    public boolean staffCanSeePens(Collection<Long> penIds) {
        if (!FarmContext.isStaff()) {
            return true;
        }
        Set<Long> own = context().penIds();
        return own != null && penIds != null && penIds.stream().anyMatch(own::contains);
    }

    /** 批次在当前有在栏数的栏舍 */
    public List<FeedBatchPenRow> batchPens(BatchInfo batch) {
        List<FeedBatchPenRow> rows = feedRefMapper.batchPens(batch.batchId());
        if (rows != null && !rows.isEmpty()) {
            return rows;
        }
        List<FeedBatchPenRow> fallback = new ArrayList<>();
        int count = batch.currentCount() == null ? 0 : batch.currentCount();
        if (batch.penIds() != null) {
            for (Long penId : batch.penIds()) {
                FeedBatchPenRow row = new FeedBatchPenRow();
                row.setPenId(penId);
                row.setStockCount(count);
                fallback.add(row);
            }
        }
        return fallback;
    }

    public PenInfo pen(Long penId) {
        Map<Long, PenInfo> pens = penQueryService.getPens(List.of(penId));
        return pens.get(penId);
    }

    public Map<Long, PenInfo> pens(Collection<Long> penIds) {
        if (penIds == null || penIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PenInfo> result = penQueryService.getPens(penIds);
        return result == null ? Map.of() : result;
    }

    public String penName(Long penId) {
        PenInfo pen = pen(penId);
        return pen == null ? null : pen.penName();
    }

    /** 批次栏舍名称，按 pen_code 排序后用顿号连接 */
    public String penNames(Collection<Long> penIds) {
        if (penIds == null || penIds.isEmpty()) {
            return null;
        }
        Map<Long, PenInfo> pens = pens(penIds);
        List<PenInfo> ordered = new ArrayList<>(pens.values());
        ordered.sort(Comparator.comparing(PenInfo::penCode, Comparator.nullsLast(Comparator.naturalOrder())));
        StringBuilder sb = new StringBuilder();
        for (PenInfo pen : ordered) {
            if (!sb.isEmpty()) {
                sb.append('、');
            }
            sb.append(pen.penName());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    public String breedName(Long breedId) {
        if (breedId == null) {
            return null;
        }
        BreedQueryService.BreedInfo info = breedQueryService.info(breedId);
        return info == null ? null : info.name();
    }

    public FeedStockPort.FeedBrief feed(Long feedId) {
        return feedStockPort.getFeed(farmId(), feedId);
    }

    public List<FeedStockPort.FeedBrief> feeds() {
        List<FeedStockPort.FeedBrief> list = feedStockPort.listFeeds(farmId());
        return list == null ? List.of() : list;
    }

    /** 本场在场成员（所有者、场长、员工），带姓名与角色 */
    public List<UserBriefVO> memberOptions() {
        List<FeedMemberRow> rows = feedRefMapper.activeMembers(farmId());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = rows.stream().map(FeedMemberRow::getUserId).toList();
        Map<Long, AccountBrief> accounts = accountQueryService.listBriefs(userIds);
        List<UserBriefVO> result = new ArrayList<>();
        for (FeedMemberRow row : rows) {
            result.add(toBrief(row, accounts == null ? null : accounts.get(row.getUserId())));
        }
        return result;
    }

    public Map<Long, UserBriefVO> memberBriefs(Collection<Long> userIds) {
        return memberBriefs(farmId(), userIds);
    }

    /** 指定养殖场的成员姓名与角色（定时任务没有当前养殖场上下文时使用） */
    public Map<Long, UserBriefVO> memberBriefs(Long farmId, Collection<Long> userIds) {
        Map<Long, UserBriefVO> result = new LinkedHashMap<>();
        if (farmId == null || userIds == null || userIds.isEmpty()) {
            return result;
        }
        Set<Long> ids = new LinkedHashSet<>(userIds);
        Map<Long, AccountBrief> accounts = accountQueryService.listBriefs(ids);
        Map<Long, FeedMemberRow> rows = new LinkedHashMap<>();
        List<FeedMemberRow> members = feedRefMapper.activeMembers(farmId);
        if (members != null) {
            for (FeedMemberRow row : members) {
                rows.putIfAbsent(row.getUserId(), row);
            }
        }
        for (Long userId : ids) {
            AccountBrief account = accounts == null ? null : accounts.get(userId);
            FeedMemberRow row = rows.get(userId);
            UserBriefVO brief = new UserBriefVO();
            brief.setUserId(userId);
            brief.setName(account == null ? null : account.name());
            brief.setRole(row == null ? null : row.getRole());
            brief.setRoleText(roleText(row == null ? null : row.getRole()));
            result.put(userId, brief);
        }
        return result;
    }

    public UserBriefVO userBrief(Long userId) {
        if (userId == null) {
            return null;
        }
        return memberBriefs(List.of(userId)).get(userId);
    }

    public UserBriefVO currentUserBrief() {
        FarmContext.FarmMember member = context();
        AccountBrief account = accountQueryService.getBrief(member.userId());
        UserBriefVO brief = new UserBriefVO();
        brief.setUserId(member.userId());
        brief.setName(account == null ? null : account.name());
        brief.setRole(member.role().name());
        brief.setRoleText(roleText(member.role().name()));
        return brief;
    }

    public boolean isActiveMember(Long userId) {
        if (userId == null) {
            return false;
        }
        List<FeedMemberRow> rows = feedRefMapper.activeMembers(farmId());
        return rows != null && rows.stream().anyMatch(row -> userId.equals(row.getUserId()));
    }

    /** 栏舍第一位负责员工（t_member_pen 按创建顺序），没有返回 null */
    public Long firstAssignee(Long penId) {
        return firstAssignee(farmId(), penId);
    }

    /** 指定养殖场的栏舍第一位负责员工（定时任务没有当前养殖场上下文时使用） */
    public Long firstAssignee(Long farmId, Long penId) {
        if (farmId == null || penId == null) {
            return null;
        }
        List<FeedMemberPenRow> rows = feedRefMapper.memberPens(farmId);
        if (rows == null) {
            return null;
        }
        List<FeedMemberRow> members = feedRefMapper.activeMembers(farmId);
        Set<Long> active = new LinkedHashSet<>();
        if (members != null) {
            for (FeedMemberRow row : members) {
                active.add(row.getUserId());
            }
        }
        for (FeedMemberPenRow row : rows) {
            if (penId.equals(row.getPenId()) && active.contains(row.getUserId())) {
                return row.getUserId();
            }
        }
        return null;
    }

    public void requireManager(String message) {
        if (!context().isManagerView()) {
            throw FeedErrors.roleForbidden(message);
        }
    }

    private static UserBriefVO toBrief(FeedMemberRow row, AccountBrief account) {
        UserBriefVO brief = new UserBriefVO();
        brief.setUserId(row.getUserId());
        brief.setName(account == null ? null : account.name());
        brief.setRole(row.getRole());
        brief.setRoleText(roleText(row.getRole()));
        return brief;
    }

    public static String roleText(String role) {
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

    public static String roleOf(FarmContext.FarmMember member) {
        return member == null ? null : member.role().name();
    }

    public static FarmRole roleOrStaff(FarmContext.FarmMember member) {
        return member == null ? FarmRole.STAFF : member.role();
    }
}

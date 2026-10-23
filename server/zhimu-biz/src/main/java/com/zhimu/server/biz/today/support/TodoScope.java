package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办可见范围（2.7.4）：所有者/场长看全场全部类别；员工只看负责栏舍的作业类待办，
 * 不含预产（FARROWING）与预计出雏（HATCHING），栏舍调整后立即按新范围生效。
 * 全部养殖场视角（scope = ALL）取本人为成员且状态 NORMAL 的养殖场，每场按本人角色分别套用。
 */
@Component
@RequiredArgsConstructor
public class TodoScope {

    private final FarmMemberLoader memberLoader;
    private final FarmQueryService farmQueryService;

    /** 一次查询的可见范围 */
    public record Scope(Long currentFarmId, boolean all, List<Long> farmIds,
                        Map<Long, FarmContext.FarmMember> members) {

        public FarmContext.FarmMember member(Long farmId) {
            return members.get(farmId);
        }
    }

    /** 解析 scope（CURRENT / ALL）；CURRENT 使用拦截器写入的上下文 */
    public Scope resolve(Long userId, String scope) {
        FarmContext.FarmMember current = FarmContext.require();
        if (!"ALL".equalsIgnoreCase(scope)) {
            return new Scope(current.farmId(), false, List.of(current.farmId()),
                    Map.of(current.farmId(), current));
        }
        List<Long> ids = new ArrayList<>();
        Map<Long, FarmContext.FarmMember> members = new LinkedHashMap<>();
        for (FarmBriefVO farm : farmQueryService.listFarmsOfUser(userId)) {
            if (!FarmStatus.NORMAL.name().equals(farm.getStatus())) {
                continue;
            }
            FarmContext.FarmMember member = memberLoader.load(farm.getFarmId(), userId);
            if (member != null) {
                ids.add(farm.getFarmId());
                members.put(farm.getFarmId(), member);
            }
        }
        return new Scope(current.farmId(), true, ids, members);
    }

    /** 按待办所属养殖场加载成员；不是成员返回 null（调用方决定 40302 / 40401） */
    public FarmContext.FarmMember memberFor(Long farmId, Long userId) {
        return farmId == null ? null : memberLoader.load(farmId, userId);
    }

    /** 详情、处理与打开消息时的成员校验：非成员 40302，冻结 40306 */
    public FarmContext.FarmMember requireMember(Long farmId, Long userId) {
        FarmContext.FarmMember member = memberFor(farmId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FARM_MEMBER);
        }
        if (member.farmStatus() == FarmStatus.FROZEN) {
            throw new BizException(ErrorCode.FARM_FROZEN,
                    member.farmName() + "已被平台冻结，暂时不能使用。原因见消息中心");
        }
        return member;
    }

    /** 写操作前的养殖场状态校验：停用、待审核、已驳回都不能处理待办（2.10） */
    public void checkWritable(FarmContext.FarmMember member) {
        FarmStatus status = member.farmStatus();
        if (status == FarmStatus.NORMAL) {
            return;
        }
        if (status == FarmStatus.FROZEN) {
            throw new BizException(ErrorCode.FARM_FROZEN,
                    member.farmName() + "已被平台冻结，暂时不能使用。原因见消息中心");
        }
        throw new BizException(ErrorCode.FARM_STATUS_FORBIDDEN,
                member.farmName() + "已停用，重新启用后才能处理待办");
    }

    /** 员工是否可见该待办 */
    public boolean canView(Todo todo, FarmContext.FarmMember member) {
        if (member == null) {
            return false;
        }
        if (!member.isStaff()) {
            return true;
        }
        if (!"WORK".equals(todo.getCategory())) {
            return false;
        }
        if ("FARROWING".equals(todo.getTodoType()) || "HATCHING".equals(todo.getTodoType())) {
            return false;
        }
        return todo.getPenId() != null && member.canAccessPen(todo.getPenId());
    }

    /** 员工不可见的提示：栏舍不在负责范围时 40304，其余按 40401 */
    public void checkViewable(Todo todo, FarmContext.FarmMember member) {
        if (!canView(todo, member)) {
            if (member != null && member.isStaff() && todo.getPenId() != null
                    && !member.canAccessPen(todo.getPenId())) {
                throw new BizException(ErrorCode.PEN_FORBIDDEN);
            }
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }
}

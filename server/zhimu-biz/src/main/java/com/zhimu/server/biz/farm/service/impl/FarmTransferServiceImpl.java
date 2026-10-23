package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.request.CreateTransferRequest;
import com.zhimu.server.biz.farm.service.FarmTransferService;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNameLock;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.farm.vo.TransferAcceptVO;
import com.zhimu.server.biz.farm.vo.TransferCandidateVO;
import com.zhimu.server.biz.farm.vo.TransferPageVO;
import com.zhimu.server.biz.farm.vo.TransferVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API-F28–F32 所有权转让 */
@Service
@RequiredArgsConstructor
public class FarmTransferServiceImpl implements FarmTransferService {

    private final FarmTransferMapper transferMapper;
    private final FarmMemberMapper memberMapper;
    private final FarmMapper farmMapper;
    private final FarmLookup lookup;
    private final FarmAssembler assembler;
    private final FarmNameGuard nameGuard;
    private final FarmNameLock nameLock;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final FarmNotify notify;

    @Override
    public TransferPageVO page(Long farmId, Long userId) {
        Viewer viewer = viewer(farmId, userId);
        requireOwner(viewer);
        requireReadable(viewer);
        TransferPageVO vo = new TransferPageVO();
        vo.setFarm(assembler.transferFarm(viewer.farm()));
        List<FarmMember> managers = lookup.activeMembers(farmId).stream()
                .filter(member -> "MANAGER".equals(member.getRole())).toList();
        Map<Long, AccountBrief> accounts = assembler.accounts(
                managers.stream().map(FarmMember::getUserId).toList());
        List<TransferCandidateVO> candidates = new ArrayList<>();
        for (FarmMember manager : managers) {
            candidates.add(assembler.candidate(manager, accounts.get(manager.getUserId())));
        }
        vo.setCandidates(candidates);
        FarmTransfer pending = assembler.pendingTransfer(farmId);
        vo.setPending(assembler.transfer(viewer.farm(), pending));
        return vo;
    }

    @Override
    @Transactional
    public TransferVO create(Long farmId, Long userId, CreateTransferRequest request) {
        Viewer viewer = viewer(farmId, userId);
        requireOwner(viewer);
        if (viewer.status() == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(viewer.farm().getName());
        }
        if (!FarmStatuses.readable(viewer.status())) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
        if (request.getToMemberId() == null) {
            throw com.zhimu.server.biz.farm.support.FarmValidations.invalid("to_member_id",
                    "请选择接收所有权的场长");
        }
        FarmMember target = lookup.findActiveMemberById(farmId, request.getToMemberId());
        if (target == null || !"MANAGER".equals(target.getRole())) {
            throw FarmErrors.stateChanged("该成员已不是本场场长，请刷新后重新选择");
        }
        AccountBrief targetAccount = assembler.account(target.getUserId());
        String targetName = targetAccount == null ? "对方" : targetAccount.name();
        FarmTransfer pending = assembler.pendingTransfer(farmId);
        if (pending != null) {
            throw FarmErrors.stateChanged("已向" + targetName + "发送转让请求，等待对方确认。确认前你仍是"
                    + viewer.farm().getName() + "的所有者。");
        }
        return nameLock.call(target.getUserId(), () -> {
            if (nameGuard.exists(target.getUserId(), viewer.farm().getName(), null)) {
                throw new com.zhimu.server.common.error.BizException(ErrorCode.NAME_DUPLICATE,
                        targetName + "名下已有同名养殖场“" + viewer.farm().getName()
                                + "”，请先修改养殖场名称再转让");
            }
            LocalDateTime now = BeijingTime.now();
            FarmTransfer transfer = new FarmTransfer();
            transfer.setFarmId(farmId);
            transfer.setFromUserId(userId);
            transfer.setToUserId(target.getUserId());
            transfer.setToMemberId(target.getId());
            transfer.setStatus("PENDING");
            transfer.setPendingFlag(true);
            transfer.setExpiresAt(now.plusDays(7));
            transfer.setRequestId(RequestContext.idempotencyKey());
            transfer.setCreatedBy(userId);
            TenantSkip.run(() -> transferMapper.insert(transfer));
            notify.transferCreated(viewer.farm(), transfer,
                    assembler.account(userId) == null ? "养殖场所有者" : assembler.account(userId).name());
            logs.record(OperationLogEntry.builder()
                    .operatorType("USER")
                    .operatorId(userId)
                    .opType("RECORD_EDIT")
                    .opAction("SEND")
                    .bizType(BizType.FARM_TRANSFER.name())
                    .bizId(transfer.getId())
                    .farmId(farmId)
                    .targetName(targetName)
                    .searchText(viewer.farm().getName())
                    .after(Map.of("to_member_id", target.getId()))
                    .build());
            return assembler.transfer(viewer.farm(), transfer, assembler.account(userId) == null ? null
                    : assembler.account(userId).name(), targetName);
        });
    }

    @Override
    @Transactional
    public TransferAcceptVO accept(Long transferId, Long userId) {
        return nameLock.call(userId, () -> doAccept(transferId, userId));
    }

    private TransferAcceptVO doAccept(Long transferId, Long userId) {
        FarmTransfer transfer = requireTransferForUser(transferId, userId);
        Farm farm = lookup.requireNotDeletedFarm(transfer.getFarmId());
        if ("ACCEPTED".equals(transfer.getStatus())) {
            if (Objects.equals(farm.getOwnerUserId(), userId)) {
                return acceptVO(farm);
            }
            throw FarmErrors.stateChanged("这条转让请求已处理");
        }
        if (!"PENDING".equals(transfer.getStatus())) {
            throw FarmErrors.stateChanged("CANCELED".equals(transfer.getStatus())
                    ? "转让请求已撤回" : "这条转让请求已处理");
        }
        LocalDateTime now = BeijingTime.now();
        if (transfer.getExpiresAt() != null && !transfer.getExpiresAt().isAfter(now)) {
            expire(transfer, now);
            notify.transferExpired(farm, transfer, assembler.account(userId) == null ? "对方"
                    : assembler.account(userId).name());
            throw FarmErrors.stateChanged("转让请求已过期");
        }
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        FarmMember target = lookup.findActiveMember(farm.getId(), userId);
        if (target == null || !"MANAGER".equals(target.getRole())) {
            cancel(transfer, "对方已不是本场场长", now);
            throw FarmErrors.stateChanged("你已不是" + farm.getName() + "的场长，不能接收所有权");
        }
        if (!Objects.equals(farm.getOwnerUserId(), transfer.getFromUserId())) {
            cancel(transfer, "养殖场所有者已变化", now);
            throw FarmErrors.stateChanged("养殖场所有者已变化，请刷新后重试");
        }
        if (nameGuard.exists(userId, farm.getName(), null)) {
            AccountBrief from = assembler.account(transfer.getFromUserId());
            throw new com.zhimu.server.common.error.BizException(ErrorCode.NAME_DUPLICATE,
                    "你名下已有一个叫“" + farm.getName() + "”的养殖场，暂时不能接收。请联系"
                            + (from == null ? "对方" : from.name()) + "修改养殖场名称后再接收");
        }
        int transferRows = TenantSkip.call(() -> transferMapper.update(new FarmTransfer(),
                Wrappers.<FarmTransfer>lambdaUpdate()
                        .eq(FarmTransfer::getId, transfer.getId())
                        .eq(FarmTransfer::getStatus, "PENDING")
                        .set(FarmTransfer::getStatus, "ACCEPTED")
                        .set(FarmTransfer::getPendingFlag, null)
                        .set(FarmTransfer::getHandledAt, now)));
        if (transferRows == 0) {
            throw FarmErrors.stateChanged("这条转让请求已处理");
        }
        FarmMember oldOwner = lookup.findActiveMember(farm.getId(), transfer.getFromUserId());
        if (oldOwner == null) {
            throw FarmErrors.stateChanged("养殖场所有者已变化，请刷新后重试");
        }
        target.setRole("OWNER");
        target.setUpdatedBy(userId);
        if (TenantSkip.call(() -> memberMapper.updateById(target)) == 0) {
            throw FarmErrors.versionConflict();
        }
        oldOwner.setRole("MANAGER");
        oldOwner.setUpdatedBy(userId);
        if (TenantSkip.call(() -> memberMapper.updateById(oldOwner)) == 0) {
            throw FarmErrors.versionConflict();
        }
        int farmRows = TenantSkip.call(() -> farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                .eq(Farm::getId, farm.getId())
                .eq(Farm::getVersion, farm.getVersion())
                .eq(Farm::getOwnerUserId, transfer.getFromUserId())
                .set(Farm::getOwnerUserId, userId)
                .set(Farm::getUpdatedBy, userId)
                .setSql("version = version + 1")));
        if (farmRows == 0) {
            throw FarmErrors.stateChanged("养殖场所有者已变化，请刷新后重试");
        }
        farm.setOwnerUserId(userId);
        farm.setVersion(farm.getVersion() + 1);
        transfer.setStatus("ACCEPTED");
        transfer.setHandledAt(now);
        cache.evictUsers(farm.getId(), List.of(transfer.getFromUserId(), userId));
        notify.transferAccepted(farm, transfer, assembler.account(userId) == null ? "对方"
                : assembler.account(userId).name());
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("APPROVE")
                .bizType(BizType.FARM_TRANSFER.name())
                .bizId(transfer.getId())
                .farmId(farm.getId())
                .targetName(farm.getName())
                .searchText(farm.getName())
                .before(Map.of("owner_user_id", transfer.getFromUserId()))
                .after(Map.of("owner_user_id", userId))
                .build());
        return acceptVO(farm);
    }

    @Override
    @Transactional
    public void reject(Long transferId, Long userId) {
        FarmTransfer transfer = requireTransferForUser(transferId, userId);
        if (!"PENDING".equals(transfer.getStatus())) {
            throw FarmErrors.stateChanged("这条转让请求已处理");
        }
        LocalDateTime now = BeijingTime.now();
        if (transfer.getExpiresAt() != null && !transfer.getExpiresAt().isAfter(now)) {
            expire(transfer, now);
            Farm farm = lookup.findFarm(transfer.getFarmId());
            notify.transferExpired(farm, transfer, assembler.account(userId) == null ? "对方"
                    : assembler.account(userId).name());
            throw FarmErrors.stateChanged("转让请求已过期");
        }
        int rows = TenantSkip.call(() -> transferMapper.update(new FarmTransfer(),
                Wrappers.<FarmTransfer>lambdaUpdate()
                        .eq(FarmTransfer::getId, transfer.getId())
                        .eq(FarmTransfer::getStatus, "PENDING")
                        .set(FarmTransfer::getStatus, "REJECTED")
                        .set(FarmTransfer::getPendingFlag, null)
                        .set(FarmTransfer::getHandledAt, now)));
        if (rows == 0) {
            throw FarmErrors.stateChanged("这条转让请求已处理");
        }
        Farm farm = lookup.findFarm(transfer.getFarmId());
        notify.transferRejected(farm, transfer, assembler.account(userId) == null ? "对方"
                : assembler.account(userId).name());
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("REJECT")
                .bizType(BizType.FARM_TRANSFER.name())
                .bizId(transfer.getId())
                .farmId(transfer.getFarmId())
                .build());
    }

    @Override
    @Transactional
    public TransferVO cancel(Long transferId, Long userId) {
        FarmTransfer transfer = TenantSkip.call(() -> transferMapper.selectById(transferId));
        if (transfer == null) {
            throw FarmErrors.notFound(null);
        }
        if (!Objects.equals(transfer.getFromUserId(), userId)) {
            throw FarmErrors.notFound(null);
        }
        Farm farm = lookup.requireNotDeletedFarm(transfer.getFarmId());
        if (!Objects.equals(farm.getOwnerUserId(), userId)) {
            throw FarmErrors.notFound(null);
        }
        if (FarmStatuses.of(farm) == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (!"PENDING".equals(transfer.getStatus())) {
            throw FarmErrors.stateChanged("对方已处理这条转让请求，列表已刷新");
        }
        LocalDateTime now = BeijingTime.now();
        if (transfer.getExpiresAt() != null && !transfer.getExpiresAt().isAfter(now)) {
            expire(transfer, now);
            throw FarmErrors.stateChanged("转让请求已过期");
        }
        int rows = TenantSkip.call(() -> transferMapper.update(new FarmTransfer(),
                Wrappers.<FarmTransfer>lambdaUpdate()
                        .eq(FarmTransfer::getId, transfer.getId())
                        .eq(FarmTransfer::getStatus, "PENDING")
                        .set(FarmTransfer::getStatus, "CANCELED")
                        .set(FarmTransfer::getPendingFlag, null)
                        .set(FarmTransfer::getHandledAt, now)
                        .set(FarmTransfer::getCancelReason, "所有者撤回")));
        if (rows == 0) {
            throw FarmErrors.stateChanged("对方已处理这条转让请求，列表已刷新");
        }
        transfer.setStatus("CANCELED");
        transfer.setHandledAt(now);
        transfer.setCancelReason("所有者撤回");
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("WITHDRAW")
                .bizType(BizType.FARM_TRANSFER.name())
                .bizId(transfer.getId())
                .farmId(farm.getId())
                .targetName(farm.getName())
                .searchText(farm.getName())
                .build());
        return assembler.transfer(farm, transfer);
    }

    // ===== 共用 =====

    private TransferAcceptVO acceptVO(Farm farm) {
        TransferAcceptVO vo = new TransferAcceptVO();
        vo.setFarmId(farm.getId());
        vo.setFarmName(farm.getName());
        vo.setRole(FarmRole.OWNER.name());
        return vo;
    }

    private FarmTransfer requireTransferForUser(Long transferId, Long userId) {
        FarmTransfer transfer = TenantSkip.call(() -> transferMapper.selectById(transferId));
        if (transfer == null || !Objects.equals(transfer.getToUserId(), userId)) {
            throw FarmErrors.notFound(null);
        }
        return transfer;
    }

    private void expire(FarmTransfer transfer, LocalDateTime now) {
        TenantSkip.run(() -> transferMapper.update(new FarmTransfer(), Wrappers.<FarmTransfer>lambdaUpdate()
                .eq(FarmTransfer::getId, transfer.getId())
                .eq(FarmTransfer::getStatus, "PENDING")
                .set(FarmTransfer::getStatus, "EXPIRED")
                .set(FarmTransfer::getPendingFlag, null)
                .set(FarmTransfer::getHandledAt, now)));
        transfer.setStatus("EXPIRED");
        transfer.setHandledAt(now);
    }

    private void cancel(FarmTransfer transfer, String reason, LocalDateTime now) {
        TenantSkip.run(() -> transferMapper.update(new FarmTransfer(), Wrappers.<FarmTransfer>lambdaUpdate()
                .eq(FarmTransfer::getId, transfer.getId())
                .eq(FarmTransfer::getStatus, "PENDING")
                .set(FarmTransfer::getStatus, "CANCELED")
                .set(FarmTransfer::getPendingFlag, null)
                .set(FarmTransfer::getHandledAt, now)
                .set(FarmTransfer::getCancelReason, reason)));
        transfer.setStatus("CANCELED");
        transfer.setHandledAt(now);
        transfer.setCancelReason(reason);
    }

    private Viewer viewer(Long farmId, Long userId) {
        Farm farm = lookup.findFarmIncludingDeleted(farmId);
        if (farm == null) {
            throw FarmErrors.notFound(null);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw FarmErrors.deleted(farm.getName());
        }
        FarmMember member = lookup.findActiveMember(farmId, userId);
        if (member == null) {
            throw FarmErrors.notMember(farm.getName());
        }
        return new Viewer(farm, member, FarmStatuses.of(farm));
    }

    private static void requireOwner(Viewer viewer) {
        if (!"OWNER".equals(viewer.member().getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
        }
    }

    private static void requireReadable(Viewer viewer) {
        if (viewer.status() == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(viewer.farm().getName());
        }
        if (!FarmStatuses.readable(viewer.status())) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
    }

    private record Viewer(Farm farm, FarmMember member, FarmStatus status) {
    }
}

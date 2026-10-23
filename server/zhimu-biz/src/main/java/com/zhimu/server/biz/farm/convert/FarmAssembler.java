package com.zhimu.server.biz.farm.convert;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmInvitePen;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmInvitePenMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmPermissions;
import com.zhimu.server.biz.farm.support.FarmSnapshot;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.farm.support.FarmValidations;
import com.zhimu.server.biz.farm.support.JsonLists;
import com.zhimu.server.biz.farm.vo.AuditRecordVO;
import com.zhimu.server.biz.farm.vo.ChangeBriefVO;
import com.zhimu.server.biz.farm.vo.ChangeSnapshotVO;
import com.zhimu.server.biz.farm.vo.FarmAuditVO;
import com.zhimu.server.biz.farm.vo.FarmBasicVO;
import com.zhimu.server.biz.farm.vo.FarmChangeVO;
import com.zhimu.server.biz.farm.vo.FarmContextVO;
import com.zhimu.server.biz.farm.vo.FarmDetailVO;
import com.zhimu.server.biz.farm.vo.FarmListItemVO;
import com.zhimu.server.biz.farm.vo.FarmPermissionVO;
import com.zhimu.server.biz.farm.vo.InviteItemVO;
import com.zhimu.server.biz.farm.vo.MemberActionsVO;
import com.zhimu.server.biz.farm.vo.MemberDetailVO;
import com.zhimu.server.biz.farm.vo.MemberItemVO;
import com.zhimu.server.biz.farm.vo.MemberSummaryVO;
import com.zhimu.server.biz.farm.vo.PenOptionVO;
import com.zhimu.server.biz.farm.vo.QualFileVO;
import com.zhimu.server.biz.farm.vo.RegistrationVO;
import com.zhimu.server.biz.farm.vo.TransferCandidateVO;
import com.zhimu.server.biz.farm.vo.TransferFarmVO;
import com.zhimu.server.biz.farm.vo.TransferVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 模块02 的 VO 组装：状态/权限文案、注册与资质图、审核记录、成员、邀请、转让。 */
@Component
@RequiredArgsConstructor
public class FarmAssembler {

    private static final Map<String, String> INVITE_STATUS_TEXT = Map.of(
            "PENDING", "待接受",
            "ACCEPTED", "已加入",
            "REJECTED", "已拒绝",
            "EXPIRED", "已过期",
            "CANCELED", "已作废");

    private static final Map<String, String> TRANSFER_STATUS_TEXT = Map.of(
            "PENDING", "待确认",
            "ACCEPTED", "已生效",
            "REJECTED", "已拒绝",
            "CANCELED", "已撤回",
            "EXPIRED", "已过期");

    private final BaseDataQueryService baseData;
    private final AccountQueryService accountQuery;
    private final PenQueryService penQuery;
    private final BizMediaService bizMedia;
    private final FarmLookup lookup;
    private final FarmChangeMapper changeMapper;
    private final FarmInviteMapper inviteMapper;
    private final FarmInvitePenMapper invitePenMapper;
    private final FarmTransferMapper transferMapper;

    // ===== 基础信息 =====

    public FarmStatus status(Farm farm) {
        return FarmStatuses.of(farm);
    }

    public FarmPermissionVO permissions(FarmStatus status, FarmRole role) {
        return FarmPermissions.of(status, role);
    }

    public CategoryInfo category(Farm farm) {
        return farm == null || farm.getMainCategoryId() == null ? null : baseData.getCategory(farm.getMainCategoryId());
    }

    public FarmBasicVO basic(Farm farm) {
        FarmBasicVO vo = new FarmBasicVO();
        vo.setContactName(farm.getContactName());
        vo.setContactPhone(farm.getContactPhone());
        vo.setContactPhoneMasked(FarmValidations.maskedPhone(farm.getContactPhone()));
        vo.setIntro(farm.getIntro());
        vo.setScale(farm.getScale());
        vo.setVersion(farm.getVersion());
        return vo;
    }

    public FarmContextVO context(Farm farm, FarmMember member) {
        FarmStatus status = status(farm);
        FarmRole role = FarmRole.valueOf(member.getRole());
        CategoryInfo category = category(farm);
        FarmContextVO vo = new FarmContextVO();
        vo.setFarmId(farm.getId());
        vo.setName(farm.getName());
        vo.setStatus(status.name());
        vo.setStatusText(status.getText());
        vo.setRole(role.name());
        vo.setRoleText(role.getText());
        if (category != null) {
            vo.setSpeciesText(category.speciesText());
            vo.setMainCategoryId(category.id());
            vo.setMainCategoryName(category.name());
            vo.setCountUnit(category.countUnit());
        }
        vo.setRestrictTip(FarmStatuses.restrictTip(farm, status));
        if (role == FarmRole.STAFF) {
            List<Long> penIds = lookup.memberPens(farm.getId(), member.getId()).stream()
                    .map(MemberPen::getPenId).toList();
            vo.setMyPenIds(penIds);
        }
        vo.setPermissions(permissions(status, role));
        return vo;
    }

    public FarmListItemVO listItem(Farm farm, FarmMember member, FarmStatus status, FarmRole role,
                                   boolean current, Integer currentStock, int myPenCount) {
        CategoryInfo category = category(farm);
        FarmListItemVO vo = new FarmListItemVO();
        vo.setFarmId(farm.getId());
        vo.setName(farm.getName());
        vo.setStatus(status.name());
        vo.setStatusText(status.getText());
        vo.setRole(role.name());
        vo.setRoleText(role.getText());
        vo.setJoinType(member.getJoinType());
        vo.setIsCurrent(current);
        vo.setCanSwitch(status == FarmStatus.NORMAL || status == FarmStatus.DISABLED);
        vo.setCanQuit(role != FarmRole.OWNER && FarmStatuses.approved(status));
        vo.setLastSwitchAt(BeijingTime.format(member.getLastSwitchAt()));
        if (role == FarmRole.STAFF) {
            vo.setMyPenCount(myPenCount);
            return vo;
        }
        if (category != null) {
            vo.setSpeciesText(category.speciesText());
            vo.setMainCategoryName(category.name());
            vo.setCountUnit(category.countUnit());
            vo.setCountUnitText(category.countUnitText());
        }
        if (status == FarmStatus.PENDING || status == FarmStatus.REJECTED) {
            vo.setScale(farm.getScale());
        } else if (FarmStatuses.readable(status)) {
            vo.setCurrentStock(currentStock);
        }
        return vo;
    }

    // ===== 注册信息与资质图 =====

    public RegistrationVO registration(Farm farm, FarmAudit audit, boolean fullPhone) {
        FarmSnapshot snapshot = audit == null ? snapshotOf(farm) : snapshot(audit.getSnapshotJson(), farm);
        Set<Long> reshoot = new HashSet<>(JsonLists.readLongs(audit == null ? null : audit.getReshootFileIds()));
        return registration(farm, snapshot, fullPhone, reshoot, Set.of());
    }

    public RegistrationVO detailRegistration(Farm farm) {
        RegistrationVO vo = new RegistrationVO();
        vo.setName(farm.getName());
        vo.setAddress(farm.getAddress());
        vo.setProvince(farm.getProvince());
        vo.setCity(farm.getCity());
        vo.setDistrict(farm.getDistrict());
        vo.setTownship(farm.getTownship());
        vo.setAdcode(farm.getAdcode());
        vo.setLongitude(farm.getLongitude());
        vo.setLatitude(farm.getLatitude());
        List<Long> fileIds = mediaFileIds(BizType.FARM, farm.getId());
        List<QualFileVO> files = qualFiles(BizType.FARM, farm.getId(), fileIds, Set.of(), Set.of());
        vo.setQualificationFiles(files);
        vo.setQualificationCount(files.size());
        return vo;
    }

    public RegistrationVO registration(Farm farm, FarmSnapshot snapshot, boolean fullPhone, Set<Long> reshootIds,
                                       Set<Long> newIds) {
        RegistrationVO vo = new RegistrationVO();
        vo.setName(snapshot.name());
        vo.setMainCategoryId(snapshot.mainCategoryId());
        CategoryInfo category = snapshot.mainCategoryId() == null ? null
                : baseData.getCategory(snapshot.mainCategoryId());
        if (category != null) {
            vo.setMainCategoryName(category.name());
            vo.setCountUnit(category.countUnit());
            vo.setCountUnitText(category.countUnitText());
        }
        vo.setScale(snapshot.scale());
        vo.setContactName(snapshot.contactName());
        vo.setContactPhone(fullPhone ? snapshot.contactPhone() : null);
        vo.setContactPhoneMasked(FarmValidations.maskedPhone(snapshot.contactPhone()));
        vo.setAddress(snapshot.address());
        vo.setProvince(snapshot.province());
        vo.setCity(snapshot.city());
        vo.setDistrict(snapshot.district());
        vo.setTownship(snapshot.township());
        vo.setAdcode(snapshot.adcode());
        vo.setLongitude(snapshot.longitude());
        vo.setLatitude(snapshot.latitude());
        vo.setLocAccuracy(snapshot.locAccuracy());
        List<QualFileVO> files = qualFiles(BizType.FARM, farm.getId(), snapshot.fileIds(), reshootIds, newIds);
        vo.setQualificationFiles(files);
        vo.setQualificationCount(files.size());
        return vo;
    }

    /** 记录关联的资质图（按给定 file_id 顺序；本地没有关联时只返回 file_id） */
    public List<QualFileVO> qualFiles(BizType bizType, Long bizId, List<Long> order, Set<Long> reshootIds,
                                      Set<Long> newIds) {
        Map<Long, MediaItemVO> media = new LinkedHashMap<>();
        if (bizId != null) {
            List<MediaItemVO> items = bizMedia.list(bizType, bizId);
            if (items != null) {
                for (MediaItemVO item : items) {
                    media.put(item.getFileId(), item);
                }
            }
        }
        List<Long> fileIds = order == null || order.isEmpty() ? new ArrayList<>(media.keySet()) : order;
        List<QualFileVO> result = new ArrayList<>();
        int index = 1;
        for (Long fileId : fileIds) {
            if (fileId == null) {
                continue;
            }
            MediaItemVO item = media.get(fileId);
            QualFileVO vo = new QualFileVO();
            vo.setFileId(fileId);
            if (item != null) {
                vo.setUrl(item.getUrl());
                vo.setThumbUrl(item.getThumbUrl());
                vo.setMediaType(item.getMediaType());
                vo.setMimeType(item.getMimeType());
                vo.setSize(item.getSize());
                vo.setWidth(item.getWidth());
                vo.setHeight(item.getHeight());
                vo.setStatus(item.getStatus());
            }
            vo.setSortNo(index++);
            vo.setReshoot(reshootIds != null && reshootIds.contains(fileId));
            vo.setIsNew(newIds != null && newIds.contains(fileId));
            result.add(vo);
        }
        return result;
    }

    public List<Long> mediaFileIds(BizType bizType, Long bizId) {
        List<Long> ids = bizMedia.listFileIds(bizType, bizId);
        return ids == null ? List.of() : ids;
    }

    public FarmSnapshot snapshotOf(Farm farm) {
        return new FarmSnapshot(farm.getName(), farm.getMainCategoryId(), farm.getScale(), farm.getContactName(),
                farm.getContactPhone(), farm.getAddress(), farm.getProvince(), farm.getCity(), farm.getDistrict(),
                farm.getTownship(), farm.getAdcode(), farm.getLongitude(), farm.getLatitude(), farm.getLocAccuracy(),
                mediaFileIds(BizType.FARM, farm.getId()));
    }

    public FarmSnapshot snapshot(String json, Farm farm) {
        if (json == null || json.isBlank()) {
            return snapshotOf(farm);
        }
        try {
            FarmSnapshot snapshot = com.zhimu.server.framework.jackson.CacheJson.read(json, FarmSnapshot.class);
            return snapshot == null ? snapshotOf(farm) : snapshot;
        } catch (RuntimeException e) {
            return snapshotOf(farm);
        }
    }

    // ===== 审核 =====

    public FarmAuditVO audit(Farm farm, FarmAudit latest, List<FarmAudit> rows) {
        FarmStatus status = status(farm);
        CategoryInfo category = category(farm);
        FarmAuditVO vo = new FarmAuditVO();
        vo.setFarmId(farm.getId());
        vo.setName(farm.getName());
        vo.setStatus(status.name());
        vo.setStatusText(status.getText());
        vo.setRestrictTip(FarmStatuses.restrictTip(farm, status));
        if (latest != null) {
            vo.setAuditStatus(latest.getStatus());
            vo.setSubmitNo(latest.getSubmitNo());
            vo.setAuditVersion(latest.getVersion());
            vo.setSubmittedAt(BeijingTime.format(latest.getSubmittedAt()));
            vo.setAuditedAt(BeijingTime.format(latest.getAuditedAt()));
            vo.setRejectReason(latest.getRejectReason());
            vo.setReshootCount(JsonLists.readLongs(latest.getReshootFileIds()).size());
            vo.setRegistration(registration(farm, latest, true));
        }
        if (rows != null && !rows.isEmpty()) {
            LocalDateTime first = rows.stream().map(FarmAudit::getSubmittedAt).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            vo.setFirstSubmittedAt(BeijingTime.format(first));
        }
        vo.setRecords(records(rows));
        return vo;
    }

    public List<AuditRecordVO> records(List<FarmAudit> rows) {
        List<AuditRecordVO> records = new ArrayList<>();
        if (rows == null) {
            return records;
        }
        for (FarmAudit row : rows) {
            boolean first = row.getSubmitNo() != null && row.getSubmitNo() == 1;
            String submitType = first ? "SUBMIT" : "RESUBMIT";
            AuditRecordVO submit = new AuditRecordVO();
            submit.setType(submitType);
            submit.setTypeText(first ? "提交审核" : "重新提交审核");
            submit.setTime(BeijingTime.format(row.getSubmittedAt()));
            submit.setStatusTag("PENDING".equals(row.getStatus()) ? "待审核" : "已提交");
            submit.setDesc(first ? null : row.getChangeSummary());
            submit.setSubmitNo(row.getSubmitNo());
            records.add(submit);
            if ("REJECTED".equals(row.getStatus())) {
                AuditRecordVO reject = new AuditRecordVO();
                reject.setType("REJECT");
                reject.setTypeText("审核未通过");
                reject.setTime(BeijingTime.format(row.getAuditedAt()));
                reject.setStatusTag("已驳回");
                reject.setDesc(row.getRejectReason());
                reject.setSubmitNo(row.getSubmitNo());
                records.add(reject);
            } else if ("APPROVED".equals(row.getStatus())) {
                AuditRecordVO approve = new AuditRecordVO();
                approve.setType("APPROVE");
                approve.setTypeText("审核通过");
                approve.setTime(BeijingTime.format(row.getAuditedAt()));
                approve.setStatusTag("已通过");
                approve.setDesc(row.getAuditOpinion());
                approve.setSubmitNo(row.getSubmitNo());
                records.add(approve);
            }
        }
        records.sort(Comparator.comparing(AuditRecordVO::getTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return records;
    }

    // ===== 资料变更 =====

    public FarmChangeVO change(Farm farm, FarmChange change) {
        FarmChangeVO vo = new FarmChangeVO();
        vo.setChangeId(change.getId());
        vo.setChangeNo(change.getChangeNo());
        vo.setStatus(change.getStatus());
        vo.setStatusText(auditStatusText(change.getStatus()));
        vo.setSubmittedAt(BeijingTime.format(change.getCreatedAt()));
        vo.setAuditedAt(BeijingTime.format(change.getAuditedAt()));
        vo.setRejectReason(change.getRejectReason());
        vo.setRemark(change.getRemark());
        List<String> fields = new ArrayList<>();
        if (Boolean.TRUE.equals(change.getNameChanged())) {
            fields.add("NAME");
        }
        if (Boolean.TRUE.equals(change.getAddressChanged())) {
            fields.add("ADDRESS");
        }
        if (Boolean.TRUE.equals(change.getQualChanged())) {
            fields.add("QUALIFICATION");
        }
        vo.setChangedFields(fields);

        FarmSnapshot before = snapshot(change.getBeforeJson(), farm);
        Set<Long> beforeIds = new LinkedHashSet<>(before.fileIds());
        List<Long> afterIds = mediaFileIds(BizType.FARM_CHANGE, change.getId());
        Set<Long> afterSet = new LinkedHashSet<>(afterIds);
        Set<Long> added = new LinkedHashSet<>(afterSet);
        added.removeAll(beforeIds);
        Set<Long> removed = new LinkedHashSet<>(beforeIds);
        removed.removeAll(afterSet);
        vo.setAddedCount(added.size());
        vo.setRemovedCount(removed.size());

        ChangeSnapshotVO beforeVo = new ChangeSnapshotVO();
        beforeVo.setName(before.name());
        beforeVo.setAddress(before.address());
        beforeVo.setQualificationFiles(qualFiles(BizType.FARM, farm.getId(), before.fileIds(), Set.of(), Set.of()));
        vo.setBefore(beforeVo);

        ChangeSnapshotVO afterVo = new ChangeSnapshotVO();
        afterVo.setName(change.getNewName());
        afterVo.setAddress(change.getNewAddress());
        afterVo.setProvince(change.getNewProvince());
        afterVo.setCity(change.getNewCity());
        afterVo.setDistrict(change.getNewDistrict());
        afterVo.setTownship(change.getNewTownship());
        afterVo.setAdcode(change.getNewAdcode());
        afterVo.setLongitude(change.getNewLongitude());
        afterVo.setLatitude(change.getNewLatitude());
        afterVo.setLocAccuracy(change.getNewLocAccuracy());
        afterVo.setQualificationFiles(qualFiles(BizType.FARM_CHANGE, change.getId(), afterIds, Set.of(), added));
        vo.setAfter(afterVo);
        return vo;
    }

    public ChangeBriefVO changeBrief(FarmChange change) {
        if (change == null) {
            return null;
        }
        ChangeBriefVO vo = new ChangeBriefVO();
        vo.setChangeId(change.getId());
        vo.setSubmittedAt(BeijingTime.format(change.getCreatedAt()));
        vo.setRejectReason(change.getRejectReason());
        vo.setAuditedAt(BeijingTime.format(change.getAuditedAt()));
        return vo;
    }

    public String auditStatusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "CANCELED" -> "已撤销";
            default -> status;
        };
    }

    // ===== 成员 =====

    public MemberSummaryVO memberSummary(Farm farm) {
        List<FarmMember> members = lookup.activeMembers(farm.getId());
        MemberSummaryVO vo = new MemberSummaryVO();
        int owner = 0;
        int manager = 0;
        int staff = 0;
        for (FarmMember member : members) {
            switch (member.getRole()) {
                case "OWNER" -> owner++;
                case "MANAGER" -> manager++;
                default -> staff++;
            }
        }
        vo.setMemberTotal(members.size());
        vo.setOwnerCount(owner);
        vo.setManagerCount(manager);
        vo.setStaffCount(staff);

        List<FarmInvite> invites = invitesOfFarm(farm.getId(), false);
        vo.setInviteCount(invites.size());
        vo.setInvitePendingCount((int) invites.stream().filter(i -> "PENDING".equals(i.getStatus())).count());

        Map<Long, AccountBrief> accounts = accounts(members.stream().map(FarmMember::getUserId).toList());
        List<String> avatars = new ArrayList<>();
        for (FarmMember member : members) {
            if (avatars.size() >= 4) {
                break;
            }
            AccountBrief account = accounts.get(member.getUserId());
            if (account != null) {
                avatars.add(account.nameInitial());
            }
        }
        vo.setAvatars(avatars);
        List<PenBriefVO> unassigned = unassignedPens(farm.getId(), members);
        vo.setUnassignedPens(unassigned);
        vo.setShowUnassignedBanner(staff > 0 && !unassigned.isEmpty());
        return vo;
    }

    public MemberItemVO memberItem(Farm farm, FarmMember member, FarmMember viewer, AccountBrief account,
                                   FarmStatus status) {
        MemberItemVO vo = new MemberItemVO();
        fillMemberItem(vo, farm, member, viewer, account, status);
        return vo;
    }

    public MemberDetailVO memberDetail(Farm farm, FarmMember member, FarmMember viewer, AccountBrief account,
                                       FarmStatus status) {
        MemberDetailVO vo = new MemberDetailVO();
        fillMemberItem(vo, farm, member, viewer, account, status);
        vo.setPhone(account == null ? null : account.phone());
        vo.setFarmName(farm.getName());
        vo.setActions(actions(status, FarmRole.valueOf(viewer.getRole()), viewer, member));
        return vo;
    }

    private void fillMemberItem(MemberItemVO vo, Farm farm, FarmMember member, FarmMember viewer,
                                AccountBrief account, FarmStatus status) {
        vo.setMemberId(member.getId());
        vo.setUserId(member.getUserId());
        vo.setName(account == null ? "已注销用户" : account.name());
        vo.setNameInitial(account == null ? null : account.nameInitial());
        vo.setPhoneMasked(account == null ? null : account.phoneMasked());
        vo.setRole(member.getRole());
        vo.setRoleText(FarmRole.valueOf(member.getRole()).getText());
        vo.setJoinType(member.getJoinType());
        vo.setJoinTypeText("CREATE".equals(member.getJoinType()) ? "创建" : "邀请加入");
        vo.setJoinedAt(BeijingTime.format(member.getJoinedAt()));
        vo.setIsMe(Objects.equals(viewer.getUserId(), member.getUserId()));
        boolean allPens = !"STAFF".equals(member.getRole());
        vo.setAllPens(allPens);
        vo.setCanAdjustPens(canAdjustPens(status, viewer, member));
        vo.setPens(allPens ? null : pens(farm.getId(), member.getId()));
        vo.setVersion(member.getVersion());
    }

    private boolean canAdjustPens(FarmStatus status, FarmMember viewer, FarmMember member) {
        if (status != FarmStatus.NORMAL || !"STAFF".equals(member.getRole())) {
            return false;
        }
        return "OWNER".equals(viewer.getRole()) || "MANAGER".equals(viewer.getRole());
    }

    public MemberActionsVO actions(FarmStatus status, FarmRole viewerRole, FarmMember viewer, FarmMember target) {
        MemberActionsVO actions = new MemberActionsVO();
        boolean self = Objects.equals(viewer.getUserId(), target.getUserId());
        boolean targetOwner = "OWNER".equals(target.getRole());
        boolean normal = status == FarmStatus.NORMAL;
        boolean removable = status == FarmStatus.NORMAL || status == FarmStatus.DISABLED;
        actions.setCanChangeRole(false);
        actions.setCanAdjustPens(false);
        actions.setCanRemove(false);
        actions.setCanQuit(false);
        if (self) {
            actions.setCanQuit(viewerRole != FarmRole.OWNER && FarmStatuses.approved(status));
            return actions;
        }
        if (viewerRole == FarmRole.OWNER) {
            actions.setCanChangeRole(normal && !targetOwner);
            actions.setCanAdjustPens(normal && "STAFF".equals(target.getRole()));
            actions.setCanRemove(removable && !targetOwner);
        } else if (viewerRole == FarmRole.MANAGER) {
            actions.setCanAdjustPens(normal && "STAFF".equals(target.getRole()));
        }
        return actions;
    }

    public List<PenBriefVO> pens(Long farmId, Long memberId) {
        List<MemberPen> memberPens = lookup.memberPens(farmId, memberId);
        if (memberPens.isEmpty()) {
            return List.of();
        }
        Map<Long, PenInfo> pens = penQuery.getPens(memberPens.stream().map(MemberPen::getPenId).toList());
        List<PenBriefVO> result = new ArrayList<>();
        for (MemberPen memberPen : memberPens) {
            PenInfo pen = pens == null ? null : pens.get(memberPen.getPenId());
            if (pen != null && Objects.equals(pen.farmId(), farmId)) {
                result.add(new PenBriefVO(pen.penId(), pen.penName(), pen.penTypeText()));
            }
        }
        return result;
    }

    /** 本场启用但没有在场员工负责的栏舍 */
    public List<PenBriefVO> unassignedPens(Long farmId, List<FarmMember> members) {
        List<PenInfo> enabled = penQuery.listPens(farmId, false);
        if (enabled == null || enabled.isEmpty()) {
            return List.of();
        }
        Set<Long> staffMemberIds = new HashSet<>();
        for (FarmMember member : members) {
            if ("STAFF".equals(member.getRole())) {
                staffMemberIds.add(member.getId());
            }
        }
        Set<Long> covered = new HashSet<>();
        for (MemberPen pen : lookup.pensOfFarm(farmId)) {
            if (staffMemberIds.contains(pen.getMemberId())) {
                covered.add(pen.getPenId());
            }
        }
        List<PenBriefVO> result = new ArrayList<>();
        for (PenInfo pen : enabled) {
            if (!covered.contains(pen.penId())) {
                result.add(new PenBriefVO(pen.penId(), pen.penName(), pen.penTypeText()));
            }
        }
        return result;
    }

    public List<PenOptionVO> penOptions(Long farmId, Long memberId, FarmMember member) {
        List<PenInfo> enabled = penQuery.listPens(farmId, false);
        if (enabled == null || enabled.isEmpty()) {
            return List.of();
        }
        Set<Long> selected = new HashSet<>();
        if (member != null) {
            lookup.memberPens(farmId, member.getId()).forEach(pen -> selected.add(pen.getPenId()));
        }
        List<FarmMember> members = lookup.activeMembers(farmId);
        Set<Long> staffMemberIds = new HashSet<>();
        for (FarmMember row : members) {
            if ("STAFF".equals(row.getRole()) && !Objects.equals(row.getId(), memberId)) {
                staffMemberIds.add(row.getId());
            }
        }
        Map<Long, Integer> otherCounts = new HashMap<>();
        for (MemberPen pen : lookup.pensOfFarm(farmId)) {
            if (staffMemberIds.contains(pen.getMemberId())) {
                otherCounts.merge(pen.getPenId(), 1, Integer::sum);
            }
        }
        List<PenOptionVO> result = new ArrayList<>();
        for (PenInfo pen : enabled) {
            PenOptionVO vo = new PenOptionVO();
            vo.setPenId(pen.penId());
            vo.setPenCode(pen.penCode());
            vo.setName(pen.penName());
            vo.setSelected(selected.contains(pen.penId()));
            vo.setOtherStaffCount(otherCounts.getOrDefault(pen.penId(), 0));
            result.add(vo);
        }
        result.sort(Comparator.comparing(PenOptionVO::getPenCode, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    public List<PenBriefVO> penBriefs(Collection<Long> penIds, Long farmId) {
        if (penIds == null || penIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PenInfo> pens = penQuery.getPens(penIds);
        List<PenBriefVO> result = new ArrayList<>();
        for (Long penId : penIds) {
            PenInfo pen = pens == null ? null : pens.get(penId);
            if (pen != null && (farmId == null || Objects.equals(pen.farmId(), farmId))) {
                result.add(new PenBriefVO(pen.penId(), pen.penName(), pen.penTypeText()));
            }
        }
        return result;
    }

    public AccountBrief account(Long userId) {
        return userId == null ? null : accountQuery.getBrief(userId);
    }

    public Map<Long, AccountBrief> accounts(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AccountBrief> briefs = accountQuery.listBriefs(userIds);
        return briefs == null ? Map.of() : briefs;
    }

    // ===== 邀请 =====

    public InviteItemVO inviteItem(Farm farm, FarmInvite invite, boolean ownerView) {
        InviteItemVO vo = new InviteItemVO();
        vo.setInviteId(invite.getId());
        vo.setInviteePhoneMasked(FarmValidations.maskedPhone(invite.getInviteePhone()));
        vo.setInviteePhone(ownerView ? invite.getInviteePhone() : null);
        vo.setRole(invite.getRole());
        vo.setRoleText(FarmRole.valueOf(invite.getRole()).getText());
        vo.setStatus(invite.getStatus());
        vo.setStatusText(INVITE_STATUS_TEXT.getOrDefault(invite.getStatus(), invite.getStatus()));
        vo.setInvitedAt(BeijingTime.format(invite.getCreatedAt()));
        vo.setExpiresAt(BeijingTime.format(invite.getExpiresAt()));
        vo.setHandledAt(BeijingTime.format(invite.getHandledAt()));
        vo.setCanReinvite(ownerView && ("EXPIRED".equals(invite.getStatus())
                || "REJECTED".equals(invite.getStatus())));
        List<FarmInvitePen> invitePens = TenantSkip.call(() -> invitePenMapper.selectList(
                Wrappers.<FarmInvitePen>lambdaQuery()
                        .eq(FarmInvitePen::getInviteId, invite.getId())
                        .orderByAsc(FarmInvitePen::getId)));
        vo.setPens(penBriefs(invitePens.stream().map(FarmInvitePen::getPenId).toList(), farm.getId()));
        return vo;
    }

    public List<FarmInvite> invitesOfFarm(Long farmId, boolean onlyPending) {
        return TenantSkip.call(() -> inviteMapper.selectList(Wrappers.<FarmInvite>lambdaQuery()
                .eq(FarmInvite::getFarmId, farmId)
                .eq(onlyPending, FarmInvite::getStatus, "PENDING")
                .in(!onlyPending, FarmInvite::getStatus, List.of("PENDING", "EXPIRED", "REJECTED"))
                .orderByDesc(FarmInvite::getCreatedAt)
                .orderByDesc(FarmInvite::getId)));
    }

    // ===== 转让 =====

    public TransferVO transfer(Farm farm, FarmTransfer transfer) {
        if (transfer == null) {
            return null;
        }
        Map<Long, AccountBrief> accounts = accounts(List.of(transfer.getFromUserId(), transfer.getToUserId()));
        AccountBrief from = accounts.get(transfer.getFromUserId());
        AccountBrief to = accounts.get(transfer.getToUserId());
        return transfer(farm, transfer, from == null ? null : from.name(), to == null ? null : to.name());
    }

    public TransferVO transfer(Farm farm, FarmTransfer transfer, String fromName, String toName) {
        if (transfer == null) {
            return null;
        }
        TransferVO vo = new TransferVO();
        vo.setTransferId(transfer.getId());
        vo.setFarmId(transfer.getFarmId());
        vo.setToMemberId(transfer.getToMemberId());
        vo.setFarmName(farm == null ? null : farm.getName());
        vo.setFromUserName(fromName);
        vo.setToName(toName);
        vo.setStatus(transfer.getStatus());
        vo.setStatusText(TRANSFER_STATUS_TEXT.getOrDefault(transfer.getStatus(), transfer.getStatus()));
        vo.setCreatedAt(BeijingTime.format(transfer.getCreatedAt()));
        vo.setExpiresAt(BeijingTime.format(transfer.getExpiresAt()));
        vo.setHandledAt(BeijingTime.format(transfer.getHandledAt()));
        vo.setCancelReason(transfer.getCancelReason());
        return vo;
    }

    public TransferFarmVO transferFarm(Farm farm) {
        TransferFarmVO vo = new TransferFarmVO();
        vo.setFarmId(farm.getId());
        vo.setName(farm.getName());
        CategoryInfo category = category(farm);
        if (category != null) {
            vo.setMainCategoryName(category.name());
            vo.setCountUnit(category.countUnit());
        }
        vo.setCurrentStock(currentStock(farm.getId()));
        return vo;
    }

    public TransferCandidateVO candidate(FarmMember member, AccountBrief account) {
        TransferCandidateVO vo = new TransferCandidateVO();
        vo.setMemberId(member.getId());
        vo.setUserId(member.getUserId());
        vo.setName(account == null ? "已注销用户" : account.name());
        vo.setNameInitial(account == null ? null : account.nameInitial());
        vo.setPhoneMasked(account == null ? null : account.phoneMasked());
        return vo;
    }

    public FarmTransfer pendingTransfer(Long farmId) {
        return TenantSkip.call(() -> transferMapper.selectOne(Wrappers.<FarmTransfer>lambdaQuery()
                .eq(FarmTransfer::getFarmId, farmId)
                .eq(FarmTransfer::getStatus, "PENDING")
                .last("LIMIT 1")));
    }

    // ===== 养殖场信息 =====

    public FarmDetailVO detail(Farm farm, FarmMember viewer) {
        FarmStatus status = status(farm);
        FarmRole role = FarmRole.valueOf(viewer.getRole());
        CategoryInfo category = category(farm);
        FarmDetailVO vo = new FarmDetailVO();
        vo.setFarmId(farm.getId());
        vo.setCurrentStock(currentStock(farm.getId()));
        vo.setName(farm.getName());
        vo.setStatus(status.name());
        vo.setStatusText(status.getText());
        vo.setRole(role.name());
        vo.setRoleText(role.getText());
        if (category != null) {
            vo.setSpeciesText(category.speciesText());
            vo.setMainCategoryName(category.name());
            vo.setCountUnit(category.countUnit());
        }
        vo.setApprovedAt(BeijingTime.format(farm.getApprovedAt()));
        vo.setDisabledAt(BeijingTime.format(farm.getDisabledAt()));
        vo.setRestrictTip(FarmStatuses.restrictTip(farm, status));
        vo.setBasic(basic(farm));
        vo.setRegistration(detailRegistration(farm));
        vo.setMemberSummary(memberSummary(farm));
        vo.setPermissions(permissions(status, role));
        vo.setPendingChange(changeBrief(latestChange(farm.getId(), "PENDING")));
        vo.setRejectedChange(changeBrief(latestChange(farm.getId(), "REJECTED")));
        if (role == FarmRole.OWNER) {
            FarmTransfer pending = pendingTransfer(farm.getId());
            vo.setPendingTransfer(transfer(farm, pending));
        }
        return vo;
    }

    public FarmChange latestChange(Long farmId, String status) {
        return TenantSkip.call(() -> changeMapper.selectOne(Wrappers.<FarmChange>lambdaQuery()
                .eq(FarmChange::getFarmId, farmId)
                .eq(FarmChange::getStatus, status)
                .orderByDesc(FarmChange::getCreatedAt)
                .orderByDesc(FarmChange::getId)
                .last("LIMIT 1")));
    }

    public int currentStock(Long farmId) {
        Map<Long, Integer> stock = penQuery.farmCurrentStock(List.of(farmId));
        return stock == null ? 0 : stock.getOrDefault(farmId, 0);
    }

    /** 批量当前存栏（F01 一次查询） */
    public Map<Long, Integer> penCurrentStock(Collection<Long> farmIds) {
        Map<Long, Integer> stock = penQuery.farmCurrentStock(farmIds);
        return stock == null ? Map.of() : stock;
    }
}

package com.zhimu.server.biz.farm.convert;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmInvitePenMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.vo.FarmAuditVO;
import com.zhimu.server.biz.farm.vo.FarmChangeVO;
import com.zhimu.server.biz.farm.vo.FarmContextVO;
import com.zhimu.server.biz.farm.vo.FarmDetailVO;
import com.zhimu.server.biz.farm.vo.FarmListItemVO;
import com.zhimu.server.biz.farm.vo.MemberDetailVO;
import com.zhimu.server.biz.farm.vo.MemberItemVO;
import com.zhimu.server.biz.farm.vo.MemberSummaryVO;
import com.zhimu.server.biz.farm.vo.TransferCandidateVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.framework.jackson.CacheJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmAssemblerTest {

    @Mock private BaseDataQueryService baseData;
    @Mock private AccountQueryService accountQuery;
    @Mock private PenQueryService penQuery;
    @Mock private BizMediaService bizMedia;
    @Mock private FarmLookup lookup;
    @Mock private FarmChangeMapper changeMapper;
    @Mock private FarmInviteMapper inviteMapper;
    @Mock private FarmInvitePenMapper invitePenMapper;
    @Mock private FarmTransferMapper transferMapper;

    private FarmAssembler assembler;

    @BeforeEach
    void setUp() {
        com.zhimu.server.biz.farm.FarmTableInfo.ensure();
        assembler = new FarmAssembler(baseData, accountQuery, penQuery, bizMedia, lookup,
                changeMapper, inviteMapper, invitePenMapper, transferMapper);
        lenient().when(baseData.getCategory(101L))
                .thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD", "头", false, true));
        lenient().when(accountQuery.getBrief(10001L))
                .thenReturn(new AccountBrief(10001L, "王建国", "13800006620", "NORMAL", false));
        lenient().when(accountQuery.getBrief(10002L))
                .thenReturn(new AccountBrief(10002L, "刘芳", "13900001234", "NORMAL", false));
        lenient().when(accountQuery.getBrief(10003L))
                .thenReturn(new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false));
        lenient().when(accountQuery.listBriefs(any())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            Map<Long, AccountBrief> map = new java.util.LinkedHashMap<>();
            for (Long id : ids) {
                AccountBrief brief = id == 10001L
                        ? new AccountBrief(10001L, "王建国", "13800006620", "NORMAL", false)
                        : id == 10002L ? new AccountBrief(10002L, "刘芳", "13900001234", "NORMAL", false)
                        : new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false);
                map.put(id, brief);
            }
            return map;
        });
        lenient().when(penQuery.farmCurrentStock(any())).thenReturn(Map.of(1001L, 870));
        lenient().when(penQuery.listPens(anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(List.of(pen(2003L, "YF-03", "3 号育肥舍"), pen(2004L, "YF-04", "4 号育肥舍")));
        lenient().when(penQuery.getPens(any())).thenReturn(Map.of(
                2003L, pen(2003L, "YF-03", "3 号育肥舍"),
                2004L, pen(2004L, "YF-04", "4 号育肥舍")));
        lenient().when(bizMedia.list(org.mockito.ArgumentMatchers.any(), anyLong()))
                .thenReturn(List.of(media(9001L), media(9002L)));
        lenient().when(bizMedia.listFileIds(any(), anyLong())).thenReturn(List.of(9001L, 9002L));
    }

    @Test
    void buildsContextForOwnerAndStaff() {
        Farm farm = farm(FarmStatus.NORMAL);
        FarmMember owner = member(5001L, 10001L, "OWNER");
        FarmContextVO ownerContext = assembler.context(farm, owner);
        assertThat(ownerContext.getPermissions().getCanInvite()).isTrue();
        assertThat(ownerContext.getPermissions().getCanQuit()).isFalse();
        assertThat(ownerContext.getCountUnit()).isEqualTo("HEAD");
        assertThat(ownerContext.getSpeciesText()).isEqualTo("猪");

        FarmMember staff = member(5003L, 10003L, "STAFF");
        MemberPen pen = new MemberPen();
        pen.setPenId(2003L);
        when(lookup.memberPens(1001L, 5003L)).thenReturn(List.of(pen));
        FarmContextVO staffContext = assembler.context(farm, staff);
        assertThat(staffContext.getPermissions().getWritable()).isTrue();
        assertThat(staffContext.getPermissions().getCanViewFarmInfo()).isFalse();
        assertThat(staffContext.getMyPenIds()).containsExactly(2003L);
    }

    @Test
    void staffListItemHidesCategoryAndStockAndShowsPenCount() {
        Farm farm = farm(FarmStatus.NORMAL);
        FarmMember staff = member(5003L, 10003L, "STAFF");
        FarmListItemVO item = assembler.listItem(farm, staff, FarmStatus.NORMAL, FarmRole.STAFF,
                true, 870, 2);
        assertThat(item.getMyPenCount()).isEqualTo(2);
        assertThat(item.getSpeciesText()).isNull();
        assertThat(item.getCurrentStock()).isNull();
        assertThat(item.getCountUnit()).isNull();
        assertThat(item.getIsCurrent()).isTrue();
        assertThat(item.getCanSwitch()).isTrue();
        assertThat(item.getCanQuit()).isTrue();
    }

    @Test
    void pendingFarmListItemShowsScaleAndCannotSwitch() {
        Farm farm = farm(FarmStatus.PENDING);
        FarmMember owner = member(5001L, 10001L, "OWNER");
        FarmListItemVO item = assembler.listItem(farm, owner, FarmStatus.PENDING, FarmRole.OWNER,
                false, 0, 0);
        assertThat(item.getScale()).isEqualTo(1500);
        assertThat(item.getCurrentStock()).isNull();
        assertThat(item.getCanSwitch()).isFalse();
        assertThat(item.getCanQuit()).isFalse();
    }

    @Test
    void auditRecordsUseRequiredStatusAndTypeTexts() {
        Farm farm = farm(FarmStatus.REJECTED);
        FarmAudit rejected = new FarmAudit();
        rejected.setId(3001L);
        rejected.setFarmId(1001L);
        rejected.setSubmitNo(1);
        rejected.setStatus("REJECTED");
        rejected.setSubmittedAt(LocalDateTime.of(2026, 10, 17, 18, 32));
        rejected.setAuditedAt(LocalDateTime.of(2026, 10, 18, 16, 20));
        rejected.setRejectReason("照片模糊");
        rejected.setSnapshotJson(snapshot());
        FarmAudit pending = new FarmAudit();
        pending.setId(3002L);
        pending.setFarmId(1001L);
        pending.setSubmitNo(2);
        pending.setStatus("PENDING");
        pending.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 8, 46));
        pending.setChangeSummary("重新拍摄了被标为“需重拍”的 1 张资质图");
        pending.setSnapshotJson(snapshot());
        pending.setReshootFileIds("[9002]");
        pending.setVersion(0);

        FarmAuditVO vo = assembler.audit(farm, pending, List.of(pending, rejected));

        assertThat(vo.getAuditStatus()).isEqualTo("PENDING");
        assertThat(vo.getAuditVersion()).isZero();
        assertThat(vo.getReshootCount()).isEqualTo(1);
        assertThat(vo.getRecords()).hasSize(3);
        assertThat(vo.getRecords().get(0).getStatusTag()).isEqualTo("待审核");
        assertThat(vo.getRecords().get(0).getType()).isEqualTo("RESUBMIT");
        assertThat(vo.getRecords().get(0).getTypeText()).isEqualTo("重新提交审核");
        assertThat(vo.getRecords().get(1).getStatusTag()).isEqualTo("已驳回");
        assertThat(vo.getRecords().get(1).getDesc()).isEqualTo("照片模糊");
        assertThat(vo.getRecords().get(2).getType()).isEqualTo("SUBMIT");
        assertThat(vo.getRecords().get(2).getStatusTag()).isEqualTo("已提交");
        assertThat(vo.getRecords().get(2).getDesc()).isNull();
        assertThat(vo.getRegistration().getContactPhone()).isEqualTo("13800006620");
        assertThat(vo.getRegistration().getQualificationFiles()).hasSize(2);
        assertThat(vo.getRegistration().getQualificationFiles().get(1).getReshoot()).isTrue();
    }

    @Test
    void changeComputesAddedRemovedAndChangedFields() {
        Farm farm = farm(FarmStatus.NORMAL);
        FarmChange change = new FarmChange();
        change.setId(21L);
        change.setFarmId(1001L);
        change.setChangeNo(1);
        change.setStatus("PENDING");
        change.setNameChanged(false);
        change.setAddressChanged(true);
        change.setQualChanged(true);
        change.setNewName("东山猪场");
        change.setNewAddress("新地址");
        change.setNewProvince("内蒙古自治区");
        change.setNewCity("赤峰市");
        change.setNewDistrict("松山区");
        change.setNewAdcode("150404");
        change.setNewLongitude(BigDecimal.valueOf(118.86));
        change.setNewLatitude(BigDecimal.valueOf(42.31));
        change.setCreatedAt(LocalDateTime.of(2026, 10, 19, 10, 12));
        change.setBeforeJson(CacheJson.write(new com.zhimu.server.biz.farm.support.FarmSnapshot(
                "东山猪场", 101L, 1500, "王建国", "13800006620", "旧地址", "内蒙古自治区", "赤峰市",
                "松山区", "太平地镇", "150404", BigDecimal.valueOf(118.85), BigDecimal.valueOf(42.30),
                null, List.of(9001L, 9002L))));
        when(bizMedia.listFileIds(BizType.FARM_CHANGE, 21L)).thenReturn(List.of(9002L, 9003L));
        when(bizMedia.list(BizType.FARM_CHANGE, 21L)).thenReturn(List.of(media(9002L), media(9003L)));

        FarmChangeVO vo = assembler.change(farm, change);

        assertThat(vo.getChangedFields()).containsExactly("ADDRESS", "QUALIFICATION");
        assertThat(vo.getAddedCount()).isEqualTo(1);
        assertThat(vo.getRemovedCount()).isEqualTo(1);
        assertThat(vo.getAfter().getQualificationFiles()).hasSize(2);
        assertThat(vo.getAfter().getQualificationFiles().get(1).getIsNew()).isTrue();
        assertThat(vo.getBefore().getAddress()).isEqualTo("旧地址");
        assertThat(vo.getStatusText()).isEqualTo("待审核");
    }

    @Test
    void memberActionsFollowMatrixAndDisabledFarm() {
        Farm farm = farm(FarmStatus.NORMAL);
        FarmMember owner = member(5001L, 10001L, "OWNER");
        FarmMember staff = member(5003L, 10003L, "STAFF");
        when(lookup.memberPens(1001L, 5003L)).thenReturn(List.of(memberPen(5003L, 2003L),
                memberPen(5003L, 2004L)));
        when(accountQuery.avatarUrl(10003L)).thenReturn("/zmt-private/avatar/staff.jpg?signature=example");
        MemberDetailVO ownerView = assembler.memberDetail(farm, staff, owner,
                new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false), FarmStatus.NORMAL);
        assertThat(ownerView.getActions().getCanChangeRole()).isTrue();
        assertThat(ownerView.getAvatarUrl()).isEqualTo("/zmt-private/avatar/staff.jpg?signature=example");
        assertThat(ownerView.getActions().getCanAdjustPens()).isTrue();
        assertThat(ownerView.getActions().getCanRemove()).isTrue();
        assertThat(ownerView.getActions().getCanQuit()).isFalse();
        assertThat(ownerView.getAllPens()).isFalse();
        assertThat(ownerView.getPens()).hasSize(2);

        FarmMember manager = member(5002L, 10002L, "MANAGER");
        MemberDetailVO managerView = assembler.memberDetail(farm, staff, manager,
                new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false), FarmStatus.NORMAL);
        assertThat(managerView.getActions().getCanAdjustPens()).isTrue();
        assertThat(managerView.getActions().getCanChangeRole()).isFalse();
        assertThat(managerView.getActions().getCanRemove()).isFalse();

        MemberDetailVO selfView = assembler.memberDetail(farm, manager, manager,
                new AccountBrief(10002L, "刘芳", "13900001234", "NORMAL", false), FarmStatus.NORMAL);
        assertThat(selfView.getActions().getCanQuit()).isTrue();

        Farm disabledFarm = farm(FarmStatus.DISABLED);
        MemberDetailVO disabledView = assembler.memberDetail(disabledFarm, staff, owner,
                new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false), FarmStatus.DISABLED);
        assertThat(disabledView.getActions().getCanRemove()).isTrue();
        assertThat(disabledView.getActions().getCanChangeRole()).isFalse();
        assertThat(disabledView.getActions().getCanAdjustPens()).isFalse();
    }

    @Test
    void detailContainsFullSections() {
        Farm farm = farm(FarmStatus.NORMAL);
        FarmMember owner = member(5001L, 10001L, "OWNER");
        when(lookup.activeMembers(1001L)).thenReturn(List.of(owner,
                member(5002L, 10002L, "MANAGER"), member(5003L, 10003L, "STAFF")));
        when(lookup.pensOfFarm(1001L)).thenReturn(List.of());

        FarmDetailVO vo = assembler.detail(farm, owner);

        assertThat(vo.getFarmId()).isEqualTo(1001L);
        assertThat(vo.getBasic().getVersion()).isEqualTo(3);
        assertThat(vo.getBasic().getContactPhoneMasked()).isEqualTo("138****6620");
        assertThat(vo.getRegistration().getQualificationCount()).isEqualTo(2);
        assertThat(vo.getMemberSummary().getMemberTotal()).isEqualTo(3);
        assertThat(vo.getMemberSummary().getOwnerCount()).isEqualTo(1);
        assertThat(vo.getPermissions().getCanInvite()).isTrue();
        assertThat(vo.getStatusText()).isEqualTo("正常");
        assertThat(vo.getCurrentStock()).isEqualTo(870);
    }

    @Test
    void memberSummaryAndTransferCandidateCarryAvatarUrls() {
        Farm farm = farm(FarmStatus.NORMAL);
        when(lookup.activeMembers(1001L)).thenReturn(List.of(member(5001L, 10001L, "OWNER"),
                member(5002L, 10002L, "MANAGER"), member(5003L, 10003L, "STAFF")));
        when(lookup.pensOfFarm(1001L)).thenReturn(List.of());
        when(accountQuery.avatarUrl(10001L)).thenReturn("http://8.208.12.217/zmt-private/avatar/a.png?X-Amz-Signature=1");
        when(accountQuery.avatarUrl(10002L)).thenReturn(null);
        when(accountQuery.avatarUrl(10003L)).thenReturn("http://8.208.12.217/zmt-private/avatar/c.png?X-Amz-Signature=3");

        MemberSummaryVO summary = assembler.memberSummary(farm);

        // App 仍按 avatars 显示首字；Web 用同序的 avatar_urls 显示照片，没有头像的位置为 null
        assertThat(summary.getAvatars()).containsExactly("王", "刘", "李");
        assertThat(summary.getAvatarUrls()).containsExactly(
                "http://8.208.12.217/zmt-private/avatar/a.png?X-Amz-Signature=1", null,
                "http://8.208.12.217/zmt-private/avatar/c.png?X-Amz-Signature=3");

        TransferCandidateVO candidate = assembler.candidate(member(5003L, 10003L, "MANAGER"),
                new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false));
        assertThat(candidate.getAvatarUrl()).isEqualTo("http://8.208.12.217/zmt-private/avatar/c.png?X-Amz-Signature=3");
        TransferCandidateVO cancelled = assembler.candidate(member(5003L, 10003L, "MANAGER"),
                new AccountBrief(10003L, "李明", "13700005678", "CANCELLED", true));
        assertThat(cancelled.getAvatarUrl()).isNull();
    }

    @Test
    void acceptedInviteRemainsSuccessfulAfterItsOldExpiryDate() {
        var invite = new com.zhimu.server.biz.farm.entity.FarmInvite();
        invite.setId(7001L);
        invite.setFarmId(1001L);
        invite.setInviteePhone("13800006621");
        invite.setRole("STAFF");
        invite.setStatus("ACCEPTED");
        invite.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        invite.setExpiresAt(LocalDateTime.of(2026, 10, 8, 9, 0));
        invite.setHandledAt(LocalDateTime.of(2026, 10, 2, 10, 0));
        when(invitePenMapper.selectList(any())).thenReturn(List.of());
        var item = assembler.inviteItem(farm(FarmStatus.NORMAL), invite, true);
        assertThat(item.getStatus()).isEqualTo("ACCEPTED");
        assertThat(item.getStatusText()).isEqualTo("邀请成功");
        assertThat(item.getCanReinvite()).isFalse();
        assertThat(item.getHandledAt()).isEqualTo("2026-10-02 10:00:00");
        assertThat(assembler.inviteItem(farm(FarmStatus.NORMAL), invite, false).getInviteePhone()).isNull();
    }

    private static Farm farm(FarmStatus status) {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setOwnerUserId(10001L);
        farm.setMainCategoryId(101L);
        farm.setScale(1500);
        farm.setContactName("王建国");
        farm.setContactPhone("13800006620");
        farm.setIntro("简介");
        farm.setAddress("地址");
        farm.setProvince("内蒙古自治区");
        farm.setCity("赤峰市");
        farm.setDistrict("松山区");
        farm.setTownship("太平地镇");
        farm.setAdcode("150404");
        farm.setLongitude(BigDecimal.valueOf(118.85));
        farm.setLatitude(BigDecimal.valueOf(42.30));
        farm.setAuditStatus(status == FarmStatus.PENDING ? "PENDING"
                : status == FarmStatus.REJECTED ? "REJECTED" : "APPROVED");
        farm.setDisabled(status == FarmStatus.DISABLED);
        farm.setFrozen(status == FarmStatus.FROZEN);
        farm.setVersion(3);
        farm.setCreatedAt(LocalDateTime.of(2026, 3, 2, 9, 0));
        farm.setDeleted(false);
        return farm;
    }

    private static FarmMember member(Long id, Long userId, String role) {
        FarmMember member = new FarmMember();
        member.setId(id);
        member.setFarmId(1001L);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        member.setJoinType("OWNER".equals(role) ? "CREATE" : "INVITE");
        member.setJoinedAt(LocalDateTime.of(2026, 3, 2, 9, 0));
        member.setVersion(1);
        return member;
    }

    private static MemberPen memberPen(Long memberId, Long penId) {
        MemberPen pen = new MemberPen();
        pen.setMemberId(memberId);
        pen.setPenId(penId);
        return pen;
    }

    private static PenInfo pen(Long id, String code, String name) {
        return new PenInfo(id, 1001L, code, name, "FATTENING", "育肥舍", 500, "ENABLED");
    }

    private static MediaItemVO media(Long fileId) {
        MediaItemVO item = new MediaItemVO();
        item.setFileId(fileId);
        item.setUrl("https://files/" + fileId + ".jpg");
        item.setThumbUrl("https://files/" + fileId + "-thumb.jpg");
        item.setMediaType("IMAGE");
        item.setWidth(640);
        item.setHeight(480);
        item.setStatus("READY");
        item.setSortNo(1);
        return item;
    }

    private static String snapshot() {
        return CacheJson.write(new com.zhimu.server.biz.farm.support.FarmSnapshot(
                "北湾牛场", 101L, 1500, "王建国", "13800006620", "地址", "内蒙古自治区", "赤峰市",
                "松山区", "太平地镇", "150404", BigDecimal.valueOf(118.85), BigDecimal.valueOf(42.30),
                null, List.of(9001L, 9002L)));
    }
}

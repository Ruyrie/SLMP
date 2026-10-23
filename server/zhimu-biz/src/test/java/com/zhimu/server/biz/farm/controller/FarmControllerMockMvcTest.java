package com.zhimu.server.biz.farm.controller;

import com.zhimu.server.biz.farm.FarmTestMvc;
import com.zhimu.server.biz.farm.service.FarmAuditService;
import com.zhimu.server.biz.farm.service.FarmChangeService;
import com.zhimu.server.biz.farm.service.FarmInviteService;
import com.zhimu.server.biz.farm.service.FarmMemberService;
import com.zhimu.server.biz.farm.service.FarmService;
import com.zhimu.server.biz.farm.service.FarmTransferService;
import com.zhimu.server.biz.farm.vo.FarmAuditVO;
import com.zhimu.server.biz.farm.vo.FarmBasicVO;
import com.zhimu.server.biz.farm.vo.FarmChangeVO;
import com.zhimu.server.biz.farm.vo.FarmContextVO;
import com.zhimu.server.biz.farm.vo.FarmDetailVO;
import com.zhimu.server.biz.farm.vo.FarmListItemVO;
import com.zhimu.server.biz.farm.vo.InviteAcceptVO;
import com.zhimu.server.biz.farm.vo.InviteItemVO;
import com.zhimu.server.biz.farm.vo.MemberChangeResultVO;
import com.zhimu.server.biz.farm.vo.MemberDetailVO;
import com.zhimu.server.biz.farm.vo.MemberItemVO;
import com.zhimu.server.biz.farm.vo.MemberRemoveResultVO;
import com.zhimu.server.biz.farm.vo.MemberSummaryVO;
import com.zhimu.server.biz.farm.vo.NameCheckVO;
import com.zhimu.server.biz.farm.vo.PenOptionVO;
import com.zhimu.server.biz.farm.vo.RemovalImpactVO;
import com.zhimu.server.biz.farm.vo.TransferAcceptVO;
import com.zhimu.server.biz.farm.vo.TransferPageVO;
import com.zhimu.server.biz.farm.vo.TransferVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmControllerMockMvcTest {

    private static final String AUTH = "Bearer test";
    private static final String JSON = "application/json";

    @Mock private FarmService farmService;
    @Mock private FarmAuditService auditService;
    @Mock private FarmChangeService changeService;
    @Mock private FarmMemberService memberService;
    @Mock private FarmInviteService inviteService;
    @Mock private FarmTransferService transferService;
    @Mock private FarmMemberCache memberCache;

    private MockMvc plain;
    private MockMvc guarded;

    /** TraceFilter 每个请求结束会清理 ThreadLocal，测试里用拦截器模拟 AuthInterceptor 写入登录用户。 */
    private static final org.springframework.web.servlet.HandlerInterceptor AUTH_STUB =
            new org.springframework.web.servlet.HandlerInterceptor() {
                @Override
                public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                         jakarta.servlet.http.HttpServletResponse response, Object handler) {
                    UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "test-jti"));
                    return true;
                }
            };

    @BeforeEach
    void setUp() {
        FarmController farmController = new FarmController(farmService, auditService, changeService);
        FarmMemberController memberController = new FarmMemberController(memberService);
        FarmInviteController inviteController = new FarmInviteController(inviteService);
        FarmTransferController transferController = new FarmTransferController(transferService);
        plain = FarmTestMvc.builderWithInterceptors(
                new Object[]{farmController, memberController, inviteController, transferController},
                AUTH_STUB).build();
        guarded = FarmTestMvc.builderWithInterceptors(
                new Object[]{farmController, memberController, inviteController, transferController},
                AUTH_STUB, new FarmContextInterceptor(memberCache)).build();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "test-jti"));
        FarmContext.clear();
        stubSuccess();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void f01ToF14RoutesReturnSuccess() throws Exception {
        plain.perform(get("/api/v1/farms").param("relation", "ALL").param("page", "1").param("size", "20")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tab_counts.all").value(3));

        plain.perform(get("/api/v1/farms/1001/context").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.farm_id").value(1001));

        plain.perform(post("/api/v1/farms/1001/switch").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(get("/api/v1/farms/name-check").param("name", "北湾牛场").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true));

        plain.perform(post("/api/v1/farms").header("Authorization", AUTH)
                        .header("Idempotency-Key", "k1").contentType(JSON)
                        .content("""
                                {"name":"北湾牛场","main_category_id":101,"scale":120,
                                 "contact_name":"王建国","contact_phone":"13800006620",
                                 "address":"赤峰市松山区","province":"内蒙古自治区","city":"赤峰市",
                                 "district":"松山区","adcode":"150404",
                                 "longitude":118.9,"latitude":42.3,"qualification_file_ids":[9001]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(get("/api/v1/farms/1003/audit").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.farm_id").value(1003));

        plain.perform(put("/api/v1/farms/1003/registration").header("Authorization", AUTH)
                        .header("Idempotency-Key", "k2").contentType(JSON)
                        .content("""
                                {"name":"北湾牛场","main_category_id":101,"scale":120,
                                 "contact_name":"王建国","contact_phone":"13800006620",
                                 "address":"赤峰市松山区","province":"内蒙古自治区","city":"赤峰市",
                                 "district":"松山区","adcode":"150404","longitude":118.9,"latitude":42.3,
                                 "qualification_file_ids":[9001],"audit_version":0}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(delete("/api/v1/farms/1003").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已删除北湾牛场"));

        plain.perform(get("/api/v1/farms/1001").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.farm_id").value(1001));

        plain.perform(patch("/api/v1/farms/1001/basic").header("Authorization", AUTH).contentType(JSON)
                        .content("{\"contact_name\":\"王建军\",\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(4));

        plain.perform(post("/api/v1/farms/1001/disable").header("Authorization", AUTH).contentType(JSON)
                        .content("{\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("已停用东山猪场"));

        plain.perform(post("/api/v1/farms/1001/enable").header("Authorization", AUTH).contentType(JSON)
                        .content("{\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("已重新启用东山猪场"));

        plain.perform(get("/api/v1/farms/1001/changes/latest").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

        plain.perform(post("/api/v1/farms/1001/changes").header("Authorization", AUTH)
                        .header("Idempotency-Key", "k3").contentType(JSON)
                        .content("""
                                {"name":"东山猪场","address":"新地址","province":"内蒙古自治区",
                                 "city":"赤峰市","district":"松山区","adcode":"150404",
                                 "longitude":118.86,"latitude":42.31,"qualification_file_ids":[9001]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void f15ToF27RoutesReturnSuccess() throws Exception {
        plain.perform(get("/api/v1/farms/1001/member-summary").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.member_total").value(4));

        plain.perform(get("/api/v1/farms/1001/members").param("role", "ALL")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4));

        plain.perform(get("/api/v1/farms/1001/members/5003").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.member_id").value(5003));

        plain.perform(get("/api/v1/farms/1001/pen-options").param("member_id", "5003")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].pen_id").value(2003));

        plain.perform(get("/api/v1/farms/1001/invites").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));

        plain.perform(post("/api/v1/farms/1001/invites").header("Authorization", AUTH)
                        .header("Idempotency-Key", "k4").contentType(JSON)
                        .content("{\"invitee_phone\":\"15804763306\",\"role\":\"STAFF\",\"pen_ids\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(post("/api/v1/farm-invites/6001/accept").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("已加入东山猪场"));

        plain.perform(post("/api/v1/farm-invites/6001/reject").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("已拒绝邀请"));

        plain.perform(put("/api/v1/farms/1001/members/5003/role").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"role\":\"MANAGER\",\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(put("/api/v1/farms/1001/members/5003/pens").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"pen_ids\":[2004],\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(get("/api/v1/farms/1001/members/5003/removal-impact").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.member_id").value(5003));

        plain.perform(delete("/api/v1/farms/1001/members/5003").param("version", "3")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(post("/api/v1/farms/1001/quit").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("你已退出东山猪场"));
    }

    @Test
    void f28ToF32RoutesReturnSuccess() throws Exception {
        plain.perform(get("/api/v1/farms/1001/transfer").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates[0].member_id").value(5002));

        plain.perform(post("/api/v1/farms/1001/transfers").header("Authorization", AUTH)
                        .header("Idempotency-Key", "k5").contentType(JSON)
                        .content("{\"to_member_id\":5002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已向刘芳发送转让请求，对方确认后生效"));

        plain.perform(post("/api/v1/farm-transfers/7001/accept").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("你已成为东山猪场的所有者"));

        plain.perform(post("/api/v1/farm-transfers/7001/reject").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        plain.perform(post("/api/v1/farm-transfers/7001/cancel").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void businessErrorsBecomeHttpStatusAndFieldErrors() throws Exception {
        plain.perform(get("/api/v1/farms/1001/members/5003").header("Authorization", AUTH))
                .andExpect(status().isOk());
        org.mockito.Mockito.when(memberService.detail(eq(1001L), eq(5009L), anyLong()))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "参数错误",
                        Map.of("field_errors", List.of(new FieldErrorVO("keyword", "搜索内容不能超过 20 个字")))));

        plain.perform(get("/api/v1/farms/1001/members/5009").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.field_errors[0].field").value("keyword"));
    }

    @Test
    void invitePenErrorOnlyHasMessageWithoutFieldErrors() throws Exception {
        org.mockito.Mockito.when(inviteService.send(eq(1001L), anyLong(), any()))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "栏舍已停用，请重新选择"));

        plain.perform(post("/api/v1/farms/1001/invites").header("Authorization", AUTH)
                        .header("Idempotency-Key", "k6").contentType(JSON)
                        .content("{\"invitee_phone\":\"15804763306\",\"role\":\"STAFF\",\"pen_ids\":[9999]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("栏舍已停用，请重新选择"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void statusAndRoleGuardsFromFarmContextInterceptor() throws Exception {
        org.mockito.Mockito.when(memberCache.get(1001L, 10001L)).thenReturn(member(1001L, "东山猪场",
                FarmRole.STAFF, FarmStatus.NORMAL, 5003L, Set.of(2003L)));
        guarded.perform(get("/api/v1/farms/1001").header("Authorization", AUTH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));

        org.mockito.Mockito.when(memberCache.get(1001L, 10001L)).thenReturn(member(1001L, "东山猪场",
                FarmRole.OWNER, FarmStatus.FROZEN, 5001L, Set.of()));
        guarded.perform(get("/api/v1/farms/1001").header("Authorization", AUTH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40306));

        org.mockito.Mockito.when(memberCache.get(1003L, 10001L)).thenReturn(member(1003L, "北湾牛场",
                FarmRole.OWNER, FarmStatus.PENDING, 5001L, Set.of()));
        guarded.perform(post("/api/v1/farms/1003/switch").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        org.mockito.Mockito.when(memberCache.get(1003L, 10001L)).thenReturn(member(1003L, "北湾牛场",
                FarmRole.OWNER, FarmStatus.REJECTED, 5001L, Set.of()));
        guarded.perform(post("/api/v1/farms/1003/switch").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        guarded.perform(get("/api/v1/farms/1003/context").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        guarded.perform(get("/api/v1/farms/1003/audit").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    private void stubSuccess() {
        FarmListItemVO item = new FarmListItemVO();
        item.setFarmId(1001L);
        PageVO<FarmListItemVO> page = new PageVO<>(List.of(item), 3, 1, 20);
        page.setTabCounts(Map.of("all", 3, "created", 2, "joined", 1, "pending_audit", 1));
        lenient().when(farmService.listFarms(anyLong(), any(), any())).thenReturn(page);

        FarmContextVO context = new FarmContextVO();
        context.setFarmId(1001L);
        lenient().when(farmService.getContext(anyLong(), anyLong())).thenReturn(context);
        lenient().when(farmService.switchFarm(anyLong(), anyLong())).thenReturn(context);
        lenient().when(farmService.checkName(anyLong(), any())).thenAnswer(invocation -> {
            NameCheckVO vo = new NameCheckVO();
            vo.setAvailable(true);
            return vo;
        });

        FarmAuditVO audit = new FarmAuditVO();
        audit.setFarmId(1003L);
        lenient().when(auditService.create(anyLong(), any())).thenReturn(audit);
        lenient().when(auditService.getAudit(anyLong(), anyLong())).thenReturn(audit);
        lenient().when(auditService.resubmit(anyLong(), anyLong(), any())).thenReturn(audit);
        lenient().when(farmService.deleteFarm(anyLong(), anyLong())).thenReturn("北湾牛场");

        FarmDetailVO detail = new FarmDetailVO();
        detail.setFarmId(1001L);
        detail.setName("东山猪场");
        lenient().when(farmService.detail(anyLong(), anyLong())).thenReturn(detail);
        lenient().when(farmService.disable(anyLong(), anyLong(), any())).thenReturn(detail);
        lenient().when(farmService.enable(anyLong(), anyLong(), any())).thenReturn(detail);
        FarmBasicVO basic = new FarmBasicVO();
        basic.setVersion(4);
        lenient().when(farmService.updateBasic(anyLong(), anyLong(), any())).thenReturn(basic);

        lenient().when(changeService.latest(anyLong(), anyLong())).thenReturn(null);
        FarmChangeVO change = new FarmChangeVO();
        change.setChangeId(21L);
        lenient().when(changeService.submit(anyLong(), anyLong(), any())).thenReturn(change);

        MemberSummaryVO summary = new MemberSummaryVO();
        summary.setMemberTotal(4);
        lenient().when(memberService.summary(anyLong(), anyLong())).thenReturn(summary);

        PageVO<MemberItemVO> memberPage = new PageVO<>(List.of(new MemberItemVO()), 4, 1, 20);
        lenient().when(memberService.list(anyLong(), anyLong(), any())).thenReturn(memberPage);

        MemberDetailVO memberDetail = new MemberDetailVO();
        memberDetail.setMemberId(5003L);
        memberDetail.setName("李明");
        memberDetail.setRole("STAFF");
        lenient().when(memberService.detail(anyLong(), anyLong(), anyLong())).thenReturn(memberDetail);

        PenOptionVO penOption = new PenOptionVO();
        penOption.setPenId(2003L);
        lenient().when(memberService.penOptions(anyLong(), any(), anyLong())).thenReturn(List.of(penOption));

        MemberChangeResultVO changeResult = new MemberChangeResultVO();
        MemberDetailVO changed = new MemberDetailVO();
        changed.setName("李明");
        changed.setRole("MANAGER");
        changeResult.setMember(changed);
        lenient().when(memberService.updateRole(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(changeResult);
        changed.setRole("STAFF");
        lenient().when(memberService.updatePens(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(changeResult);

        RemovalImpactVO impact = new RemovalImpactVO();
        impact.setMemberId(5003L);
        lenient().when(memberService.removalImpact(anyLong(), anyLong(), anyLong())).thenReturn(impact);
        lenient().when(memberService.remove(anyLong(), anyLong(), anyLong(), anyInt()))
                .thenReturn(new MemberRemoveResultVO());
        lenient().when(memberService.quit(anyLong(), anyLong())).thenReturn("东山猪场");

        PageVO<InviteItemVO> invitePage = new PageVO<>(List.of(new InviteItemVO()), 2, 1, 20);
        lenient().when(inviteService.list(anyLong(), anyLong(), any())).thenReturn(invitePage);
        lenient().when(inviteService.send(anyLong(), anyLong(), any())).thenReturn(new InviteItemVO());
        InviteAcceptVO accept = new InviteAcceptVO();
        accept.setFarmId(1001L);
        accept.setFarmName("东山猪场");
        accept.setRole("STAFF");
        lenient().when(inviteService.accept(anyLong(), anyLong())).thenReturn(accept);

        TransferPageVO transferPage = new TransferPageVO();
        com.zhimu.server.biz.farm.vo.TransferCandidateVO candidate =
                new com.zhimu.server.biz.farm.vo.TransferCandidateVO();
        candidate.setMemberId(5002L);
        transferPage.setCandidates(List.of(candidate));
        lenient().when(transferService.page(anyLong(), anyLong())).thenReturn(transferPage);
        TransferVO transfer = new TransferVO();
        transfer.setToName("刘芳");
        transfer.setFarmName("东山猪场");
        lenient().when(transferService.create(anyLong(), anyLong(), any())).thenReturn(transfer);
        lenient().when(transferService.cancel(anyLong(), anyLong())).thenReturn(transfer);
        TransferAcceptVO transferAccept = new TransferAcceptVO();
        transferAccept.setFarmName("东山猪场");
        lenient().when(transferService.accept(anyLong(), anyLong())).thenReturn(transferAccept);
    }

    private static FarmContext.FarmMember member(Long farmId, String farmName, FarmRole role,
                                                 FarmStatus status, Long memberId, Set<Long> penIds) {
        return new FarmContext.FarmMember(farmId, farmName, 10001L, memberId, role, penIds, status);
    }
}

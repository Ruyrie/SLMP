package com.zhimu.server.biz.farm.service;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.request.CreateFarmRequest;
import com.zhimu.server.biz.farm.request.UpdateRegistrationRequest;
import com.zhimu.server.biz.farm.service.impl.FarmAuditServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNameLock;
import com.zhimu.server.biz.farm.vo.FarmAuditVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmAuditServiceImplTest {

    @Mock private FarmMapper farmMapper;
    @Mock private FarmAuditMapper auditMapper;
    @Mock private FarmMemberMapper memberMapper;
    @Mock private FarmLookup lookup;
    @Mock private FarmAssembler assembler;
    @Mock private FarmNameGuard nameGuard;
    @Mock private FarmNameLock nameLock;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private BizMediaService bizMedia;
    @Mock private BaseDataQueryService baseData;

    private FarmAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmAuditServiceImpl(farmMapper, auditMapper, memberMapper, lookup, assembler,
                nameGuard, nameLock, cache, logs, bizMedia, baseData);
        when(baseData.getCategory(101L))
                .thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD", "头", false, true));
        lenient().doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(nameLock).call(anyLong(), any());
        lenient().when(farmMapper.insert(any(Farm.class))).thenAnswer(invocation -> {
            Farm farm = invocation.getArgument(0);
            farm.setId(1003L);
            return 1;
        });
        lenient().when(memberMapper.insert(any(FarmMember.class))).thenAnswer(invocation -> {
            FarmMember member = invocation.getArgument(0);
            member.setId(5001L);
            return 1;
        });
        lenient().when(auditMapper.insert(any(FarmAudit.class))).thenAnswer(invocation -> {
            FarmAudit audit = invocation.getArgument(0);
            audit.setId(3001L);
            return 1;
        });
        lenient().when(farmMapper.update(any(Farm.class), any())).thenReturn(1);
        lenient().when(assembler.audit(any(), any(), any())).thenReturn(new FarmAuditVO());
    }

    @Test
    void createInsertsFarmOwnerMemberAndPendingAudit() {
        CreateFarmRequest request = request();
        service.create(10001L, request);

        ArgumentCaptor<Farm> farmCaptor = ArgumentCaptor.forClass(Farm.class);
        verify(farmMapper).insert(farmCaptor.capture());
        Farm farm = farmCaptor.getValue();
        assertThat(farm.getName()).isEqualTo("北湾牛场");
        assertThat(farm.getOwnerUserId()).isEqualTo(10001L);
        assertThat(farm.getAuditStatus()).isEqualTo("PENDING");
        assertThat(farm.getDeleted()).isFalse();

        ArgumentCaptor<FarmMember> memberCaptor = ArgumentCaptor.forClass(FarmMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo("OWNER");
        assertThat(memberCaptor.getValue().getJoinType()).isEqualTo("CREATE");
        assertThat(memberCaptor.getValue().getActiveFlag()).isTrue();

        ArgumentCaptor<FarmAudit> auditCaptor = ArgumentCaptor.forClass(FarmAudit.class);
        verify(auditMapper).insert(auditCaptor.capture());
        FarmAudit audit = auditCaptor.getValue();
        assertThat(audit.getSubmitNo()).isEqualTo(1);
        assertThat(audit.getStatus()).isEqualTo("PENDING");
        assertThat(audit.getPendingFlag()).isTrue();
        assertThat(audit.getSnapshotJson()).contains("北湾牛场");
        verify(bizMedia).validateForBind(any(), any(), anyLong(), any());
        verify(bizMedia).replace(any(), anyLong(), anyLong(), any());
    }

    @Test
    void approvedFarmAuditReturnsOnlyFourFields() {
        Farm farm = farm("APPROVED", false, false);
        FarmMember owner = member("OWNER");
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(owner);

        FarmAuditVO vo = service.getAudit(1001L, 10001L);

        assertThat(vo.getFarmId()).isEqualTo(1001L);
        assertThat(vo.getName()).isEqualTo("东山猪场");
        assertThat(vo.getStatus()).isEqualTo("NORMAL");
        assertThat(vo.getStatusText()).isEqualTo("已通过");
        assertThat(vo.getRegistration()).isNull();
        assertThat(vo.getRecords()).isNull();
    }

    @Test
    void disabledAndFrozenApprovedFarmAlsoReturnFourFields() {
        Farm farm = farm("APPROVED", true, false);
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("OWNER"));
        assertThat(service.getAudit(1001L, 10001L).getStatusText()).isEqualTo("已停用");

        farm.setDisabled(false);
        farm.setFrozen(true);
        assertThat(service.getAudit(1001L, 10001L).getStatus()).isEqualTo("FROZEN");
        assertThat(service.getAudit(1001L, 10001L).getStatusText()).isEqualTo("已冻结");
    }

    @Test
    void nonOwnerCannotReadAudit() {
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm("APPROVED", false, false));
        when(lookup.findActiveMember(1001L, 10002L)).thenReturn(member("MANAGER"));

        assertThatThrownBy(() -> service.getAudit(1001L, 10002L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40303);
    }

    @Test
    void resubmitWithWrongAuditVersionReturns40902() {
        Farm farm = farm("PENDING", false, false);
        FarmAudit latest = latestAudit("PENDING", 2);
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("OWNER"));
        when(auditMapper.selectOne(any())).thenReturn(latest);
        UpdateRegistrationRequest request = request();
        request.setAuditVersion(1);

        assertThatThrownBy(() -> service.resubmit(1001L, 10001L, request))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40902);
    }

    @Test
    void resubmitPendingOverwritesSnapshotAndBumpsVersions() {
        Farm farm = farm("PENDING", false, false);
        farm.setVersion(3);
        FarmAudit latest = latestAudit("PENDING", 0);
        latest.setSnapshotJson(CacheJson.write(new com.zhimu.server.biz.farm.support.FarmSnapshot(
                "北湾牛场", 101L, 120, "王建国", "13800006620", "旧地址", "内蒙古自治区", "赤峰市",
                "松山区", "太平地镇", "150404", BigDecimal.valueOf(118.9), BigDecimal.valueOf(42.3),
                null, List.of(9001L))));
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("OWNER"));
        when(auditMapper.selectOne(any())).thenReturn(latest);
        when(auditMapper.updateById(any(FarmAudit.class))).thenReturn(1);
        when(auditMapper.selectList(any())).thenReturn(List.of(latest));
        when(assembler.snapshot(any(), any())).thenReturn(
                new com.zhimu.server.biz.farm.support.FarmSnapshot("北湾牛场", 101L, 120,
                        "王建国", "13800006620", "旧地址", "内蒙古自治区", "赤峰市", "松山区",
                        "太平地镇", "150404", BigDecimal.valueOf(118.9), BigDecimal.valueOf(42.3),
                        null, List.of(9001L)));
        UpdateRegistrationRequest request = request();
        request.setAuditVersion(0);

        service.resubmit(1001L, 10001L, request);

        verify(auditMapper).updateById(any(FarmAudit.class));
        verify(farmMapper).update(any(Farm.class), any());
        assertThat(latest.getSubmittedAt()).isNotNull();
        assertThat(latest.getSnapshotJson()).contains("北湾牛场");
    }

    private static UpdateRegistrationRequest request() {
        UpdateRegistrationRequest request = new UpdateRegistrationRequest();
        request.setName("北湾牛场");
        request.setMainCategoryId(101L);
        request.setScale(120);
        request.setContactName("王建国");
        request.setContactPhone("13800006620");
        request.setAddress("赤峰市松山区太平地镇");
        request.setProvince("内蒙古自治区");
        request.setCity("赤峰市");
        request.setDistrict("松山区");
        request.setTownship("太平地镇");
        request.setAdcode("150404");
        request.setLongitude(BigDecimal.valueOf(118.9));
        request.setLatitude(BigDecimal.valueOf(42.3));
        request.setQualificationFileIds(List.of(9001L));
        return request;
    }

    private static Farm farm(String auditStatus, boolean disabled, boolean frozen) {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setOwnerUserId(10001L);
        farm.setMainCategoryId(101L);
        farm.setAuditStatus(auditStatus);
        farm.setDisabled(disabled);
        farm.setFrozen(frozen);
        farm.setVersion(3);
        farm.setDeleted(false);
        return farm;
    }

    private static FarmMember member(String role) {
        FarmMember member = new FarmMember();
        member.setId(5001L);
        member.setFarmId(1001L);
        member.setUserId(10001L);
        member.setRole(role);
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        return member;
    }

    private static FarmAudit latestAudit(String status, int version) {
        FarmAudit audit = new FarmAudit();
        audit.setId(3002L);
        audit.setFarmId(1001L);
        audit.setSubmitNo(2);
        audit.setStatus(status);
        audit.setPendingFlag(true);
        audit.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 8, 46));
        audit.setVersion(version);
        audit.setReshootFileIds("[]");
        return audit;
    }
}

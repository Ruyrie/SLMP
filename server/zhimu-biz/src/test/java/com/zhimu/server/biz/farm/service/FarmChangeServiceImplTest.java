package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.request.SubmitFarmChangeRequest;
import com.zhimu.server.biz.farm.service.impl.FarmChangeServiceImpl;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNameLock;
import com.zhimu.server.biz.farm.support.FarmSnapshot;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H11：资料变更审核完后，再次提交保留原资质图不能被判为“已用在其他记录中”。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmChangeServiceImplTest {

    @Mock private FarmChangeMapper changeMapper;
    @Mock private FarmLookup lookup;
    @Mock private FarmAssembler assembler;
    @Mock private FarmNameGuard nameGuard;
    @Mock private FarmNameLock nameLock;
    @Mock private FarmLogs logs;
    @Mock private BizMediaService bizMedia;

    private FarmChangeServiceImpl service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmChangeServiceImpl(changeMapper, lookup, assembler, nameGuard, nameLock, logs, bizMedia);
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("西山猪场");
        farm.setAuditStatus("APPROVED");
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        FarmMember owner = new FarmMember();
        owner.setRole("OWNER");
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(owner);
        when(assembler.snapshotOf(farm)).thenReturn(new FarmSnapshot("西山猪场", 1L, 100, "王建国", "13800006620",
                "旧地址", "内蒙古自治区", "赤峰市", "松山区", null, "150404", new BigDecimal("118.9"),
                new BigDecimal("42.2"), 10, List.of(501L)));
        FarmChange approved = new FarmChange();
        approved.setId(31L);
        FarmChange rejected = new FarmChange();
        rejected.setId(32L);
        when(changeMapper.selectOne(any())).thenReturn(null);
        when(changeMapper.selectList(any())).thenReturn(List.of(approved, rejected));
    }

    @Test
    void submitAllowsQualificationFilesFromReviewedChanges() {
        SubmitFarmChangeRequest request = new SubmitFarmChangeRequest();
        request.setName("西山猪场");
        request.setAddress("新地址 1 号");
        request.setProvince("内蒙古自治区");
        request.setCity("赤峰市");
        request.setDistrict("松山区");
        request.setAdcode("150404");
        request.setLongitude(new BigDecimal("118.9"));
        request.setLatitude(new BigDecimal("42.2"));
        request.setQualificationFileIds(List.of(501L, 502L));

        service.submit(1001L, 10001L, request);

        ArgumentCaptor<BizRef[]> refs = ArgumentCaptor.forClass(BizRef[].class);
        verify(bizMedia).validateForBind(eq(List.of(501L, 502L)), eq(FileScene.FARM_QUAL), eq(10001L),
                eq("qualification_file_ids"), refs.capture());
        assertThat(refs.getValue()).containsExactlyInAnyOrder(
                new BizRef(BizType.FARM, 1001L),
                new BizRef(BizType.FARM_CHANGE, 31L),
                new BizRef(BizType.FARM_CHANGE, 32L));
    }
}

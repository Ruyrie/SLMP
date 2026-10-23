package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.fixture.CommonFlowFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChangeRequestService：待审核计数与系统撤销（模块01 注销、模块02 删除养殖场调用）。
 */
class ChangeRequestServiceTest {

    private CommonFlowFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CommonFlowFixture();
        CommonFlowFixture.login(10001L);
        fixture.requests.add(request(9001L, 1001L, 10001L, "PENDING"));
        fixture.requests.add(request(9002L, 1002L, 10001L, "PENDING"));
        fixture.requests.add(request(9003L, 1001L, 10001L, "APPROVED"));
        fixture.requests.add(request(9004L, 1001L, 10002L, "PENDING"));
    }

    @AfterEach
    void tearDown() {
        CommonFlowFixture.logout();
    }

    @Test
    void countsPendingBySubmitterAcrossFarms() {
        assertThat(fixture.changeRequestService.countPendingSubmittedBy(10001L, null)).isEqualTo(2);
        assertThat(fixture.changeRequestService.countPendingSubmittedBy(10001L, 1001L)).isEqualTo(1);
        assertThat(fixture.changeRequestService.countPendingSubmittedBy(10004L, null)).isZero();
        assertThat(fixture.changeRequestService.countPendingSubmittedBy(null, null)).isZero();
    }

    @Test
    void cancelPendingBySubmitterMarksCanceledAndClearsPendingFlag() {
        int count = fixture.changeRequestService.cancelPendingBySubmitter(10001L, "提交人已注销账号");

        assertThat(count).isEqualTo(2);
        ChangeRequest first = fixture.requests.get(0);
        assertThat(first.getStatus()).isEqualTo("CANCELED");
        assertThat(first.getPendingFlag()).isNull();
        assertThat(first.getCancelReason()).isEqualTo("提交人已注销账号");
        assertThat(first.getVersion()).isEqualTo(1);
        // 别人的申请与已通过的申请不受影响
        assertThat(fixture.requests.get(2).getStatus()).isEqualTo("APPROVED");
        assertThat(fixture.requests.get(3).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void cancelPendingByFarmOnlyAffectsThatFarm() {
        int count = fixture.changeRequestService.cancelPendingByFarm(1002L, "养殖场已删除");

        assertThat(count).isEqualTo(1);
        assertThat(fixture.requests.get(1).getStatus()).isEqualTo("CANCELED");
        assertThat(fixture.requests.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(fixture.requests.get(1).getCancelReason()).isEqualTo("养殖场已删除");
    }

    private static ChangeRequest request(Long id, Long farmId, Long submittedBy, String status) {
        ChangeRequest request = new ChangeRequest();
        request.setId(id);
        request.setRequestNo("CR2026101900" + id);
        request.setFarmId(farmId);
        request.setBizType("FEED_RECORD");
        request.setBizId(7001L);
        request.setSubmittedBy(submittedBy);
        request.setStatus(status);
        request.setPendingFlag("PENDING".equals(status) ? 1 : null);
        request.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 9, 0));
        request.setVersion(0);
        return request;
    }
}

package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.vo.BannedDrugHitVO;
import com.zhimu.server.integration.ai.BannedDrugGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** BannedDrugGuard 实现：模块07/11 的 AI 问答文本命中禁用药时返回统一 Hit。 */
@ExtendWith(MockitoExtension.class)
class BannedDrugGuardImplTest {

    @Mock private DrugCatalogQueryService drugCatalogQueryService;
    @InjectMocks private BannedDrugGuardImpl guard;

    @Test
    void mapsServiceHits() {
        when(drugCatalogQueryService.checkBannedDrugs("猪咳嗽可以用瘦肉精吗"))
                .thenReturn(List.of(BannedDrugHitVO.of(9901L, "盐酸克仑特罗", "盐酸克仑特罗为禁用药，不能使用")));

        List<BannedDrugGuard.Hit> hits = guard.check("猪咳嗽可以用瘦肉精吗");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).drugId()).isEqualTo(9901L);
        assertThat(hits.get(0).drugName()).isEqualTo("盐酸克仑特罗");
        assertThat(hits.get(0).matchedWord()).isEqualTo("盐酸克仑特罗");
    }

    @Test
    void returnsEmptyWhenClean() {
        when(drugCatalogQueryService.checkBannedDrugs("阿莫西林怎么用")).thenReturn(List.of());
        assertThat(guard.check("阿莫西林怎么用")).isEmpty();
    }
}

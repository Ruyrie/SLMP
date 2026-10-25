package com.zhimu.server.biz.ai.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryAiBatchContextServiceTest {

    private final TemporaryAiBatchContextService service = new TemporaryAiBatchContextService();

    @Test
    void listsPoultryBatchesForWestFarm() {
        var batches = service.listLinkable(1002L, false, null);
        assertThat(batches).hasSize(5);
        assertThat(batches).allSatisfy(batch -> assertThat(batch.getCountUnitText()).isEqualTo("只"));
        assertThat(batches).extracting("batchId").contains(3201L, 3202L, 3211L, 3212L, 3213L);
    }

    @Test
    void staffOnlySeesOwnPens() {
        var batches = service.listLinkable(1002L, true, List.of(2201L));
        assertThat(batches).extracting("batchId").containsExactly(3202L);
    }

    @Test
    void resolvesLinkedBatchForWestFarm() {
        var batch = service.resolve(1002L, 3201L, false, null);
        assertThat(batch).isNotNull();
        assertThat(batch.getBatchNo()).isEqualTo("ZM2610-026");
        assertThat(batch.getPenName()).isEqualTo("2 号鸡舍");
    }

    @Test
    void unknownFarmHasNoBatches() {
        assertThat(service.listLinkable(1003L, false, null)).isEmpty();
    }
}

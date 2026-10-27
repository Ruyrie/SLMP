package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchGrowthQueryServiceTest {
    private final BatchGrowthMapper mapper = mock(BatchGrowthMapper.class);
    private final BatchGrowthQueryServiceImpl service = new BatchGrowthQueryServiceImpl(mapper);

    @Test
    void latestWeightQueryAlwaysRestrictsFarmBatchAndValidMeasurement() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BatchGrowth.class);
        BatchGrowth growth = new BatchGrowth();
        growth.setFarmId(60601L);growth.setBatchId(3276L);
        growth.setLastWeighDate(LocalDate.of(2026, 10, 26));growth.setLastAvgWeight(new BigDecimal("60.0"));
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<BatchGrowth> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("farm_id =", "batch_id IN", "last_weigh_id IS NOT NULL", "last_weigh_date <=", "last_avg_weight >");
            assertThat(query.getParamNameValuePairs()).containsValue(60601L).containsValue(3276L);
            return List.of(growth);
        });
        var result = service.latestWeights(60601L, List.of(3276L));
        assertThat(result.get(3276L).avgWeight()).isEqualByComparingTo("60.0");
    }

    @Test
    void emptyFarmOrBatchSelectionNeverQueriesAllFarms() {
        assertThat(service.latestWeights(null, List.of(3276L))).isEmpty();
        assertThat(service.latestWeights(60601L, List.of())).isEmpty();
        verifyNoInteractions(mapper);
    }
}

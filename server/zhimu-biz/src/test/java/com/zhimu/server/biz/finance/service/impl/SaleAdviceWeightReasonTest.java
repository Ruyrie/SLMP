package com.zhimu.server.biz.finance.service.impl;

import com.zhimu.server.biz.feeding.api.BatchGrowthQueryService;
import com.zhimu.server.biz.finance.FinanceTestTables;
import com.zhimu.server.biz.finance.entity.SaleAdvice;
import com.zhimu.server.biz.finance.mapper.SaleAdviceMapper;
import com.zhimu.server.biz.finance.mapper.SaleAdviceRiskMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.health.api.WithdrawalQueryService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出栏建议：“体重依据”读最近一次称重与品种标准体重；休药状态按有效用药记录（与出栏单拦截同源），
 * 不再读出栏建议表；AI 生成按批次品类写提示词，总时限与输出长度够推理模型返回。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaleAdviceWeightReasonTest {

    @Mock private SaleAdviceMapper adviceMapper;
    @Mock private SaleAdviceRiskMapper riskMapper;
    @Mock private SaleOrderMapper saleOrderMapper;
    @Mock private BatchQueryService batchQueryService;
    @Mock private MarketPriceService marketPriceService;
    @Mock private AiGateway aiGateway;
    @Mock private FinanceSupport support;
    @Mock private BatchGrowthQueryService growthQueryService;
    @Mock private WithdrawalQueryService withdrawalQueryService;

    private SaleAdviceServiceImpl service;

    @BeforeEach
    void setUp() {
        FinanceTestTables.init();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), SaleAdvice.class);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 28, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 1, "jti"));
        service = new SaleAdviceServiceImpl(adviceMapper, riskMapper, saleOrderMapper, batchQueryService,
                marketPriceService, aiGateway, support, growthQueryService, withdrawalQueryService);
        when(support.farmId()).thenReturn(1001L);
        when(support.requireBatch(3001L, 1001L)).thenReturn(new BatchInfo(1001L, 3001L, "ZM2610-031", 11L, 101L,
                LocalDate.of(2026, 7, 20), 200, "ACTIVE", List.of(2003L), 50, 200, 0, 0, 2003L));
        when(support.categoryName(101L)).thenReturn("商品猪");
        when(support.countUnitText(101L)).thenReturn("头");
        when(withdrawalQueryService.statusOf(1001L, 3001L, LocalDate.of(2026, 10, 28))).thenReturn(
                new WithdrawalQueryService.WithdrawalStatus(3001L, LocalDate.of(2026, 11, 2), "氟苯尼考粉",
                        LocalDate.of(2026, 10, 13), 20));
        when(adviceMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        UserContext.clear();
    }

    @Test
    void weightReasonComparesLatestWeighingWithBreedStandard() {
        // 入栏日龄 50 + 已养 100 天 = 150 日龄
        when(growthQueryService.latestWeighing(eq(1001L), eq(3001L), eq(11L), eq(150))).thenReturn(
                new BatchGrowthQueryService.WeighSnapshot(new BigDecimal("98.4"), LocalDate.of(2026, 10, 20), 142,
                        new BigDecimal("102.5"), new BigDecimal("-0.0400"), new BigDecimal("108.0")));

        service.latest(3001L);

        ArgumentCaptor<SaleAdvice> captor = ArgumentCaptor.forClass(SaleAdvice.class);
        verify(adviceMapper).insert(captor.capture());
        assertThat(captor.getValue().getReasonWeight())
                .isEqualTo("最近一次称重（10月20日）均重 98.4 千克，低于同日龄标准 102.5 千克约 4%。当前日龄标准约 108 千克。");
    }

    @Test
    void weightReasonWithoutWeighingFallsBackToStandard() {
        when(growthQueryService.latestWeighing(anyLong(), anyLong(), anyLong(), any())).thenReturn(
                new BatchGrowthQueryService.WeighSnapshot(null, null, null, null, null, new BigDecimal("108.0")));

        service.latest(3001L);

        ArgumentCaptor<SaleAdvice> captor = ArgumentCaptor.forClass(SaleAdvice.class);
        verify(adviceMapper).insert(captor.capture());
        assertThat(captor.getValue().getReasonWeight()).isEqualTo("暂无称重记录；当前 150 日龄品种标准体重约 108 千克。");
    }

    @Test
    void withdrawalComesFromMedicationRecordsNotFromEarlierAdvices() {
        service.latest(3001L);

        ArgumentCaptor<SaleAdvice> captor = ArgumentCaptor.forClass(SaleAdvice.class);
        verify(adviceMapper).insert(captor.capture());
        SaleAdvice advice = captor.getValue();
        assertThat(advice.getGenMode()).isEqualTo("RULE");
        assertThat(advice.getInWithdrawal()).isTrue();
        assertThat(advice.getSellableDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(advice.getWithdrawalDaysLeft()).isEqualTo(5);
        assertThat(advice.getReasonWithdrawal()).isEqualTo("氟苯尼考粉末次用药 10月13日，休药期 20 天，11月2日起可出栏。");
        assertThat(advice.getReasonAge()).isEqualTo("150 日龄，存栏 200 头。");
        org.mockito.Mockito.verifyNoInteractions(aiGateway);
    }

    @Test
    void aiAdviceUsesCategoryPromptLongBudgetAndEnoughTokens() {
        when(withdrawalQueryService.statusOf(any(), any(), any())).thenReturn(null);
        when(support.categoryName(101L)).thenReturn("肉鸡");
        when(support.countUnitText(101L)).thenReturn("只");
        when(aiGateway.isEnabled()).thenReturn(true);
        when(aiGateway.chat(any())).thenReturn(AiChatResult.builder()
                .content("{\"conclusion\":\"SELL_SOON\",\"window_start\":\"2026-10-30\",\"window_end\":\"2026-11-03\","
                        + "\"risks\":[\"注意行情波动\"]}").model("qwen").build());

        service.latest(3001L);

        ArgumentCaptor<AiChatRequest> request = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway).chat(request.capture());
        String system = request.getValue().getMessages().get(0).getText();
        String user = request.getValue().getMessages().get(1).getText();
        assertThat(system).contains("肉鸡").doesNotContain("养猪");
        assertThat(user).contains("今天：2026-10-28").contains("当前存栏：200 只");
        assertThat(request.getValue().getTimeout()).isEqualTo(SaleAdviceServiceImpl.AI_TIMEOUT);
        assertThat(SaleAdviceServiceImpl.AI_TIMEOUT.getSeconds()).isLessThanOrEqualTo(110);
        assertThat(request.getValue().getMaxTokens()).isGreaterThanOrEqualTo(4000);
        ArgumentCaptor<SaleAdvice> captor = ArgumentCaptor.forClass(SaleAdvice.class);
        verify(adviceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getReasonAge()).isEqualTo("150 日龄，存栏 200 只。");
    }
}

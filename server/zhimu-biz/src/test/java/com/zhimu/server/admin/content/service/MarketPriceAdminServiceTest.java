package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.entity.MarketPriceCorrection;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.MarketPriceAdminMapper;
import com.zhimu.server.admin.content.mapper.MarketPriceConfigAdminMapper;
import com.zhimu.server.admin.content.mapper.MarketPriceCorrectionMapper;
import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.vo.MarketPriceAdminVos;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.biz.finance.entity.MarketPrice;
import com.zhimu.server.biz.finance.entity.MarketPriceConfig;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketPriceAdminServiceTest {

    private MarketPriceAdminMapper priceMapper;
    private MarketPriceConfigAdminMapper configMapper;
    private MarketPriceCorrectionMapper correctionMapper;
    private OperationLogService operationLogService;
    private MarketPriceAdminService service;

    @BeforeEach
    void setUp() {
        ContentTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
        AdminContext.set(new AdminContext.LoginAdmin(2L, "周敏", Set.of("SUPER_ADMIN"),
                Set.of("WA-C02:VIEW", "WA-C02:HANDLE"), 0, "token"));
        priceMapper = mock(MarketPriceAdminMapper.class);
        configMapper = mock(MarketPriceConfigAdminMapper.class);
        correctionMapper = mock(MarketPriceCorrectionMapper.class);
        operationLogService = mock(OperationLogService.class);
        service = new MarketPriceAdminService(priceMapper, configMapper, correctionMapper,
                mock(ContentLookupMapper.class), new MarketPriceCalculator(), operationLogService,
                mock(AdminMessageService.class));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    @Test
    void batchPublishSplitsPublishedAndPendingReviewByTwentyPercent() {
        MarketPriceConfig slaughter = config(1L, 101L, 0L, "SLAUGHTER");
        MarketPriceConfig seedling = config(2L, 102L, 0L, "SLAUGHTER");
        when(configMapper.selectList(any())).thenReturn(List.of(slaughter, seedling));
        MarketPrice previous = publishedPrice("100.00");
        // 预检查两项当日已有；每项：查当日已有 -> 查上一次；插库后重算后续日期 -> 空
        when(priceMapper.selectList(any())).thenReturn(
                List.of(), List.of(),
                List.of(), List.of(previous), List.of(),
                List.of(), List.of(previous), List.of());

        ContentRequests.BatchPublishRequest request = new ContentRequests.BatchPublishRequest();
        request.setPriceDate(LocalDate.of(2026, 10, 19));
        request.setProvinceCode("150000");
        request.setDataSource("9 个收购点报价均价");
        request.setItems(List.of(item(1L, "130.00"), item(2L, "105.00")));
        MarketPriceAdminVos.BatchPublishVO result = service.batchPublish(request);

        assertThat(result.getReviewCount()).isEqualTo(1);
        assertThat(result.getPublishedCount()).isEqualTo(1);
        ArgumentCaptor<MarketPrice> captor = ArgumentCaptor.forClass(MarketPrice.class);
        verify(priceMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(MarketPrice::getStatus)
                .containsExactly("PENDING_REVIEW", "PUBLISHED");
        verify(operationLogService, times(2)).writeAdminLog(any(com.zhimu.server.admin.system.api.AdminLogCommand.class));
    }

    @Test
    void correctionAboveThresholdGoesToReviewWithoutChangingPublishedPrice() {
        MarketPrice price = publishedPrice("110.00");
        price.setId(9L);
        price.setVersion(0);
        price.setPrevPrice(new BigDecimal("100.00"));
        when(priceMapper.selectById(9L)).thenReturn(price);
        when(correctionMapper.selectPending(9L)).thenReturn(null);
        when(correctionMapper.insert(any(MarketPriceCorrection.class))).thenAnswer(invocation -> {
            MarketPriceCorrection correction = invocation.getArgument(0);
            correction.setId(77L);
            return 1;
        });

        ContentRequests.PriceCorrectionRequest request = new ContentRequests.PriceCorrectionRequest();
        request.setNewPrice(new BigDecimal("130.00"));
        request.setReason("取错样本");
        request.setVersion(0);
        MarketPriceAdminVos.CorrectionResultVO result = service.correct(9L, request);

        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(result.getPriceDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        verify(priceMapper, never()).update(any(), any());
        ArgumentCaptor<MarketPriceCorrection> captor = ArgumentCaptor.forClass(MarketPriceCorrection.class);
        verify(correctionMapper).insert(captor.capture());
        assertThat(captor.getValue().getPendingFlag()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASS", "RETURN"})
    void recorderCanReviewOwnPriceAndAuditIsRetained(String decision) {
        MarketPrice price = publishedPrice("110.00");
        price.setId(9L);
        price.setStatus("PENDING_REVIEW");
        price.setVersion(0);
        price.setEnteredBy(2L);
        when(priceMapper.selectById(9L)).thenReturn(price);
        when(priceMapper.update(any(), any())).thenReturn(1);

        ContentRequests.PriceReviewRequest request = new ContentRequests.PriceReviewRequest();
        request.setResult(decision);
        request.setReviewNote("核对无误");
        request.setVersion(0);
        assertThat(service.review(9L, request)).isNotNull();
        verify(priceMapper).update(any(), any());
        verify(operationLogService).writeAdminLog(any(com.zhimu.server.admin.system.api.AdminLogCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASS", "RETURN"})
    void recorderCanReviewOwnCorrectionAndReviewerIsRecorded(String decision) {
        MarketPrice price = publishedPrice("110.00");
        price.setId(9L);
        price.setVersion(0);
        when(priceMapper.selectById(9L)).thenReturn(price);
        when(priceMapper.update(any(), any())).thenReturn(1);
        MarketPriceCorrection correction = new MarketPriceCorrection();
        correction.setId(42L);
        correction.setPriceId(9L);
        correction.setStatus("PENDING_REVIEW");
        correction.setPendingFlag(true);
        correction.setCorrectedBy(2L);
        correction.setNewPrice(new BigDecimal("140.00"));
        correction.setChangeRateAfter(new BigDecimal("0.4"));
        when(correctionMapper.selectById(42L)).thenReturn(correction);
        ContentRequests.PriceReviewRequest request = new ContentRequests.PriceReviewRequest();
        request.setResult(decision);
        request.setReviewNote("核对原始报价");
        request.setCorrectionId(42L);
        request.setVersion(0);

        assertThat(service.review(9L, request)).isNotNull();
        assertThat(correction.getReviewerId()).isEqualTo(2L);
        assertThat(correction.getStatus()).isEqualTo("PASS".equals(decision) ? "APPLIED" : "RETURNED");
        assertThat(correction.getReviewNote()).isEqualTo("核对原始报价");
        assertThat(correction.getPendingFlag()).isNull();
        verify(operationLogService).writeAdminLog(any(com.zhimu.server.admin.system.api.AdminLogCommand.class));
    }

    @Test
    void listAllowsRecorderToReviewTheirPendingPrice() {
        when(configMapper.selectList(any())).thenReturn(List.of(config(1L, 101L, 0L, "SLAUGHTER")));
        MarketPrice price = publishedPrice("100.00");
        price.setId(9L);
        price.setPriceDate(LocalDate.of(2026, 10, 19));
        price.setStatus("PENDING_REVIEW");
        price.setEnteredBy(2L);
        when(priceMapper.selectList(any())).thenReturn(List.of(price));
        when(correctionMapper.selectList(any())).thenReturn(List.of());
        ContentQueries.MarketPriceQuery query = new ContentQueries.MarketPriceQuery();
        query.setDate(LocalDate.of(2026, 10, 19));
        assertThat(service.list(query).getList().getFirst().getCanReview()).isTrue();
    }

    @Test
    void reviewVersionMismatchReturns40902() {
        MarketPrice price = publishedPrice("110.00");
        price.setId(9L);
        price.setStatus("PENDING_REVIEW");
        price.setVersion(2);
        when(priceMapper.selectById(9L)).thenReturn(price);
        ContentRequests.PriceReviewRequest request = new ContentRequests.PriceReviewRequest();
        request.setResult("PASS");
        request.setReviewNote("核对无误");
        request.setVersion(1);
        assertThatThrownBy(() -> service.review(9L, request))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT.getCode()));
    }

    @Test
    void listToleratesSamplePricesWithoutRecorder() {
        when(configMapper.selectList(any())).thenReturn(List.of(config(1L, 101L, 0L, "SLAUGHTER")));
        // 示例行情（V9_17）没有录入人和审核人：entered_by / reviewer_id 都为空
        MarketPrice price = publishedPrice("100.00");
        price.setId(9L);
        price.setPriceDate(LocalDate.of(2026, 10, 19));
        when(priceMapper.selectList(any())).thenReturn(List.of(price));
        when(correctionMapper.selectList(any())).thenReturn(List.of());

        ContentQueries.MarketPriceQuery query = new ContentQueries.MarketPriceQuery();
        query.setDate(LocalDate.of(2026, 10, 19));
        PageVO<MarketPriceAdminVos.RowVO> page = service.list(query);

        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().getFirst().getRowStatus()).isEqualTo("PUBLISHED");
        assertThat(page.getList().getFirst().getEnteredByName()).isNull();
    }

    private static MarketPriceConfig config(Long id, Long categoryId, Long breedId, String priceType) {
        MarketPriceConfig config = new MarketPriceConfig();
        config.setId(id);
        config.setProvinceCode("150000");
        config.setProvinceName("内蒙古");
        config.setCategoryId(categoryId);
        config.setBreedId(breedId);
        config.setPriceType(priceType);
        config.setStatus("ENABLED");
        config.setDefaultUnit("YUAN_PER_KG");
        config.setSortNo(1);
        return config;
    }

    private static MarketPrice publishedPrice(String value) {
        MarketPrice price = new MarketPrice();
        price.setProvinceCode("150000");
        price.setProvinceName("内蒙古");
        price.setCategoryId(101L);
        price.setBreedId(0L);
        price.setPriceType("SLAUGHTER");
        price.setPriceDate(LocalDate.of(2026, 10, 18));
        price.setPrice(new BigDecimal(value));
        price.setPriceUnit("YUAN_PER_KG");
        price.setPriceUnitText("元/千克");
        price.setStatus("PUBLISHED");
        price.setCorrected(false);
        return price;
    }

    private static ContentRequests.BatchPublishRequest.Item item(Long configId, String price) {
        ContentRequests.BatchPublishRequest.Item item = new ContentRequests.BatchPublishRequest.Item();
        item.setConfigId(configId);
        item.setPrice(new BigDecimal(price));
        item.setPriceUnit("YUAN_PER_KG");
        return item;
    }
}

package com.zhimu.server.biz.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.ai.convert.AiMessageConvert;
import com.zhimu.server.biz.ai.convert.AiSessionConvert;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.entity.AiSession;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.mapper.AiSessionMapper;
import com.zhimu.server.biz.ai.query.AiMessageQuery;
import com.zhimu.server.biz.ai.query.AiSessionQuery;
import com.zhimu.server.biz.ai.service.impl.AiSessionServiceImpl;
import com.zhimu.server.biz.ai.support.AiFixtures;
import com.zhimu.server.biz.ai.support.FixtureAiBatchContextService;
import com.zhimu.server.biz.ai.vo.AiSessionDetailVO;
import com.zhimu.server.biz.ai.vo.AiSessionItemVO;
import com.zhimu.server.biz.ai.vo.LinkableBatchesVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

/**
 * API-K01–K05 Service 单测：分页、本人校验、轮次参数、关联批次权限。
 */
class AiSessionServiceTest {

    private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final AiMessageConvert messageConvert = mock(AiMessageConvert.class);
    private final FixtureAiBatchContextService batchContextService = spy(new FixtureAiBatchContextService());
    private AiSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯 Mockito 单测不经过 Spring/MyBatis-Plus 启动流程，lambda 条件列缓存需手动初始化。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiSession.class);
        com.zhimu.server.common.util.BeijingTime.setClock(java.time.Clock.fixed(
                AiFixtures.NOW.atZone(com.zhimu.server.common.util.BeijingTime.ZONE).toInstant(),
                com.zhimu.server.common.util.BeijingTime.ZONE));
        service = new AiSessionServiceImpl(mock(
                com.zhimu.server.biz.account.support.AccountPersonalDataGuard.class), sessionMapper, messageMapper,
                new AiSessionConvert(batchContextService, messageMapper), messageConvert, batchContextService);
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
        com.zhimu.server.common.util.BeijingTime.setClock(null);
    }

    @Test
    void listsSessionsWithDateGroupAndCorrection() {
        when(sessionMapper.selectCount(any())).thenReturn(1L);
        when(sessionMapper.selectList(any())).thenReturn(List.of(AiFixtures.session(1021L, 10001L)));

        PageVO<AiSessionItemVO> page = service.listSessions(10001L, new AiSessionQuery());

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).hasSize(1);
        AiSessionItemVO vo = page.getList().get(0);
        assertThat(vo.getId()).isEqualTo(1021L);
        assertThat(vo.getBatchNo()).isEqualTo("ZM2610-031");
        assertThat(vo.getDateGroup()).isEqualTo("TODAY");
        assertThat(vo.getDateGroupText()).isEqualTo("今天");
    }

    @Test
    void rejectsKeywordLongerThanFifty() {
        AiSessionQuery query = new AiSessionQuery();
        query.setKeyword("长".repeat(51));
        assertThatThrownBy(() -> service.listSessions(10001L, query))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40001);
    }

    @Test
    void detailOfOtherUserIsNotFound() {
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10002L));
        assertThatThrownBy(() -> service.detail(10001L, 1021L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40401);
    }

    @Test
    void detailReturnsLinkedBatchSnapshot() {
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10001L));
        AiSessionDetailVO vo = service.detail(10001L, 1021L);
        assertThat(vo.getBatchLinkState()).isEqualTo("VALID");
        assertThat(vo.getBatch().getBatchNo()).isEqualTo("ZM2610-031");
        assertThat(vo.getLastRoundNo()).isEqualTo(1);
    }

    @Test
    void reopeningSessionRefreshesOldSnapshotWithLiveStockAndWeight() {
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10001L));
        var live = batchContextService.resolve(1001L, 3001L, false, Set.of());
        live.setCurrentCount(173);
        live.setLatestAvgWeight(new java.math.BigDecimal("68.5"));
        doReturn(live).when(batchContextService).resolve(1001L, 3001L, false, Set.of());
        var result = service.detail(10001L, 1021L);
        assertThat(result.getBatch().getCurrentCount()).isEqualTo(173);
        assertThat(result.getBatch().getLatestAvgWeight()).isEqualByComparingTo("68.5");
    }

    @Test
    void reopeningStaffSessionAfterReassignmentHidesBatchData() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2004L), FarmStatus.NORMAL));
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10003L));
        var result = service.detail(10003L, 1021L);
        assertThat(result.getBatchLinkState()).isEqualTo("NO_PERMISSION");
        assertThat(result.getBatch()).isNull();
    }

    @Test
    void endedBatchIsNoLongerExposedAsValidContext() {
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10001L));
        doReturn(null).when(batchContextService).resolve(1001L, 3001L, false, Set.of());
        var result = service.detail(10001L, 1021L);
        assertThat(result.getBatchLinkState()).isEqualTo("NOT_IN_STOCK");
        assertThat(result.getBatch()).isNull();
    }

    @Test
    void beforeAndAfterRoundAreMutuallyExclusive() {
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10001L));
        AiMessageQuery query = new AiMessageQuery();
        query.setBeforeRoundNo(3);
        query.setAfterRoundNo(1);
        assertThatThrownBy(() -> service.messages(10001L, 1021L, query))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40001);
    }

    @Test
    void messagesSliceUsesRounds() {
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10001L));
        when(messageMapper.selectBeforeRound(1021L, Integer.MAX_VALUE, 20)).thenReturn(List.of(
                AiFixtures.message(5501L, 1021L, 1)));
        when(messageMapper.countBeforeRound(1021L, Integer.MAX_VALUE)).thenReturn(1L);
        AiMessageQuery query = new AiMessageQuery();
        service.messages(10001L, 1021L, query);
        verify(messageConvert).slice(any(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void staffCannotLinkBatchOfOtherPen() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2004L), FarmStatus.NORMAL));
        AiSession session = AiFixtures.session(1021L, 10003L);
        when(sessionMapper.selectById(1021L)).thenReturn(session);
        assertThatThrownBy(() -> service.changeBatch(10003L, 1021L, 3001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40304);
    }

    @Test
    void clearsBatchWhenNull() {
        AiSession session = AiFixtures.session(1021L, 10001L);
        when(sessionMapper.selectById(1021L)).thenReturn(session);
        AiSessionDetailVO vo = service.changeBatch(10001L, 1021L, null);
        assertThat(vo.getBatchLinkState()).isEqualTo("NONE");
        assertThat(session.getBatchId()).isNull();
        verify(sessionMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void linkableBatchesForStaffOnlyTheirPen() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2003L), FarmStatus.NORMAL));
        LinkableBatchesVO vo = service.linkableBatches(10003L);
        assertThat(vo.getBatches()).extracting(b -> b.getBatchId()).containsExactly(3001L);
        assertThat(vo.getPanelHint()).contains("负责栏舍");
    }

    @Test
    void linkableBatchesForOwnerReturnsAll() {
        LinkableBatchesVO vo = service.linkableBatches(10001L);
        assertThat(vo.getBatches()).hasSize(4);
        assertThat(vo.getPanelHint()).contains("东山猪场");
    }

    @Test
    void unknownBatchReturnsNotFound() {
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10001L));
        assertThatThrownBy(() -> service.changeBatch(10001L, 1021L, 9999L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40401);
    }

    @Test
    void messageOfOtherFarmNotFoundThroughMapper() {
        when(sessionMapper.selectById(anyLong())).thenReturn(null);
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        assertThat(message.getId()).isEqualTo(5501L);
        assertThatThrownBy(() -> service.detail(10001L, 1021L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40401);
    }
}

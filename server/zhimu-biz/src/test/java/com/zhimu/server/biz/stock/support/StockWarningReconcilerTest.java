package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockWarningReconcilerTest {
    private final FeedMapper feeds = mock(FeedMapper.class);
    private final DrugMapper drugs = mock(DrugMapper.class);
    private final DrugLotMapper lots = mock(DrugLotMapper.class);
    private final TodoMapper todos = mock(TodoMapper.class);
    private final TodoSourceWriteService writer = mock(TodoSourceWriteService.class);
    private StockWarningReconciler service;
    private Feed feed;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "stock-warning-test");
        assistant.setCurrentNamespace("stock-warning-test");
        for (Class<?> entity : List.of(Feed.class, Drug.class, DrugLot.class, Todo.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, entity);
        }
        ObjectProvider<TodoSourceWriteService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(writer);
        service = new StockWarningReconciler(feeds, drugs, lots, new DrugUsables(lots), todos, provider);
        feed = new Feed();
        feed.setId(51L);
        feed.setFarmId(1001L);
        feed.setName("育肥料");
        feed.setMinStock(new BigDecimal("100"));
        feed.setDailyUseQty(new BigDecimal("50"));
        feed.setWarnDays(7);
        feed.setIsLowStock(true);
        feed.setLowStockAt(BeijingTime.now().minusDays(5));
        when(feeds.selectOne(any())).thenReturn(feed);
        when(todos.selectList(any())).thenReturn(List.of(warning("PENDING")));
    }

    @Test
    void replenishmentAtBothThresholdsCompletesOldWarningWithoutDeletingHistory() {
        feed.setStockQty(new BigDecimal("350"));
        service.reconcileFeed(1001L, 51L);
        verify(writer).completeFromSource(1001L, "FEED_LOW_STOCK", "legacy-key", null, null, 0L);
        verify(writer, never()).upsertFromSource(any(), any());
        verify(todos, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void replenishmentAboveMinimumButBelowDaysKeepsWarningWithReason() {
        feed.setStockQty(new BigDecimal("200"));
        service.reconcileFeed(1001L, 51L);
        verify(writer, never()).completeFromSource(any(), any(), any(), any(), any(), any());
        verify(writer).upsertFromSource(eq(1001L), argThat(drafts -> drafts.size() == 1
                && "legacy-key".equals(drafts.get(0).getDedupKey())
                && drafts.get(0).getStatusNote().length() <= 20
                && drafts.get(0).getContentText().contains("可用 4 天")
                && drafts.get(0).getContentText().contains("7 天")));
    }

    @Test
    void recoveryClosesEveryPendingLegacyDuplicate() {
        feed.setStockQty(new BigDecimal("1000"));
        Todo duplicate = warning("PENDING");
        duplicate.setDedupKey("old-duplicate");
        when(todos.selectList(any())).thenReturn(List.of(warning("PENDING"), duplicate, warning("DONE")));
        service.reconcileFeed(1001L, 51L);
        verify(writer).completeFromSource(1001L, "FEED_LOW_STOCK", "legacy-key", null, null, 0L);
        verify(writer).completeFromSource(1001L, "FEED_LOW_STOCK", "old-duplicate", null, null, 0L);
    }

    @Test
    void ignoredWarningStaysIgnoredUntilAnotherLowStockCycle() {
        feed.setStockQty(new BigDecimal("20"));
        when(todos.selectList(any())).thenReturn(List.of(warning("IGNORED")));
        service.reconcileFeed(1001L, 51L);
        verifyNoInteractions(writer);
        feed.setIsLowStock(false);
        feed.setLowStockAt(null);
        service.reconcileFeed(1001L, 51L);
        verify(writer).upsertFromSource(eq(1001L), argThat(drafts ->
                !"legacy-key".equals(drafts.get(0).getDedupKey())));
    }

    @Test
    void expiredOrRecalledDrugStockDoesNotClearLowUsableStockWarning() {
        Drug drug = new Drug();
        drug.setId(61L); drug.setFarmId(1001L); drug.setName("药品"); drug.setUnit("ML");
        drug.setMinStock(new BigDecimal("100"));
        when(drugs.selectOne(any())).thenReturn(drug);
        DrugLot expired = lot("1000"); expired.setExpireDate(BeijingTime.today().minusDays(1));
        DrugLot recalled = lot("1000"); recalled.setRecalledAt(BeijingTime.now());
        DrugLot usable = lot("20");
        when(lots.selectList(any())).thenReturn(List.of(expired, recalled, usable));
        service.reconcileDrug(1001L, 61L);
        verify(writer, never()).completeFromSource(any(), any(), any(), any(), any(), any());
        verify(writer).upsertFromSource(eq(1001L), argThat(drafts ->
                drafts.get(0).getContentText().contains("可用结存 20")));
        clearInvocations(writer);
        usable.setStockQty(new BigDecimal("100"));
        service.reconcileDrug(1001L, 61L);
        verify(writer).completeFromSource(1001L, "DRUG_LOW_STOCK", "legacy-key", null, null, 0L);
    }

    private static DrugLot lot(String quantity) {
        DrugLot lot = new DrugLot(); lot.setStockQty(new BigDecimal(quantity));
        lot.setExpireDate(BeijingTime.today().plusDays(10)); return lot;
    }

    private static Todo warning(String status) {
        Todo todo = new Todo(); todo.setId(9L); todo.setFarmId(1001L); todo.setBizId(51L);
        todo.setTodoType("FEED_LOW_STOCK"); todo.setDedupKey("legacy-key"); todo.setStatus(status);
        todo.setDueDate(BeijingTime.today().minusDays(5)); return todo;
    }
}

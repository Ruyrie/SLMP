package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.today.TodayTableInfo;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.MessageMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.support.TodoLinks;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.biz.today.vo.HomeVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** API-T01 首页关键字段：基本信息、owner_metrics 口径、未读数与员工裁剪分组。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeServiceImplTest {

    @Mock private FarmQueryService farmQueryService;
    @Mock private AccountQueryService accountQueryService;
    @Mock private TodoMapper todoMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private TodoScope todoScope;
    @Mock private TodoLinks todoLinks;

    private HomeServiceImpl service;

    @BeforeEach
    void setUp() {
        TodayTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZoneId.of("Asia/Shanghai")));
        service = new HomeServiceImpl(farmQueryService, accountQueryService, todoMapper, messageMapper,
                todoScope, todoLinks, List.of());
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        when(farmQueryService.listFarmsOfUser(10001L)).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1001L).name("东山猪场").speciesText("猪")
                        .status("NORMAL").statusText("正常").role("OWNER").build()));
        when(accountQueryService.getBrief(10001L)).thenReturn(
                new AccountBrief(10001L, "王建国", "13800006620", "NORMAL", false));
        when(messageMapper.selectCount(any())).thenReturn(3L);
        when(todoMapper.selectList(any())).thenReturn(List.of(
                overdueFeeding(), receivable(), warning(), doneFeeding()));
        when(todoScope.canView(any(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        FarmContext.clear();
    }

    @Test
    void homeReturnsBasicInfoAndOwnerMetrics() {
        HomeVO home = service.getHome(10001L);

        assertThat(home.getServerTime()).isEqualTo("2026-10-19 09:30:00");
        assertThat(home.getGreeting()).isEqualTo("早上好");
        assertThat(home.getUserName()).isEqualTo("王建国");
        assertThat(home.getAvatarText()).isEqualTo("王");
        assertThat(home.getRole()).isEqualTo("OWNER");
        assertThat(home.getRoleText()).isEqualTo("你是本场所有者");
        assertThat(home.getFarmStatus()).isEqualTo("NORMAL");
        assertThat(home.getFarm().getSpeciesText()).isEqualTo("猪");
        assertThat(home.getHasPen()).isTrue();
        assertThat(home.getUnreadCount()).isEqualTo(3);
        assertThat(home.getStaffMetrics()).isNull();

        var metrics = home.getOwnerMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTodayTodoCount()).isEqualTo(3);
        assertThat(metrics.getOverdueCount()).isEqualTo(1);
        assertThat(metrics.getWarningCount()).isEqualTo(1);
        assertThat(metrics.getWarningNames()).containsExactly("育肥全价料库存不足");
        assertThat(metrics.getReceivableOverdueCount()).isEqualTo(1);
        assertThat(metrics.getReceivableMaxOverdueDays()).isEqualTo(4);
        assertThat(metrics.getReceivableAmount()).isEqualByComparingTo(new BigDecimal("68448.00"));
        // 待模块04：存栏与批次返回 0
        assertThat(metrics.getStockCount()).isZero();
        assertThat(metrics.getBatchCount()).isZero();
        assertThat(metrics.getCountUnit()).isEqualTo("HEAD");
        assertThat(metrics.getCountUnitText()).isEqualTo("头");
        assertThat(home.getAiHint()).contains("已逾期");
    }

    @Test
    void staffPensUseProviderMetricsAndBatchLink() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2003L, 2004L), FarmStatus.NORMAL));
        when(farmQueryService.listFarmsOfUser(10003L)).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1001L).name("东山猪场").speciesText("猪")
                        .status("NORMAL").statusText("正常").role("STAFF").build()));
        when(accountQueryService.getBrief(10003L)).thenReturn(
                new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false));
        when(todoMapper.selectList(any())).thenReturn(List.of());
        com.zhimu.server.biz.today.api.HomeMetricProvider provider =
                new com.zhimu.server.biz.today.api.HomeMetricProvider() {
                    @Override
                    public String moduleName() {
                        return "herd";
                    }

                    @Override
                    public List<PenMetric> pens(FarmContext.FarmMember member) {
                        return List.of(
                                new PenMetric(2003L, "3 号育肥舍", "育肥舍", 220,
                                        List.of(new BatchMetric(3001L, "ZM2610-031", "杜长大育肥猪", 96, 200,
                                                "3 号育肥舍", "育肥期"))),
                                new PenMetric(2004L, "4 号育肥舍", "育肥舍", 220, List.of()));
                    }

                    @Override
                    public java.util.Map<Long, Integer> withdrawalDaysByPen(FarmContext.FarmMember member) {
                        return java.util.Map.of(2003L, 6);
                    }
                };
        HomeServiceImpl withProvider = new HomeServiceImpl(farmQueryService, accountQueryService, todoMapper,
                messageMapper, todoScope, todoLinks, List.of(provider));

        HomeVO home = withProvider.getHome(10003L);

        assertThat(home.getMyPens()).hasSize(2);
        var pen = home.getMyPens().stream().filter(item -> item.getPenId() == 2003L).findFirst().orElseThrow();
        assertThat(pen.getCapacity()).isEqualTo(220);
        assertThat(pen.getPenTypeText()).isEqualTo("育肥舍");
        assertThat(pen.getBatches()).hasSize(1);
        assertThat(pen.getBatches().get(0).getStageText()).isEqualTo("育肥期");
        assertThat(pen.getInWithdrawal()).isTrue();
        assertThat(pen.getWithdrawalDaysLeft()).isEqualTo(6);
        // L06 批次档案必须带 batch_id，L02 我的栏舍带 pen_id
        assertThat(pen.getLink()).isNotNull();
        assertThat(pen.getLink().getRoute()).isEqualTo("L06");
        assertThat(pen.getLink().getParams()).containsEntry("batch_id", "3001");
        var emptyPen = home.getMyPens().stream().filter(item -> item.getPenId() == 2004L).findFirst().orElseThrow();
        assertThat(emptyPen.getLink().getRoute()).isEqualTo("L02");
        assertThat(emptyPen.getLink().getParams()).containsEntry("pen_id", "2004");
        assertThat(emptyPen.getInWithdrawal()).isFalse();
        assertThat(emptyPen.getWithdrawalDaysLeft()).isNull();
    }

    @Test
    void pendingFarmOnlyReturnsBasicInfo() {
        FarmContext.set(new FarmContext.FarmMember(1003L, "北湾牛场", 10001L, 5007L, FarmRole.OWNER,
                Set.of(), FarmStatus.PENDING));
        when(farmQueryService.listFarmsOfUser(10001L)).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1003L).name("北湾牛场").speciesText("牛")
                        .status("PENDING").statusText("待审核").role("OWNER").build()));

        HomeVO home = service.getHome(10001L);

        assertThat(home.getFarmStatus()).isEqualTo("PENDING");
        assertThat(home.isFarmBlocked()).isTrue();
        assertThat(home.getHasPen()).isFalse();
        assertThat(home.getOwnerMetrics()).isNull();
        assertThat(home.getStaffMetrics()).isNull();
        assertThat(home.getUnreadCount()).isEqualTo(3);
    }

    private static Todo todo(String type, String category, LocalDate dueDate, LocalTime dueTime,
                             LocalDateTime overdueAt, BigDecimal amount, String title) {
        Todo todo = new Todo();
        todo.setId((long) (Math.random() * 100000));
        todo.setFarmId(1001L);
        todo.setTodoType(type);
        todo.setCategory(category);
        todo.setTitle(title);
        todo.setStatus("PENDING");
        todo.setDueDate(dueDate);
        todo.setTimeType(dueTime == null ? "ALL_DAY" : "AT_TIME");
        todo.setDueTime(dueTime);
        todo.setOverdueAt(overdueAt);
        todo.setSortTime(overdueAt == null ? dueDate.atStartOfDay() : overdueAt);
        todo.setAmount(amount);
        todo.setPenId(2003L);
        todo.setPenName("3 号育肥舍");
        todo.setVersion(1);
        todo.setDeleted(false);
        return todo;
    }

    private static Todo overdueFeeding() {
        return todo("FEEDING", "WORK", LocalDate.of(2026, 10, 19), LocalTime.of(7, 0),
                LocalDateTime.of(2026, 10, 19, 9, 0), null, "1 号保育舍 · 早餐饲喂");
    }

    private static Todo receivable() {
        return todo("RECEIVABLE", "BUSINESS", LocalDate.of(2026, 10, 15), null,
                LocalDateTime.of(2026, 10, 16, 0, 0), new BigDecimal("68448.00"), "ZM2605-018 收款已逾期");
    }

    private static Todo warning() {
        return todo("FEED_LOW_STOCK", "WARNING", LocalDate.of(2026, 10, 19), null,
                null, null, "育肥全价料库存不足");
    }

    private static Todo doneFeeding() {
        Todo todo = todo("FEEDING", "WORK", LocalDate.of(2026, 10, 19), LocalTime.of(7, 0),
                LocalDateTime.of(2026, 10, 19, 9, 0), null, "3 号育肥舍 · 早餐饲喂");
        todo.setStatus("DONE");
        todo.setCompletedAt(LocalDateTime.of(2026, 10, 19, 7, 12));
        return todo;
    }
}

package com.zhimu.slmp.today.ui.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.TestData;
import com.zhimu.slmp.today.data.HomeRepository;
import com.zhimu.slmp.today.data.TodayPrefs;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.vo.HomeVO;
import com.zhimu.slmp.today.model.vo.TodoCalendarResult;
import com.zhimu.slmp.today.model.vo.TodoWeeklyResult;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.CalendarMode;
import com.zhimu.slmp.today.util.CalendarRange;
import com.zhimu.slmp.today.util.LinkNavigator;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * CalendarViewModel 日期装配与 CalendarRange：完整周（周一开始、5 或 6 行、跨年）、各视图的请求范围、翻页、
 * 员工不传 scope、以服务端 is_today 校正“今天”。
 */
public class CalendarViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private TodoRepository todoRepository;
    private HomeRepository homeRepository;
    private TodayPrefs prefs;
    private FarmContext farmContext;
    private CalendarViewModel vm;

    @Before
    public void setUp() {
        todoRepository = mock(TodoRepository.class);
        homeRepository = mock(HomeRepository.class);
        prefs = mock(TodayPrefs.class);
        farmContext = mock(FarmContext.class);
        TodayServiceImpl todoService = mock(TodayServiceImpl.class);
        when(todoService.todoChanges()).thenReturn(Observable.never());
        when(farmContext.farmIdOrZero()).thenReturn(1001L);
        when(homeRepository.getCachedHome(anyLong())).thenReturn(TestData.parse(TestData.OWNER_HOME, HomeVO.class));
        when(todoRepository.getCalendar(anyString(), anyString(), any(), any()))
                .thenReturn(Single.just(TestData.parse("{'days':[]}", TodoCalendarResult.class)));
        when(todoRepository.getWeekly(anyString(), any(), any(), anyInt()))
                .thenReturn(Single.just(TestData.parse("{'days':[]}", TodoWeeklyResult.class)));
        when(todoRepository.getOverdue(any(), anyInt(), anyInt())).thenReturn(Single.just(new PageVO<>()));
        vm = new CalendarViewModel(todoRepository, homeRepository, prefs, farmContext, mock(LinkNavigator.class),
                todoService);
    }

    @After
    public void tearDown() {
        DateFormatter.setClock(Clock.system(DateFormatter.ZONE));
    }

    @Test
    public void overdueDefaultsCollapsedAndScopeChangeResetsExpansion() {
        vm.init("2026-10-19", null, "DAY");
        assertFalse(vm.getOverdueExpanded().getValue());
        vm.toggleOverdue();
        assertTrue(vm.getOverdueExpanded().getValue());
        vm.reload();
        assertTrue(vm.getOverdueExpanded().getValue());
        vm.switchScope(CalendarViewModel.SCOPE_ALL);
        assertFalse(vm.getOverdueExpanded().getValue());
        vm.onAllOverdueClick();
        assertEquals("T09", vm.getNavEvent().getValue().getRoute());
        assertEquals("ALL", vm.getNavEvent().getValue().param("scope"));
    }

    // ===================== CalendarRange =====================

    @Test
    public void monthRange_fullWeeksFromMonday() {
        CalendarRange oct = CalendarRange.month(YearMonth.of(2026, 10));
        assertEquals(LocalDate.of(2026, 9, 28), oct.getStart());
        assertEquals(LocalDate.of(2026, 11, 1), oct.getEnd());
        assertEquals(5, CalendarRange.rowsOf(YearMonth.of(2026, 10)));
        // 2026年8月1日为周六：7月27日–9月6日，6 行
        assertEquals(6, CalendarRange.rowsOf(YearMonth.of(2026, 8)));
        assertTrue(CalendarRange.month(YearMonth.of(2026, 8)).days() <= 42);
    }

    @Test
    public void weekRange_crossYear() {
        CalendarRange w = CalendarRange.week(LocalDate.of(2026, 12, 31));
        assertEquals(LocalDate.of(2026, 12, 28), w.getStart());
        assertEquals(LocalDate.of(2027, 1, 3), w.getEnd());
        assertEquals("2026年12月28日–2027年1月3日", CalendarRange.periodText(CalendarMode.WEEK, LocalDate.of(2026, 12, 31)));
        assertEquals("10月19日–25日", CalendarRange.periodText(CalendarMode.WEEK, LocalDate.of(2026, 10, 21)));
        assertEquals("10月19日 周一", CalendarRange.periodText(CalendarMode.DAY, LocalDate.of(2026, 10, 19)));
    }

    // ===================== ViewModel =====================

    @Test
    public void ownerFirstEntry_monthViewAroundServerToday() {
        vm.init(null, null, null);

        assertEquals(CalendarMode.MONTH, vm.getMode().getValue());
        assertEquals(LocalDate.of(2026, 10, 19), vm.getSelectedDate().getValue());
        assertEquals("2026年10月", vm.getPeriodText().getValue());
        assertEquals("10月19日 周一 · 今天", vm.getDayHeader().getValue());
        assertTrue(vm.getShowScope().getValue());
        assertTrue(vm.getShowFarmTag().getValue());
        verify(todoRepository).getCalendar("2026-09-28", "2026-11-01", CalendarViewModel.SCOPE_CURRENT, null);
        assertEquals("2026-10-19", vm.lastDayKey().date);
    }

    @Test
    public void switchToWeek_requestsSevenDaysAndWeekly() {
        vm.init(null, null, null);

        vm.switchMode(CalendarMode.WEEK);

        assertEquals("10月19日–25日", vm.getPeriodText().getValue());
        verify(todoRepository).getCalendar("2026-10-19", "2026-10-25", CalendarViewModel.SCOPE_CURRENT, null);
        verify(todoRepository).getWeekly("2026-10-19", CalendarViewModel.SCOPE_CURRENT, null, 5);
        verify(prefs).setCalendarMode("WEEK");
    }

    @Test
    public void nextMonth_selectsFirstDayAndSixWeeks() {
        vm.init(null, null, null);

        vm.next();

        assertEquals(LocalDate.of(2026, 11, 1), vm.getSelectedDate().getValue());
        assertEquals("2026年11月", vm.getPeriodText().getValue());
        assertEquals("11月1日 周日", vm.getDayHeader().getValue());
        verify(todoRepository).getCalendar("2026-10-26", "2026-12-06", CalendarViewModel.SCOPE_CURRENT, null);

        vm.goToday();
        assertEquals(LocalDate.of(2026, 10, 19), vm.getSelectedDate().getValue());
    }

    @Test
    public void staff_dayViewWithoutScopeAndWithOverdue() {
        when(farmContext.isStaff()).thenReturn(true);
        when(homeRepository.getCachedHome(anyLong())).thenReturn(TestData.parse(TestData.STAFF_HOME, HomeVO.class));

        vm.init("2026-10-19", null, null);

        when(farmContext.current()).thenReturn(null);
        assertEquals(" · 只显示你负责的 3 号育肥舍、4 号育肥舍的作业待办。登记对应记录后，待办自动完成。",
                vm.getStaffBanner().getValue());

        assertEquals(CalendarMode.DAY, vm.getMode().getValue());
        assertFalse(vm.getShowScope().getValue());
        assertFalse(vm.getShowFarmTag().getValue());
        verify(todoRepository).getCalendar(eq("2026-10-19"), eq("2026-10-25"), isNull(), isNull());
        verify(todoRepository).getOverdue(isNull(), eq(1), eq(5));
        assertNull(vm.lastDayKey().scope);
    }

    @Test
    public void serverIsToday_correctsPhoneDate() {
        when(homeRepository.getCachedHome(anyLong())).thenReturn(null);
        DateFormatter.setClock(Clock.fixed(LocalDate.of(2026, 10, 21).atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .toInstant(), ZoneId.of("Asia/Shanghai")));
        vm.init(null, null, null);
        assertEquals(LocalDate.of(2026, 10, 21), vm.getSelectedDate().getValue());

        vm.applyGrid(TestData.parse("{'days':[{'date':'2026-10-19','is_today':true,'total':3,'overdue_count':1,"
                + "'pending_count':1,'closed_count':1}]}", TodoCalendarResult.class));

        assertEquals(LocalDate.of(2026, 10, 19), vm.getTodayDate().getValue());
        assertEquals(LocalDate.of(2026, 10, 19), vm.getSelectedDate().getValue());
        assertEquals("10月19日 周一 · 今天", vm.getDayHeader().getValue());
        assertEquals(1, vm.getGridDays().getValue().get(LocalDate.of(2026, 10, 19)).getOverdueCount());
    }

    @Test
    public void weekly_skipsEmptyDaysAndKeepsRemaining() {
        vm.init(null, null, CalendarMode.WEEK.name());

        vm.applyWeek(TestData.parse("{'days':[{'date':'2026-10-19','is_today':true,'total':24,"
                + "'items':[" + TestData.todo(1, "PENDING", "WORK") + "]},{'date':'2026-10-20','total':0,'items':[]}]}",
                TodoWeeklyResult.class));

        assertEquals(1, vm.getWeekGroups().getValue().size());
        assertEquals("10月19日 周一 · 今天", vm.getWeekGroups().getValue().get(0).getTitle());
        assertEquals(23, vm.getWeekGroups().getValue().get(0).getRemaining());
        assertEquals(Collections.emptyList(), vm.getOverdueItems().getValue());
    }

    @Test
    public void staffBanner_textForPens() {
        assertEquals("东山猪场 · 只显示你负责的 3 号育肥舍、4 号育肥舍的作业待办。登记对应记录后，待办自动完成。",
                CalendarViewModel.staffBannerText("东山猪场", java.util.Arrays.asList("3 号育肥舍", "4 号育肥舍")));
        assertTrue(CalendarViewModel.staffBannerText("东山猪场",
                java.util.Arrays.asList("1", "2", "3", "4", "5")).contains("1等 5 个栏舍"));
    }
}

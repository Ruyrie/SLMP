package com.zhimu.server.biz.today.controller;

import com.zhimu.server.biz.today.TodayTestMvc;
import com.zhimu.server.biz.today.service.HomeService;
import com.zhimu.server.biz.today.service.MessageQueryService;
import com.zhimu.server.biz.today.service.PushDeviceService;
import com.zhimu.server.biz.today.service.ReminderSettingService;
import com.zhimu.server.biz.today.service.TodoService;
import com.zhimu.server.biz.today.vo.CalendarDayVO;
import com.zhimu.server.biz.today.vo.HomeVO;
import com.zhimu.server.biz.today.vo.LinkVO;
import com.zhimu.server.biz.today.vo.MessageActionVO;
import com.zhimu.server.biz.today.vo.MessageBriefVO;
import com.zhimu.server.biz.today.vo.MessageDeleteResult;
import com.zhimu.server.biz.today.vo.MessageDetailVO;
import com.zhimu.server.biz.today.vo.MessagePageVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.biz.today.vo.OwnerMetricsVO;
import com.zhimu.server.biz.today.vo.PushDeviceVO;
import com.zhimu.server.biz.today.vo.ReadAllResult;
import com.zhimu.server.biz.today.vo.ReminderSettingVO;
import com.zhimu.server.biz.today.vo.TodoCalendarResult;
import com.zhimu.server.biz.today.vo.TodoDetailVO;
import com.zhimu.server.biz.today.vo.TodoItemVO;
import com.zhimu.server.biz.today.vo.TodoPageVO;
import com.zhimu.server.biz.today.vo.TodoWeeklyResult;
import com.zhimu.server.biz.today.vo.UnreadCountVO;
import com.zhimu.server.biz.today.vo.WeeklyDayVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.vo.FarmBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 20 个 App 映射的 standalone MockMvc 冒烟：方法、路径、关键响应字段。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodayControllerMvcTest {

    private static final String JSON = "application/json";

    @Mock private HomeService homeService;
    @Mock private TodoService todoService;
    @Mock private ReminderSettingService reminderSettingService;
    @Mock private MessageQueryService messageQueryService;
    @Mock private PushDeviceService pushDeviceService;

    private MockMvc mvc;

    /** 模拟 AuthInterceptor 写入登录用户；TraceFilter 请求结束会清理 ThreadLocal */
    private static final HandlerInterceptor AUTH_STUB = new HandlerInterceptor() {
        @Override
        public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                 jakarta.servlet.http.HttpServletResponse response, Object handler) {
            UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "test-jti"));
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        mvc = TodayTestMvc.builderWithInterceptors(new Object[]{
                new HomeController(homeService),
                new TodoController(todoService),
                new ReminderSettingController(reminderSettingService),
                new MessageController(messageQueryService),
                new PushDeviceController(pushDeviceService)
        }, AUTH_STUB).build();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "test-jti"));
        stubResponses();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        com.zhimu.server.common.context.FarmContext.clear();
    }

    // ===================== API-T01 =====================

    @Test
    void homeReturnsKeyFields() throws Exception {
        mvc.perform(authed(get("/api/v1/home")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.server_time").value("2026-10-19 09:30:00"))
                .andExpect(jsonPath("$.data.user_name").value("王建国"))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.has_pen").value(true))
                .andExpect(jsonPath("$.data.unread_count").value(3))
                .andExpect(jsonPath("$.data.farm.name").value("东山猪场"))
                .andExpect(jsonPath("$.data.owner_metrics.today_todo_count").value(16))
                .andExpect(jsonPath("$.data.owner_metrics.warning_names[0]").value("育肥全价料库存不足"));
    }

    // ===================== API-T02–T09 =====================

    @Test
    void todayReturnsPageAndCounts() throws Exception {
        mvc.perform(authed(get("/api/v1/todos/today")).param("filter", "ALL")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].todo_id").value(90112))
                .andExpect(jsonPath("$.data.list[0].display_status").value("OVERDUE"))
                .andExpect(jsonPath("$.data.tab_counts.ALL").value(16))
                .andExpect(jsonPath("$.data.pen_counts[0].pen_id").value(2003))
                .andExpect(jsonPath("$.data.total").value(16));
    }

    @Test
    void calendarReturnsEveryDay() throws Exception {
        mvc.perform(authed(get("/api/v1/todos/calendar"))
                        .param("start_date", "2026-10-19").param("end_date", "2026-10-25")
                        .param("scope", "CURRENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.days[0].is_today").value(true))
                .andExpect(jsonPath("$.data.days[0].overdue_titles[0]").value("1 号保育舍 · 早餐饲喂"));
    }

    @Test
    void dayReturnsPage() throws Exception {
        mvc.perform(authed(get("/api/v1/todos")).param("date", "2026-10-19")
                        .param("status", "ALL").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].todo_id").value(90107))
                .andExpect(jsonPath("$.data.list[0].display_status").value("DONE"))
                .andExpect(jsonPath("$.data.total").value(17));
    }

    @Test
    void weeklyGroupsByDay() throws Exception {
        mvc.perform(authed(get("/api/v1/todos/weekly")).param("week_start", "2026-10-19")
                        .param("per_day", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.days[0].items[0].todo_id").value(90105));
    }

    @Test
    void overdueReturnsList() throws Exception {
        mvc.perform(authed(get("/api/v1/todos/overdue")).param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].todo_id").value(90112))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void todoDetailReturnsTypeSpecificFields() throws Exception {
        mvc.perform(authed(get("/api/v1/todos/90112")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todo_id").value(90112))
                .andExpect(jsonPath("$.data.subtitle").value("计划 10月18日 · 已逾期 1 天"))
                .andExpect(jsonPath("$.data.detail_label").value("消毒要求"))
                .andExpect(jsonPath("$.data.can_complete").value(true))
                .andExpect(jsonPath("$.data.related_batch.batch_no").value("ZM2609-038"));
    }

    @Test
    void completeReturnsDoneDetail() throws Exception {
        mvc.perform(authed(post("/api/v1/todos/90112/complete"))
                        .header("Idempotency-Key", "idem-complete-1")
                        .contentType(JSON).content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todo_id").value(90112))
                .andExpect(jsonPath("$.data.display_status").value("DONE"))
                .andExpect(jsonPath("$.data.complete_way").value("MANUAL"))
                .andExpect(jsonPath("$.data.completed_by.name").value("王建国"));
    }

    @Test
    void ignoreReturnsIgnoredDetail() throws Exception {
        mvc.perform(authed(post("/api/v1/todos/90201/ignore"))
                        .header("Idempotency-Key", "idem-ignore-1")
                        .contentType(JSON).content("{\"reason\":\"这批猪计划 11月中旬再出栏\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.display_status").value("IGNORED"))
                .andExpect(jsonPath("$.data.ignore_reason").value("这批猪计划 11月中旬再出栏"))
                .andExpect(jsonPath("$.data.ignored_by.name").value("王建国"));
    }

    // ===================== API-T10/T11 =====================

    @Test
    void reminderSettingGetAndPatch() throws Exception {
        mvc.perform(authed(get("/api/v1/reminder-settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quiet_end_next_day").value(true))
                .andExpect(jsonPath("$.data.summary_text").value("待办提醒开 · 每日汇总 07:00 · 免打扰 22:00–次日 07:00"));
        mvc.perform(authed(patch("/api/v1/reminder-settings"))
                        .contentType(JSON).content("{\"quiet_enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quiet_enabled").value(false));
    }

    // ===================== API-T12–T18 =====================

    @Test
    void messageListReturnsGroupAndUnread() throws Exception {
        mvc.perform(authed(get("/api/v1/messages")).param("unread_only", "false")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].message_id").value(501))
                .andExpect(jsonPath("$.data.list[0].date_group").value("TODAY"))
                .andExpect(jsonPath("$.data.list[0].action.action_type").value("INVITE"))
                .andExpect(jsonPath("$.data.filtered_unread").value(3))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void unreadCountReturnsCategories() throws Exception {
        mvc.perform(authed(get("/api/v1/messages/unread-count")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_unread").value(3))
                .andExpect(jsonPath("$.data.filtered_unread").value(3))
                .andExpect(jsonPath("$.data.by_category.AI").value(1));
    }

    @Test
    void readAllReturnsUpdatedCount() throws Exception {
        mvc.perform(authed(post("/api/v1/messages/read-all"))
                        .contentType(JSON).content("{\"category\":\"TODO\",\"farm_id\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated_count").value(3))
                .andExpect(jsonPath("$.data.total_unread").value(0));
    }

    @Test
    void openReturnsTargetAndLink() throws Exception {
        mvc.perform(authed(post("/api/v1/messages/501/open"))
                        .contentType(JSON).content("{\"push_id\":9001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target_status").value("OK"))
                .andExpect(jsonPath("$.data.link.route").value("I06"))
                .andExpect(jsonPath("$.data.total_unread").value(2));
    }

    @Test
    void messageDetailReturnsPayload() throws Exception {
        mvc.perform(authed(get("/api/v1/messages/507")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message_id").value(507))
                .andExpect(jsonPath("$.data.target_status").value("OK"))
                .andExpect(jsonPath("$.data.payload.title").value("秋季重大动物疫病防控提醒"));
    }

    @Test
    void deleteReturnsUndoDeadline() throws Exception {
        mvc.perform(authed(delete("/api/v1/messages/503")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted_at").value("2026-10-19 09:31:00"))
                .andExpect(jsonPath("$.data.undo_deadline").value("2026-10-19 09:32:00"));
    }

    @Test
    void restoreReturnsBrief() throws Exception {
        mvc.perform(authed(post("/api/v1/messages/503/restore")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message_id").value(503))
                .andExpect(jsonPath("$.data.category").value("TODO"))
                .andExpect(jsonPath("$.data.farm.name").value("东山猪场"));
    }

    // ===================== API-T20/T21 =====================

    @Test
    void registerPushDeviceReturnsBinding() throws Exception {
        mvc.perform(authed(put("/api/v1/push-devices")).contentType(JSON)
                        .content("{\"registration_id\":\"190e35f7e0a5b1c2d3\",\"platform\":\"ANDROID\","
                                + "\"device_brand\":\"Xiaomi\",\"notify_permission\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration_id").value("190e35f7e0a5b1c2d3"))
                .andExpect(jsonPath("$.data.jpush_alias").value("u10001"))
                .andExpect(jsonPath("$.data.alias_bound").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void unbindPushDeviceReturnsOk() throws Exception {
        mvc.perform(authed(delete("/api/v1/push-devices/190e35f7e0a5b1c2d3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ===================== 夹具 =====================

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer test");
    }

    private void stubResponses() {
        HomeVO home = new HomeVO();
        home.setServerTime("2026-10-19 09:30:00");
        home.setGreeting("早上好");
        home.setUserName("王建国");
        home.setAvatarText("王");
        home.setRole("OWNER");
        home.setRoleText("你是本场所有者");
        home.setFarmStatus("NORMAL");
        home.setFarmStatusText("正常");
        home.setFarm(FarmBriefVO.builder().farmId(1001L).name("东山猪场").speciesText("猪")
                .status("NORMAL").statusText("正常").role("OWNER").build());
        home.setHasPen(true);
        home.setUnreadCount(3);
        OwnerMetricsVO metrics = new OwnerMetricsVO();
        metrics.setStockCount(870);
        metrics.setBatchCount(4);
        metrics.setTodayTodoCount(16);
        metrics.setOverdueCount(4);
        metrics.setWarningCount(1);
        metrics.setReceivableOverdueCount(1);
        metrics.setReceivableMaxOverdueDays(4);
        metrics.setCountUnit("HEAD");
        metrics.setCountUnitText("头");
        metrics.setWarningNames(List.of("育肥全价料库存不足"));
        home.setOwnerMetrics(metrics);
        when(homeService.getHome(anyLong())).thenReturn(home);

        TodoItemVO item = todo(90112L, "OVERDUE", "CLASS");
        TodoPageVO today = new TodoPageVO(List.of(item), 16, 1, 20);
        today.setTabCounts(Map.of("ALL", 16, "OVERDUE", 4, "WORK", 13, "BIZ_WARN", 3));
        TodoPageVO.PenCount pen = new TodoPageVO.PenCount();
        pen.setPenId(2003L);
        pen.setPenName("3 号育肥舍");
        pen.setCount(5);
        today.setPenCounts(List.of(pen));
        when(todoService.today(any(), anyLong())).thenReturn(today);

        CalendarDayVO day = CalendarDayVO.of("2026-10-19", true);
        day.setTotal(17);
        day.setOverdueCount(1);
        day.setPendingCount(14);
        day.setClosedCount(2);
        day.setOverdueTitles(List.of("1 号保育舍 · 早餐饲喂"));
        TodoCalendarResult calendar = new TodoCalendarResult();
        calendar.setDays(List.of(day));
        when(todoService.calendar(any(), anyLong())).thenReturn(calendar);

        TodoItemVO done = todo(90107L, "DONE", "饲喂");
        when(todoService.day(any(), anyLong())).thenReturn(new com.zhimu.server.common.api.PageVO<>(
                List.of(done), 17, 1, 20));

        WeeklyDayVO weeklyDay = new WeeklyDayVO();
        weeklyDay.setDate("2026-10-19");
        weeklyDay.setIsToday(true);
        weeklyDay.setTotal(17);
        weeklyDay.setOverdueCount(1);
        weeklyDay.setPendingCount(14);
        weeklyDay.setClosedCount(2);
        weeklyDay.setOverdueTitles(List.of("1 号保育舍 · 早餐饲喂"));
        weeklyDay.setItems(List.of(todo(90105L, "PENDING", "预警")));
        TodoWeeklyResult weekly = new TodoWeeklyResult();
        weekly.setDays(List.of(weeklyDay));
        when(todoService.weekly(any(), anyLong())).thenReturn(weekly);

        when(todoService.overdue(any(), anyLong())).thenReturn(new com.zhimu.server.common.api.PageVO<>(
                List.of(item), 3, 1, 20));
        when(todoService.detail(eq(90112L), anyLong())).thenReturn(detail(90112L, "OVERDUE", null));
        when(todoService.complete(eq(90112L), any(), anyLong()))
                .thenReturn(detail(90112L, "DONE", "MANUAL"));
        when(todoService.ignore(eq(90201L), any(), anyLong()))
                .thenReturn(detail(90201L, "IGNORED", null));

        ReminderSettingVO reminder = new ReminderSettingVO();
        reminder.setTodoRemindEnabled(true);
        reminder.setDigestEnabled(true);
        reminder.setDigestTime("07:00");
        reminder.setQuietEnabled(true);
        reminder.setQuietStart("22:00");
        reminder.setQuietEnd("07:00");
        reminder.setQuietEndNextDay(true);
        reminder.setSummaryText("待办提醒开 · 每日汇总 07:00 · 免打扰 22:00–次日 07:00");
        when(reminderSettingService.get(anyLong())).thenReturn(reminder);
        ReminderSettingVO quietOff = new ReminderSettingVO();
        quietOff.setTodoRemindEnabled(true);
        quietOff.setDigestEnabled(true);
        quietOff.setDigestTime("07:00");
        quietOff.setQuietEnabled(false);
        quietOff.setQuietStart("22:00");
        quietOff.setQuietEnd("07:00");
        quietOff.setQuietEndNextDay(true);
        quietOff.setSummaryText("待办提醒开 · 每日汇总 07:00 · 免打扰关闭");
        when(reminderSettingService.patch(any(), anyLong())).thenReturn(quietOff);

        MessagePageVO messages = new MessagePageVO(List.of(brief(501L, "TODAY", "INVITE")), 2, 1, 20);
        messages.setFilteredUnread(3);
        when(messageQueryService.list(any(), anyLong())).thenReturn(messages);
        UnreadCountVO unread = new UnreadCountVO();
        unread.setTotalUnread(3);
        unread.setFilteredUnread(3);
        unread.setByCategory(Map.of("AI", 1, "TODO", 1, "MEMBER", 1));
        when(messageQueryService.unreadCount(any(), anyLong())).thenReturn(unread);
        ReadAllResult readAll = new ReadAllResult();
        readAll.setUpdatedCount(3);
        readAll.setTotalUnread(0);
        when(messageQueryService.readAll(any(), anyLong())).thenReturn(readAll);
        OpenResultVO open = new OpenResultVO();
        open.setTargetStatus("OK");
        open.setTargetStatusText("可以打开");
        LinkVO link = new LinkVO();
        link.setRoute("I06");
        link.setFarmId(1001L);
        link.setFarmName("东山猪场");
        link.setNeedSwitch(false);
        link.setParams(Map.of("report_id", "6001"));
        open.setLink(link);
        open.setTotalUnread(2);
        when(messageQueryService.open(eq(501L), any(), anyLong())).thenReturn(open);

        MessageDetailVO detail = new MessageDetailVO();
        detail.setMessageId(507L);
        detail.setCategory("NOTICE");
        detail.setMsgType("NOTICE");
        detail.setTargetStatus("OK");
        detail.setTargetStatusText("可以打开");
        detail.setTitle("秋季重大动物疫病防控提醒");
        detail.setPayload(Map.of("title", "秋季重大动物疫病防控提醒", "sender_name", "智牧通平台"));
        when(messageQueryService.detail(eq(507L), anyLong())).thenReturn(detail);

        MessageDeleteResult deleteResult = new MessageDeleteResult();
        deleteResult.setDeletedAt("2026-10-19 09:31:00");
        deleteResult.setUndoDeadline("2026-10-19 09:32:00");
        when(messageQueryService.delete(eq(503L), anyLong())).thenReturn(deleteResult);

        when(messageQueryService.restore(eq(503L), anyLong())).thenReturn(brief(503L, "TODAY", null));

        PushDeviceVO push = new PushDeviceVO();
        push.setRegistrationId("190e35f7e0a5b1c2d3");
        push.setJpushAlias("u10001");
        push.setStatus("ACTIVE");
        push.setAliasBound(true);
        push.setNotifyPermission(true);
        when(pushDeviceService.register(any(), anyLong())).thenReturn(push);
    }

    private static TodoItemVO todo(long id, String displayStatus, String categoryText) {
        TodoItemVO vo = new TodoItemVO();
        vo.setTodoId(id);
        vo.setFarmId(1001L);
        vo.setFarmName("东山猪场");
        vo.setTodoType("DISINFECTION");
        vo.setTodoTypeText("消毒");
        vo.setCategory("WORK");
        vo.setCategoryText("作业类");
        vo.setDisplayStatus(displayStatus);
        vo.setDisplayStatusText(displayStatus.equals("DONE") ? "已完成"
                : displayStatus.equals("IGNORED") ? "已忽略" : "已逾期");
        vo.setTitle("2 号保育舍 · 消毒");
        vo.setSubText("计划 10月18日 · 无负责员工");
        vo.setTailText("已逾期");
        vo.setDueDate("2026-10-18");
        vo.setTimeType("ALL_DAY");
        vo.setTimeLabel("全天");
        vo.setPenId(2002L);
        vo.setPenName("2 号保育舍");
        vo.setVersion(3);
        return vo;
    }

    private static TodoDetailVO detail(long id, String displayStatus, String completeWay) {
        TodoDetailVO vo = new TodoDetailVO();
        TodoItemVO item = todo(id, displayStatus, "作业类");
        org.springframework.beans.BeanUtils.copyProperties(item, vo);
        vo.setSubtitle(displayStatus.equals("DONE") ? "10月19日 09:40 由王建国手动标记完成"
                : displayStatus.equals("IGNORED") ? "10月29日 已由王建国忽略" : "计划 10月18日 · 已逾期 1 天");
        vo.setSourceModuleName("消毒记录");
        vo.setDueText("10月18日 周日");
        vo.setDetailLabel("消毒要求");
        vo.setDetailValue("聚维酮碘溶液 1:500 · 喷雾");
        vo.setRemindCount(3);
        vo.setCompleteCondition("登记消毒后自动完成");
        vo.setCompleteWay(completeWay);
        vo.setCompleteWayText("MANUAL".equals(completeWay) ? "手动标记，未生成记录" : null);
        vo.setCompletedAt("MANUAL".equals(completeWay) ? "2026-10-19 09:40:00" : null);
        if ("MANUAL".equals(completeWay)) {
            vo.setCompletedBy(com.zhimu.server.common.vo.UserBriefVO.builder()
                    .userId(10001L).name("王建国").role("OWNER").roleText("所有者").build());
        }
        vo.setIgnoredAt("IGNORED".equals(displayStatus) ? "2026-10-19 09:35:00" : null);
        if ("IGNORED".equals(displayStatus)) {
            vo.setIgnoredBy(com.zhimu.server.common.vo.UserBriefVO.builder()
                    .userId(10001L).name("王建国").role("OWNER").roleText("所有者").build());
            vo.setIgnoreReason("这批猪计划 11月中旬再出栏");
        }
        vo.setCanRegister(false);
        vo.setCanComplete("OVERDUE".equals(displayStatus) || "PENDING".equals(displayStatus));
        vo.setCanIgnore("OVERDUE".equals(displayStatus) || "PENDING".equals(displayStatus));
        com.zhimu.server.biz.today.vo.RelatedBatchVO batch = new com.zhimu.server.biz.today.vo.RelatedBatchVO();
        batch.setBatchId(3004L);
        batch.setBatchNo("ZM2609-038");
        batch.setPenName("2 号保育舍");
        batch.setDesc("保育猪 · 存栏 200 头");
        vo.setRelatedBatch(batch);
        return vo;
    }

    private static MessageBriefVO brief(long id, String dateGroup, String actionType) {
        MessageBriefVO vo = new MessageBriefVO();
        vo.setMessageId(id);
        vo.setCategory("TODO");
        vo.setCategoryText("待办提醒");
        vo.setMsgType("TODO_REMIND");
        vo.setLevel("NORMAL");
        vo.setLevelText("一般");
        vo.setTitle("3 号育肥舍 · 猪瘟免疫");
        vo.setSummary("已合并 2 次提醒");
        vo.setFarm(FarmBriefVO.builder().farmId(1001L).name("东山猪场").status("NORMAL")
                .statusText("正常").role("OWNER").build());
        vo.setRead(false);
        vo.setRevoked(false);
        vo.setCreatedAt(LocalDateTime.of(2026, 10, 19, 8, 0));
        vo.setSortTime(LocalDateTime.of(2026, 10, 19, 8, 0));
        vo.setDateGroup(dateGroup);
        vo.setIconState("PENDING");
        vo.setMergeCount(2);
        if (actionType != null) {
            MessageActionVO action = new MessageActionVO();
            action.setActionType(actionType);
            action.setActionId(7001L);
            action.setStatus("PENDING");
            action.setStatusText("待确认");
            action.setRoleText("员工");
            action.setCanAccept(true);
            vo.setAction(action);
        }
        return vo;
    }
}

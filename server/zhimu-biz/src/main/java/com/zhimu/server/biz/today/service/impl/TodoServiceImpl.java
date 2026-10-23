package com.zhimu.server.biz.today.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.api.TodoQueryService;
import com.zhimu.server.biz.today.convert.TodoConvert;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.entity.TodoHandleLog;
import com.zhimu.server.biz.today.mapper.TodoHandleLogMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.query.TodoCalendarQuery;
import com.zhimu.server.biz.today.query.TodoDateQuery;
import com.zhimu.server.biz.today.query.TodoOverdueQuery;
import com.zhimu.server.biz.today.query.TodoWeeklyQuery;
import com.zhimu.server.biz.today.query.TodayTodoQuery;
import com.zhimu.server.biz.today.request.TodoCompleteRequest;
import com.zhimu.server.biz.today.request.TodoIgnoreRequest;
import com.zhimu.server.biz.today.service.TodoService;
import com.zhimu.server.biz.today.support.TodayText;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.biz.today.vo.CalendarDayVO;
import com.zhimu.server.biz.today.vo.TodoCalendarResult;
import com.zhimu.server.biz.today.vo.TodoDetailVO;
import com.zhimu.server.biz.today.vo.TodoItemVO;
import com.zhimu.server.biz.today.vo.TodoPageVO;
import com.zhimu.server.biz.today.vo.TodoWeeklyResult;
import com.zhimu.server.biz.today.vo.WeeklyDayVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.TodoCategory;
import com.zhimu.server.common.enums.TodoStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 待办查询与处理（API-T02–T09、2.6.4、2.7）。
 * <p>
 * 范围：所有者/场长看全场；员工只看负责栏舍的作业类待办。全部养殖场视角显式关闭多租户插件后按场过滤。
 * 显示状态 OVERDUE 与逾期天数实时计算；完成/忽略带 version 乐观锁，员工调用返回 40303。
 */
@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService, TodoQueryService {

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_OVERDUE = "OVERDUE";
    private static final String FILTER_WORK = "WORK";
    private static final String FILTER_BIZ_WARN = "BIZ_WARN";
    private static final String SCOPE_ALL = "ALL";
    private static final String STATUS_ALL = "ALL";
    private static final String STATUS_UNFINISHED = "UNFINISHED";
    private static final int MAX_CALENDAR_DAYS = 42;

    private final TodoMapper todoMapper;
    private final TodoHandleLogMapper handleLogMapper;
    private final TodoScope todoScope;
    private final TodoConvert todoConvert;
    private final FarmQueryService farmQueryService;

    // ===================== API-T02 今天和已逾期 =====================

    @Override
    public TodoPageVO today(TodayTodoQuery query, Long userId) {
        FarmContext.FarmMember member = FarmContext.require();
        LocalDate today = BeijingTime.today();
        LocalDateTime now = BeijingTime.now();
        String filter = normalizeFilter(query.getFilter());
        boolean staff = member.isStaff();
        if (staff && !FILTER_ALL.equals(filter) && !FILTER_OVERDUE.equals(filter)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "员工只能按全部或已逾期筛选");
        }

        List<Todo> all = pendingOfFarm(member.farmId());
        List<Todo> todayList = new ArrayList<>();
        for (Todo todo : all) {
            if (todoScope.canView(todo, member) && isTodayList(todo, today, now)) {
                todayList.add(todo);
            }
        }
        if (query.getPenId() != null) {
            if (staff && !member.canAccessPen(query.getPenId())) {
                throw new BizException(ErrorCode.PEN_FORBIDDEN);
            }
            todayList.removeIf(todo -> !query.getPenId().equals(todo.getPenId()));
        }

        Map<String, Integer> tabCounts = new LinkedHashMap<>();
        tabCounts.put("ALL", todayList.size());
        tabCounts.put("OVERDUE", (int) todayList.stream().filter(t -> isOverdue(t, now)).count());
        if (!staff) {
            tabCounts.put("WORK", (int) todayList.stream().filter(t -> "WORK".equals(t.getCategory())).count());
            tabCounts.put("BIZ_WARN", (int) todayList.stream()
                    .filter(t -> !"WORK".equals(t.getCategory())).count());
        }

        List<Todo> filtered = new ArrayList<>(todayList);
        if (FILTER_OVERDUE.equals(filter)) {
            filtered.removeIf(t -> !isOverdue(t, now));
        } else if (FILTER_WORK.equals(filter)) {
            filtered.removeIf(t -> !"WORK".equals(t.getCategory()));
        } else if (FILTER_BIZ_WARN.equals(filter)) {
            filtered.removeIf(t -> "WORK".equals(t.getCategory()));
        }
        filtered.sort(todayComparator(now));

        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        Map<Long, String> farmNames = farmNames(userId);
        List<TodoItemVO> items = new ArrayList<>();
        for (Todo todo : slice(filtered, page, size)) {
            items.add(todoConvert.toItem(todo, farmNames.get(todo.getFarmId()), staff, today, now));
        }
        TodoPageVO vo = new TodoPageVO(items, filtered.size(), page, size);
        vo.setTabCounts(tabCounts);
        if (staff) {
            vo.setPenCounts(penCounts(todayList));
        } else {
            vo.setPenCounts(List.of());
        }
        return vo;
    }

    // ===================== API-T03 日期汇总 =====================

    @Override
    public TodoCalendarResult calendar(TodoCalendarQuery query, Long userId) {
        if (query.getStartDate() == null || query.getEndDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择开始日期和结束日期");
        }
        if (query.getStartDate().isAfter(query.getEndDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始日期不能晚于结束日期");
        }
        long span = ChronoUnit.DAYS.between(query.getStartDate(), query.getEndDate()) + 1;
        if (span > MAX_CALENDAR_DAYS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询跨度不能超过 42 天");
        }
        String category = validateCategory(query.getCategory());
        TodoScope.Scope scope = todoScope.resolve(userId, query.getScope());
        LocalDate today = BeijingTime.today();
        LocalDateTime now = BeijingTime.now();

        Map<LocalDate, List<Todo>> byDay = groupByBusinessDay(scope, today, category);
        TodoCalendarResult result = new TodoCalendarResult();
        List<CalendarDayVO> days = new ArrayList<>();
        for (LocalDate date = query.getStartDate(); !date.isAfter(query.getEndDate()); date = date.plusDays(1)) {
            CalendarDayVO day = CalendarDayVO.of(date.toString(), date.equals(today));
            fillCalendarDay(day, date, today, now, byDay.getOrDefault(date, List.of()));
            days.add(day);
        }
        result.setDays(days);
        return result;
    }

    // ===================== API-T04 某天待办 =====================

    @Override
    public PageVO<TodoItemVO> day(TodoDateQuery query, Long userId) {
        if (query.getDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择日期");
        }
        String category = validateCategory(query.getCategory());
        String status = query.getStatus() == null || query.getStatus().isBlank()
                ? STATUS_ALL : query.getStatus().toUpperCase();
        if (!STATUS_ALL.equals(status) && !STATUS_UNFINISHED.equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态筛选不正确");
        }
        TodoScope.Scope scope = todoScope.resolve(userId, query.getScope());
        LocalDate today = BeijingTime.today();
        LocalDateTime now = BeijingTime.now();
        Map<Long, String> farmNames = farmNames(userId);

        List<Todo> matches = new ArrayList<>();
        for (Todo todo : visibleTodos(scope)) {
            if (category != null && !category.equals(todo.getCategory())) {
                continue;
            }
            if (STATUS_UNFINISHED.equals(status) && !isOpen(todo)) {
                continue;
            }
            if (query.getDate().equals(businessDay(todo, today))) {
                matches.add(todo);
            }
        }
        matches.sort(dayComparator());
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        List<TodoItemVO> items = new ArrayList<>();
        for (Todo todo : slice(matches, page, size)) {
            FarmContext.FarmMember member = scope.member(todo.getFarmId());
            boolean staff = member != null && member.isStaff();
            items.add(todoConvert.toItem(todo, farmNames.get(todo.getFarmId()), staff, today, now));
        }
        return new PageVO<>(items, matches.size(), page, size);
    }

    // ===================== API-T05 周视图 =====================

    @Override
    public TodoWeeklyResult weekly(TodoWeeklyQuery query, Long userId) {
        if (query.getWeekStart() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择周开始日期");
        }
        if (query.getWeekStart().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BizException(ErrorCode.PARAM_INVALID, "周视图开始日期必须是周一");
        }
        int perDay = query.getPerDay() == null ? 5 : query.getPerDay();
        if (perDay < 1 || perDay > 20) {
            throw new BizException(ErrorCode.PARAM_INVALID, "每天展示条数必须在 1–20 之间");
        }
        String category = validateCategory(query.getCategory());
        TodoScope.Scope scope = todoScope.resolve(userId, query.getScope());
        LocalDate today = BeijingTime.today();
        LocalDateTime now = BeijingTime.now();
        Map<LocalDate, List<Todo>> byDay = groupByBusinessDay(scope, today, category);
        Map<Long, String> farmNames = farmNames(userId);

        List<WeeklyDayVO> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = query.getWeekStart().plusDays(i);
            List<Todo> todos = byDay.getOrDefault(date, List.of());
            if (todos.isEmpty()) {
                continue;
            }
            todos.sort(dayComparator());
            WeeklyDayVO day = new WeeklyDayVO();
            day.setDate(date.toString());
            day.setIsToday(date.equals(today));
            fillCalendarDay(day, date, today, now, todos);
            List<TodoItemVO> items = new ArrayList<>();
            for (Todo todo : todos.subList(0, Math.min(perDay, todos.size()))) {
                FarmContext.FarmMember member = scope.member(todo.getFarmId());
                items.add(todoConvert.toItem(todo, farmNames.get(todo.getFarmId()),
                        member != null && member.isStaff(), today, now));
            }
            day.setItems(items);
            days.add(day);
        }
        TodoWeeklyResult result = new TodoWeeklyResult();
        result.setDays(days);
        return result;
    }

    // ===================== API-T06 已逾期 =====================

    @Override
    public PageVO<TodoItemVO> overdue(TodoOverdueQuery query, Long userId) {
        TodoScope.Scope scope = todoScope.resolve(userId, query.getScope());
        LocalDate today = BeijingTime.today();
        LocalDateTime now = BeijingTime.now();
        Map<Long, String> farmNames = farmNames(userId);
        List<Todo> matches = new ArrayList<>();
        for (Todo todo : visibleTodos(scope)) {
            if (isOpen(todo) && todo.getDueDate() != null && todo.getDueDate().isBefore(today)
                    && todo.getOverdueAt() != null && now.isAfter(todo.getOverdueAt())) {
                matches.add(todo);
            }
        }
        matches.sort(Comparator.comparing(Todo::getSortTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        List<TodoItemVO> items = new ArrayList<>();
        for (Todo todo : slice(matches, page, size)) {
            FarmContext.FarmMember member = scope.member(todo.getFarmId());
            items.add(todoConvert.toItem(todo, farmNames.get(todo.getFarmId()),
                    member != null && member.isStaff(), today, now));
        }
        return new PageVO<>(items, matches.size(), page, size);
    }

    // ===================== API-T07 详情 =====================

    @Override
    public TodoDetailVO detail(Long todoId, Long userId) {
        Todo todo = loadTodo(todoId);
        FarmContext.FarmMember member = requireReadable(todo, userId);
        return convertDetail(todo, userId, member);
    }

    // ===================== API-T08 手动完成 =====================

    @Override
    @Transactional
    public TodoDetailVO complete(Long todoId, TodoCompleteRequest request, Long userId) {
        Todo todo = loadTodo(todoId);
        FarmContext.FarmMember member = requireWriteMember(todo, userId);
        todoScope.checkWritable(member);
        if (member.isStaff()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只有所有者和场长可以标记完成");
        }
        todoScope.checkViewable(todo, member);
        if (!TodoCategory.WORK.name().equals(todo.getCategory())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED,
                    "经营类和预警类待办只能在登记对应记录后自动完成，不能手动标记完成。");
        }
        if (!TodoStatus.PENDING.name().equals(todo.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "内容已更新，请刷新后再修改");
        }
        int version = request == null || request.getVersion() == null ? -1 : request.getVersion();
        if (version != (todo.getVersion() == null ? 0 : todo.getVersion())) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        LocalDateTime now = BeijingTime.now();
        boolean overdue = todo.getOverdueAt() != null && now.isAfter(todo.getOverdueAt());

        Todo update = new Todo();
        update.setId(todo.getId());
        update.setVersion(version);
        update.setStatus(TodoStatus.DONE.name());
        update.setCompleteWay("MANUAL");
        update.setCompletedAt(now);
        update.setCompletedBy(userId);
        update.setOverdueDone(overdue);
        int affected = todoMapper.update(update, Wrappers.<Todo>lambdaUpdate()
                .eq(Todo::getId, todo.getId())
                .eq(Todo::getStatus, TodoStatus.PENDING.name()));
        if (affected == 0) {
            Todo latest = loadTodo(todoId);
            throw new BizException(TodoStatus.PENDING.name().equals(latest.getStatus())
                    ? ErrorCode.VERSION_CONFLICT : ErrorCode.STATE_CHANGED, "内容已更新，请刷新后再修改");
        }
        writeHandleLog(todo, "COMPLETE", "MANUAL", userId, null, overdue, now);
        return convertDetail(loadTodo(todoId), userId, member);
    }

    // ===================== API-T09 忽略 =====================

    @Override
    @Transactional
    public TodoDetailVO ignore(Long todoId, TodoIgnoreRequest request, Long userId) {
        Todo todo = loadTodo(todoId);
        FarmContext.FarmMember member = requireWriteMember(todo, userId);
        todoScope.checkWritable(member);
        if (member.isStaff()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只有所有者和场长可以忽略待办");
        }
        todoScope.checkViewable(todo, member);
        String reason = TodayText.trim(request == null ? null : request.getReason());
        if (reason == null || reason.isEmpty()) {
            throw new BizException(ErrorCode.REASON_EMPTY, "请填写忽略原因");
        }
        if (TodayText.codePointLength(reason) > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "忽略原因不能超过 100 字");
        }
        if (!TodoStatus.PENDING.name().equals(todo.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "内容已更新，请刷新后再修改");
        }
        int version = request == null || request.getVersion() == null ? -1 : request.getVersion();
        if (version != (todo.getVersion() == null ? 0 : todo.getVersion())) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        LocalDateTime now = BeijingTime.now();
        Todo update = new Todo();
        update.setId(todo.getId());
        update.setVersion(version);
        update.setStatus(TodoStatus.IGNORED.name());
        update.setIgnoreWay("MANUAL");
        update.setIgnoredAt(now);
        update.setIgnoredBy(userId);
        update.setIgnoreReason(reason);
        int affected = todoMapper.update(update, Wrappers.<Todo>lambdaUpdate()
                .eq(Todo::getId, todo.getId())
                .eq(Todo::getStatus, TodoStatus.PENDING.name()));
        if (affected == 0) {
            Todo latest = loadTodo(todoId);
            throw new BizException(TodoStatus.PENDING.name().equals(latest.getStatus())
                    ? ErrorCode.VERSION_CONFLICT : ErrorCode.STATE_CHANGED, "内容已更新，请刷新后再修改");
        }
        writeHandleLog(todo, "IGNORE", "MANUAL", userId, reason, false, now);
        return convertDetail(loadTodo(todoId), userId, member);
    }

    // ===================== 对外计数（模块02、管理端） =====================

    @Override
    public int countPendingForMember(Long farmId, Long userId) {
        FarmContext.FarmMember member = todoScope.memberFor(farmId, userId);
        if (member == null) {
            return 0;
        }
        int count = 0;
        for (Todo todo : pendingOfFarm(farmId)) {
            if (TodoCategory.WORK.name().equals(todo.getCategory()) && todoScope.canView(todo, member)) {
                count++;
            }
        }
        return count;
    }

    // ===================== 内部 =====================

    private Todo loadTodo(Long todoId) {
        Todo todo = todoId == null ? null : todoMapper.selectIncludingDeleted(todoId);
        if (todo == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (Boolean.TRUE.equals(todo.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该待办已被删除，无法查看");
        }
        return todo;
    }

    private FarmContext.FarmMember requireReadable(Todo todo, Long userId) {
        FarmContext.FarmMember member = requireMemberOfTodoFarm(todo, userId);
        todoScope.checkViewable(todo, member);
        return member;
    }

    /** 写操作先只校验成员与养殖场状态，角色判断（员工 40303）在看得见范围之前 */
    private FarmContext.FarmMember requireWriteMember(Todo todo, Long userId) {
        return requireMemberOfTodoFarm(todo, userId);
    }

    private FarmContext.FarmMember requireMemberOfTodoFarm(Todo todo, Long userId) {
        FarmContext.FarmMember member = todoScope.memberFor(todo.getFarmId(), userId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FARM_MEMBER);
        }
        FarmStatus status = member.farmStatus();
        if (status == FarmStatus.FROZEN) {
            throw new BizException(ErrorCode.FARM_FROZEN,
                    member.farmName() + "已被平台冻结，暂时不能使用。原因见消息中心");
        }
        if (status == FarmStatus.PENDING || status == FarmStatus.REJECTED) {
            throw new BizException(ErrorCode.FARM_STATUS_FORBIDDEN,
                    member.farmName() + "还在审核，审核通过后才能查看待办");
        }
        return member;
    }

    private TodoDetailVO convertDetail(Todo todo, Long userId, FarmContext.FarmMember member) {
        String farmName = farmNames(userId).get(todo.getFarmId());
        return todoConvert.toDetail(todo, farmName, member.isStaff(), BeijingTime.today(), BeijingTime.now());
    }

    private List<Todo> pendingOfFarm(Long farmId) {
        return todoMapper.selectList(Wrappers.<Todo>lambdaQuery()
                .eq(Todo::getFarmId, farmId)
                .eq(Todo::getStatus, TodoStatus.PENDING.name())
                .orderByAsc(Todo::getSortTime));
    }

    private List<Todo> visibleTodos(TodoScope.Scope scope) {
        if (scope.farmIds().isEmpty()) {
            return List.of();
        }
        List<Todo> todos = TenantSkip.call(() -> todoMapper.selectList(Wrappers.<Todo>lambdaQuery()
                .in(Todo::getFarmId, scope.farmIds())
                .orderByAsc(Todo::getSortTime)));
        List<Todo> result = new ArrayList<>();
        for (Todo todo : todos) {
            FarmContext.FarmMember member = scope.member(todo.getFarmId());
            if (member != null && todoScope.canView(todo, member)) {
                result.add(todo);
            }
        }
        return result;
    }

    private Map<LocalDate, List<Todo>> groupByBusinessDay(TodoScope.Scope scope, LocalDate today, String category) {
        Map<LocalDate, List<Todo>> days = new HashMap<>();
        for (Todo todo : visibleTodos(scope)) {
            if (category != null && !category.equals(todo.getCategory())) {
                continue;
            }
            LocalDate day = businessDay(todo, today);
            if (day != null) {
                days.computeIfAbsent(day, d -> new ArrayList<>()).add(todo);
            }
        }
        return days;
    }

    /** 归日规则（2.7.3） */
    private LocalDate businessDay(Todo todo, LocalDate today) {
        String type = todo.getTodoType() == null ? "" : todo.getTodoType();
        if (TodoCategory.WARNING.name().equals(todo.getCategory())) {
            if (isOpen(todo)) {
                return today;
            }
            return closedDay(todo);
        }
        if ("WITHDRAWAL_END".equals(type)) {
            if (isOpen(todo)) {
                return todo.getDueDate() != null && todo.getDueDate().isBefore(today)
                        ? today : todo.getDueDate();
            }
            LocalDate closed = closedDay(todo);
            if (closed == null) {
                return todo.getDueDate();
            }
            return todo.getDueDate() != null && todo.getDueDate().isAfter(closed) ? todo.getDueDate() : closed;
        }
        return todo.getDueDate();
    }

    private LocalDate closedDay(Todo todo) {
        if (todo.getIgnoredAt() != null) {
            return todo.getIgnoredAt().toLocalDate();
        }
        if (todo.getCompletedAt() != null) {
            return todo.getCompletedAt().toLocalDate();
        }
        return todo.getDueDate();
    }

    private void fillCalendarDay(CalendarDayVO day, LocalDate date, LocalDate today, LocalDateTime now,
                                 List<Todo> todos) {
        int overdue = 0;
        int pending = 0;
        int closed = 0;
        List<String> overdueTitles = new ArrayList<>();
        for (Todo todo : todos) {
            TodoStatus status = TodayText.displayStatus(todo, now);
            if (status == TodoStatus.OVERDUE) {
                overdue++;
                if (overdueTitles.size() < 2) {
                    overdueTitles.add(todo.getTitle());
                }
            } else if (status == TodoStatus.PENDING) {
                pending++;
            } else {
                closed++;
            }
        }
        day.setTotal(todos.size());
        day.setOverdueCount(overdue);
        day.setPendingCount(pending);
        day.setClosedCount(closed);
        day.setOverdueTitles(overdueTitles);
    }

    private boolean isOpen(Todo todo) {
        return TodoStatus.PENDING.name().equals(todo.getStatus());
    }

    private boolean isOverdue(Todo todo, LocalDateTime now) {
        return isOpen(todo) && todo.getOverdueAt() != null && now.isAfter(todo.getOverdueAt());
    }

    /** “今天和已逾期”范围：已逾期、今天到期、预警类与已到可出栏日期的休药期到期（2.7.3） */
    private boolean isTodayList(Todo todo, LocalDate today, LocalDateTime now) {
        if (!isOpen(todo)) {
            return false;
        }
        if (isOverdue(todo, now)) {
            return true;
        }
        if (today.equals(todo.getDueDate())) {
            return true;
        }
        if (TodoCategory.WARNING.name().equals(todo.getCategory())) {
            return true;
        }
        return "WITHDRAWAL_END".equals(todo.getTodoType()) && todo.getDueDate() != null
                && todo.getDueDate().isBefore(today);
    }

    private Comparator<Todo> todayComparator(LocalDateTime now) {
        return (a, b) -> {
            int ga = todayGroup(a, now);
            int gb = todayGroup(b, now);
            if (ga != gb) {
                return Integer.compare(ga, gb);
            }
            if (ga == 0) {
                return compareTimeDesc(a.getSortTime(), b.getSortTime());
            }
            if (ga == 1) {
                return compareTimeAsc(a.getSortTime(), b.getSortTime());
            }
            boolean aAllDay = isAllDay(a);
            boolean bAllDay = isAllDay(b);
            if (aAllDay != bAllDay) {
                return aAllDay ? -1 : 1;
            }
            return compareTimeAsc(a.getSortTime(), b.getSortTime());
        };
    }

    private int todayGroup(Todo todo, LocalDateTime now) {
        if (isOverdue(todo, now)) {
            return 0;
        }
        if (TodoCategory.WARNING.name().equals(todo.getCategory())) {
            return 1;
        }
        return 2;
    }

    private Comparator<Todo> dayComparator() {
        return (a, b) -> {
            boolean aAllDay = isAllDay(a);
            boolean bAllDay = isAllDay(b);
            if (aAllDay != bAllDay) {
                return aAllDay ? -1 : 1;
            }
            return compareTimeAsc(a.getSortTime(), b.getSortTime());
        };
    }

    private static boolean isAllDay(Todo todo) {
        return "ALL_DAY".equals(todo.getTimeType()) || todo.getDueTime() == null;
    }

    private static int compareTimeAsc(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }

    private static int compareTimeDesc(LocalDateTime a, LocalDateTime b) {
        return -compareTimeAsc(a, b);
    }

    private List<TodoPageVO.PenCount> penCounts(List<Todo> todos) {
        Map<Long, TodoPageVO.PenCount> counts = new LinkedHashMap<>();
        for (Todo todo : todos) {
            if (todo.getPenId() == null) {
                continue;
            }
            TodoPageVO.PenCount count = counts.computeIfAbsent(todo.getPenId(), id -> {
                TodoPageVO.PenCount c = new TodoPageVO.PenCount();
                c.setPenId(id);
                c.setPenName(todo.getPenName());
                c.setCount(0);
                return c;
            });
            count.setCount(count.getCount() + 1);
        }
        return new ArrayList<>(counts.values());
    }

    private Map<Long, String> farmNames(Long userId) {
        Map<Long, String> names = new HashMap<>();
        for (FarmBriefVO farm : farmQueryService.listFarmsOfUser(userId)) {
            names.put(farm.getFarmId(), farm.getName());
        }
        return names;
    }

    private String normalizeFilter(String filter) {
        String value = filter == null || filter.isBlank() ? FILTER_ALL : filter.trim().toUpperCase();
        Set<String> allowed = new LinkedHashSet<>(List.of(FILTER_ALL, FILTER_OVERDUE, FILTER_WORK, FILTER_BIZ_WARN));
        if (!allowed.contains(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "筛选条件不正确");
        }
        return value;
    }

    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String value = category.trim().toUpperCase();
        if (com.zhimu.server.common.enums.CodeEnum.of(TodoCategory.class, value) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "待办类别不正确");
        }
        return value;
    }

    private static <T> List<T> slice(List<T> list, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        if (from >= list.size()) {
            return List.of();
        }
        return list.subList(from, Math.min(list.size(), from + size));
    }

    private void writeHandleLog(Todo todo, String action, String way, Long userId, String reason,
                                boolean overdueDone, LocalDateTime now) {
        TodoHandleLog log = new TodoHandleLog();
        log.setTodoId(todo.getId());
        log.setFarmId(todo.getFarmId());
        log.setAction(action);
        log.setWay(way);
        log.setOperatorId(userId);
        log.setReason(reason);
        log.setOverdueDone(overdueDone);
        log.setCreatedAt(now);
        handleLogMapper.insert(log);
    }
}

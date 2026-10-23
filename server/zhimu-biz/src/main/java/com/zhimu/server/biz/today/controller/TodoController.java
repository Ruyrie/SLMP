package com.zhimu.server.biz.today.controller;

import com.zhimu.server.biz.today.query.TodayTodoQuery;
import com.zhimu.server.biz.today.query.TodoCalendarQuery;
import com.zhimu.server.biz.today.query.TodoDateQuery;
import com.zhimu.server.biz.today.query.TodoOverdueQuery;
import com.zhimu.server.biz.today.query.TodoWeeklyQuery;
import com.zhimu.server.biz.today.request.TodoCompleteRequest;
import com.zhimu.server.biz.today.request.TodoIgnoreRequest;
import com.zhimu.server.biz.today.service.TodoService;
import com.zhimu.server.biz.today.vo.TodoCalendarResult;
import com.zhimu.server.biz.today.vo.TodoDetailVO;
import com.zhimu.server.biz.today.vo.TodoItemVO;
import com.zhimu.server.biz.today.vo.TodoPageVO;
import com.zhimu.server.biz.today.vo.TodoWeeklyResult;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-T02–T09 待办：今天和已逾期、日历、某天、周视图、已逾期、详情、手动完成、忽略。
 * T02–T06 使用 X-Farm-Id；T07–T09 按待办所属养殖场判权（日历“全部养殖场”可直接查看他场待办）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /** T02 今天和已逾期 */
    @GetMapping("/todos/today")
    @RequireFarm
    public ApiResponse<TodoPageVO> today(@Valid TodayTodoQuery query) {
        return ApiResponse.ok(todoService.today(query, UserContext.requireUserId()));
    }

    /** T03 日期汇总 */
    @GetMapping("/todos/calendar")
    @RequireFarm
    public ApiResponse<TodoCalendarResult> calendar(@Valid TodoCalendarQuery query) {
        return ApiResponse.ok(todoService.calendar(query, UserContext.requireUserId()));
    }

    /** T04 某天待办 */
    @GetMapping("/todos")
    @RequireFarm
    public ApiResponse<PageVO<TodoItemVO>> day(@Valid TodoDateQuery query) {
        return ApiResponse.ok(todoService.day(query, UserContext.requireUserId()));
    }

    /** T05 周视图分组 */
    @GetMapping("/todos/weekly")
    @RequireFarm
    public ApiResponse<TodoWeeklyResult> weekly(@Valid TodoWeeklyQuery query) {
        return ApiResponse.ok(todoService.weekly(query, UserContext.requireUserId()));
    }

    /** T06 已逾期分组 */
    @GetMapping("/todos/overdue")
    @RequireFarm
    public ApiResponse<PageVO<TodoItemVO>> overdue(@Valid TodoOverdueQuery query) {
        return ApiResponse.ok(todoService.overdue(query, UserContext.requireUserId()));
    }

    /** T07 待办详情 */
    @GetMapping("/todos/{id:\\d+}")
    public ApiResponse<TodoDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(todoService.detail(id, UserContext.requireUserId()));
    }

    /** T08 手动标记完成（幂等键 24 小时） */
    @PostMapping("/todos/{id:\\d+}/complete")
    @Idempotent(required = true)
    public ApiResponse<TodoDetailVO> complete(@PathVariable("id") Long id,
                                              @Valid @RequestBody TodoCompleteRequest request) {
        return ApiResponse.ok(todoService.complete(id, request, UserContext.requireUserId()));
    }

    /** T09 忽略（幂等键 24 小时） */
    @PostMapping("/todos/{id:\\d+}/ignore")
    @Idempotent(required = true)
    public ApiResponse<TodoDetailVO> ignore(@PathVariable("id") Long id,
                                            @Valid @RequestBody TodoIgnoreRequest request) {
        return ApiResponse.ok(todoService.ignore(id, request, UserContext.requireUserId()));
    }
}

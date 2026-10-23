package com.zhimu.server.biz.today.service;

import com.zhimu.server.biz.today.query.TodoCalendarQuery;
import com.zhimu.server.biz.today.query.TodoDateQuery;
import com.zhimu.server.biz.today.query.TodoOverdueQuery;
import com.zhimu.server.biz.today.query.TodoWeeklyQuery;
import com.zhimu.server.biz.today.query.TodayTodoQuery;
import com.zhimu.server.biz.today.request.TodoCompleteRequest;
import com.zhimu.server.biz.today.request.TodoIgnoreRequest;
import com.zhimu.server.biz.today.vo.TodoCalendarResult;
import com.zhimu.server.biz.today.vo.TodoDetailVO;
import com.zhimu.server.biz.today.vo.TodoItemVO;
import com.zhimu.server.biz.today.vo.TodoPageVO;
import com.zhimu.server.biz.today.vo.TodoWeeklyResult;
import com.zhimu.server.common.api.PageVO;

/** 待办查询与处理（API-T02–T09）。 */
public interface TodoService {

    TodoPageVO today(TodayTodoQuery query, Long userId);

    TodoCalendarResult calendar(TodoCalendarQuery query, Long userId);

    PageVO<TodoItemVO> day(TodoDateQuery query, Long userId);

    TodoWeeklyResult weekly(TodoWeeklyQuery query, Long userId);

    PageVO<TodoItemVO> overdue(TodoOverdueQuery query, Long userId);

    TodoDetailVO detail(Long todoId, Long userId);

    TodoDetailVO complete(Long todoId, TodoCompleteRequest request, Long userId);

    TodoDetailVO ignore(Long todoId, TodoIgnoreRequest request, Long userId);
}

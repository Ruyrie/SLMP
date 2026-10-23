package com.zhimu.server.biz.today.convert;

import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoRemindMapper;
import com.zhimu.server.biz.today.support.TodoLinks;
import com.zhimu.server.biz.today.vo.TodoDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/** 详情页“完成方式”文案：登记类写明登记了什么（与 App todo_90107.json 的“登记投喂记录”一致）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoConvertTest {

    @Mock private TodoRemindMapper remindMapper;
    @Mock private TodoLinks links;
    @Mock private AccountQueryService accountQuery;

    @Test
    void registerWayTextNamesRecordType() {
        TodoConvert convert = new TodoConvert(remindMapper, links, accountQuery);
        lenient().when(remindMapper.selectList(any())).thenReturn(List.of());
        LocalDate today = LocalDate.of(2026, 10, 19);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 30);

        TodoDetailVO feeding = convert.toDetail(todo("FEEDING", "REGISTER"), "东山猪场", false, today, now);
        TodoDetailVO disinfection = convert.toDetail(todo("DISINFECTION", "REGISTER"), "东山猪场", false, today, now);
        TodoDetailVO manual = convert.toDetail(todo("FEEDING", "MANUAL"), "东山猪场", false, today, now);

        assertThat(feeding.getCompleteWayText()).isEqualTo("登记投喂记录");
        assertThat(disinfection.getCompleteWayText()).isEqualTo("登记消毒记录");
        assertThat(manual.getCompleteWayText()).isEqualTo("手动标记，未生成记录");
    }

    @Test
    void overdueRegisterKeepsOverdueText() {
        TodoConvert convert = new TodoConvert(remindMapper, links, accountQuery);
        Todo todo = todo("FEEDING", "REGISTER");
        todo.setOverdueDone(true);
        LocalDate today = LocalDate.of(2026, 10, 19);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 30);
        // due_date 昨天，展示状态为 OVERDUE
        todo.setDueDate(today.minusDays(1));
        todo.setOverdueAt(LocalDateTime.of(2026, 10, 18, 9, 0));

        TodoDetailVO detail = convert.toDetail(todo, "东山猪场", false, today, now);

        assertThat(detail.getCompleteWayText()).isEqualTo("逾期登记");
    }

    private static Todo todo(String type, String completeWay) {
        Todo todo = new Todo();
        todo.setId(90107L);
        todo.setFarmId(1001L);
        todo.setTodoType(type);
        todo.setCategory("WORK");
        todo.setTitle("3 号育肥舍 · 早餐饲喂");
        todo.setStatus("DONE");
        todo.setPenId(2003L);
        todo.setPenName("3 号育肥舍");
        todo.setBatchId(3001L);
        todo.setBatchNo("ZM2610-031");
        todo.setReceiverScope("PEN_STAFF");
        todo.setDueDate(LocalDate.of(2026, 10, 19));
        todo.setTimeType("AT_TIME");
        todo.setDueTime(LocalTime.of(7, 0));
        todo.setSortTime(LocalDateTime.of(2026, 10, 19, 7, 0));
        todo.setCompletedAt(LocalDateTime.of(2026, 10, 19, 7, 12));
        todo.setCompleteWay(completeWay);
        todo.setOverdueDone(false);
        todo.setVersion(2);
        todo.setDeleted(false);
        return todo;
    }
}

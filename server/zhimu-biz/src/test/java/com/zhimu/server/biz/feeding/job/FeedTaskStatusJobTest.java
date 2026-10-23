package com.zhimu.server.biz.feeding.job;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhimu.server.biz.feeding.FeedingTableInfo;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 补登窗口结束的未执行任务流转 MISSED：只动 PENDING/OVERDUE，已关闭任务不受影响。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedTaskStatusJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 9, 30);

    @Mock private FeedTaskMapper taskMapper;
    @Mock private TodoSourceWriteService todoWriter;

    private FeedTaskStatusJob job;

    @BeforeAll
    static void initTableInfo() {
        FeedingTableInfo.ensure();
    }

    @BeforeEach
    void setUp() {
        job = new FeedTaskStatusJob(taskMapper, todoWriter);
        when(taskMapper.update(any(FeedTask.class), any())).thenReturn(1);
    }

    @Test
    void overdueTaskBecomesMissedAndTodoIsIgnored() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(70101L, "PENDING"), task(70107L, "DONE"), task(70102L, "MISSED"),
                task(70103L, "IGNORED"), task(70104L, "CANCELLED")));

        int count = job.markMissed(NOW);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<LambdaUpdateWrapper<FeedTask>> captor = wrapperCaptor();
        verify(taskMapper).update(any(FeedTask.class), captor.capture());
        LambdaUpdateWrapper<FeedTask> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("id", "status");
        assertThat(wrapper.getSqlSet()).contains("status=", "close_reason=");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(70101L, "MISSED", "超过补登期限")
                .doesNotContain(70107L, 70102L, 70103L, 70104L);
        verify(todoWriter).ignoreFromSource(1001L, "FEEDING", "FEED_TASK:70101", "超过补登期限");
    }

    @Test
    void taskStillInsideBackfillWindowIsUntouched() {
        FeedTask task = task(70101L, "PENDING");
        task.setBackfillEndAt(NOW.plusHours(1));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        int count = job.markMissed(NOW);

        assertThat(count).isZero();
        verify(taskMapper, never()).update(any(FeedTask.class), any());
        verify(todoWriter, never()).ignoreFromSource(any(), any(), any(), any());
    }

    @Test
    void concurrentStatusChangeDoesNotIgnoreTodo() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(70101L, "OVERDUE")));
        when(taskMapper.update(any(FeedTask.class), any())).thenReturn(0);

        int count = job.markMissed(NOW);

        assertThat(count).isZero();
        verify(todoWriter, never()).ignoreFromSource(any(), any(), any(), any());
    }

    private static FeedTask task(Long id, String status) {
        FeedTask task = new FeedTask();
        task.setId(id);
        task.setFarmId(1001L);
        task.setStatus(status);
        task.setTaskDate(NOW.toLocalDate().minusDays(2));
        task.setBackfillEndAt(NOW.minusHours(1));
        return task;
    }

    private static ArgumentCaptor<LambdaUpdateWrapper<FeedTask>> wrapperCaptor() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<FeedTask>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        return captor;
    }
}

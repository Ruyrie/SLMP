package com.zhimu.server.biz.demo.job;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DemoDataShiftRunnerTest {
    @Test
    void 正常模式及明确关闭时均不移动任何历史数据() {
        DemoDataShifter shifter = mock(DemoDataShifter.class);
        assertThat(new DemoDataShiftRunner(new MockEnvironment(), shifter).run()).isFalse();
        assertThat(new DemoDataShiftRunner(new MockEnvironment()
                .withProperty(DemoDataShiftRunner.ENABLED, "false"), shifter).run()).isFalse();
        verifyNoInteractions(shifter);
    }

    @Test
    void 固定样例演示只能显式选择开启() {
        DemoDataShifter shifter = mock(DemoDataShifter.class);
        when(shifter.shiftQuietly(any(), any())).thenReturn(
                new DemoDataShifter.Result(true, java.util.Map.of(), 1, 0, 0, java.util.List.of()));
        assertThat(new DemoDataShiftRunner(new MockEnvironment()
                .withProperty(DemoDataShiftRunner.ENABLED, "true"), shifter).run()).isTrue();
        verify(shifter).shiftQuietly(any(), any());
    }
}

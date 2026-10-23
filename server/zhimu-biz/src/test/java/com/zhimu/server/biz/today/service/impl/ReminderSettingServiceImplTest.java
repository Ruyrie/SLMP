package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.today.convert.ReminderSettingConvert;
import com.zhimu.server.biz.today.entity.ReminderSetting;
import com.zhimu.server.biz.today.mapper.ReminderSettingMapper;
import com.zhimu.server.biz.today.request.ReminderSettingPatchRequest;
import com.zhimu.server.biz.today.vo.ReminderSettingVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** API-T10/T11：默认补建、免打扰次日、时间步长与开始/结束相同校验。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderSettingServiceImplTest {

    @Mock private ReminderSettingMapper reminderSettingMapper;

    private ReminderSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReminderSettingServiceImpl(reminderSettingMapper, new ReminderSettingConvert());
    }

    @Test
    void getCreatesDefaultsWhenMissing() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);
        when(reminderSettingMapper.insert(any(ReminderSetting.class))).thenReturn(1);

        ReminderSettingVO vo = service.get(10001L);

        assertThat(vo.getTodoRemindEnabled()).isTrue();
        assertThat(vo.getDigestTime()).isEqualTo("07:00");
        assertThat(vo.getQuietStart()).isEqualTo("22:00");
        assertThat(vo.getQuietEnd()).isEqualTo("07:00");
        assertThat(vo.getQuietEndNextDay()).isTrue();
        assertThat(vo.getSummaryText())
                .isEqualTo("待办提醒开 · 每日汇总 07:00 · 免打扰 22:00–次日 07:00");
    }

    @Test
    void patchSwitchReturnsFullSetting() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(defaults());
        when(reminderSettingMapper.updateById(any(ReminderSetting.class))).thenReturn(1);
        ReminderSettingPatchRequest request = new ReminderSettingPatchRequest();
        request.setQuietEnabled(false);

        ReminderSettingVO vo = service.patch(request, 10001L);

        assertThat(vo.getQuietEnabled()).isFalse();
        assertThat(vo.getSummaryText()).isEqualTo("待办提醒开 · 每日汇总 07:00 · 免打扰关闭");
    }

    @Test
    void emptyPatchReturns40001() {
        assertThatThrownBy(() -> service.patch(new ReminderSettingPatchRequest(), 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void minuteNotMultipleOfFiveReturns40001() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(defaults());
        ReminderSettingPatchRequest request = new ReminderSettingPatchRequest();
        request.setDigestTime("07:03");

        assertThatThrownBy(() -> service.patch(request, 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void quietStartEqualsEndReturns40001() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(defaults());
        ReminderSettingPatchRequest request = new ReminderSettingPatchRequest();
        request.setQuietStart("22:00");
        request.setQuietEnd("22:00");

        assertThatThrownBy(() -> service.patch(request, 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    private static ReminderSetting defaults() {
        ReminderSetting setting = new ReminderSetting();
        setting.setId(1L);
        setting.setUserId(10001L);
        setting.setTodoRemindEnabled(true);
        setting.setDigestEnabled(true);
        setting.setDigestTime(LocalTime.of(7, 0));
        setting.setQuietEnabled(true);
        setting.setQuietStart(LocalTime.of(22, 0));
        setting.setQuietEnd(LocalTime.of(7, 0));
        return setting;
    }
}

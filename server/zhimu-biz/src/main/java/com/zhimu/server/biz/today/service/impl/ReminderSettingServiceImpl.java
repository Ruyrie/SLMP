package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.account.support.AccountPersonalDataGuard;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.today.api.ReminderSettingQueryService;
import com.zhimu.server.biz.today.convert.ReminderSettingConvert;
import com.zhimu.server.biz.today.entity.ReminderSetting;
import com.zhimu.server.biz.today.mapper.ReminderSettingMapper;
import com.zhimu.server.biz.today.request.ReminderSettingPatchRequest;
import com.zhimu.server.biz.today.service.ReminderSettingService;
import com.zhimu.server.biz.today.vo.ReminderSettingVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.ReminderSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * 提醒设置（API-T10、T11）：账号没有记录时按默认值补建；每次只改一个字段；
 * 时间 HH:mm、分钟为 5 的倍数，免打扰开始与结束不能相同（与已保存值合并后判断）。
 */
@Service
@RequiredArgsConstructor
public class ReminderSettingServiceImpl implements ReminderSettingService, ReminderSettingQueryService {

    private final AccountPersonalDataGuard personalDataGuard;
    private final ReminderSettingMapper reminderSettingMapper;
    private final ReminderSettingConvert convert;

    @Override
    @Transactional
    public ReminderSettingVO get(Long userId) {
        return convert.toVO(loadOrCreate(userId));
    }

    @Override
    @Transactional
    public ReminderSettingVO patch(ReminderSettingPatchRequest request, Long userId) {
        if (request == null || request.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少修改一项设置");
        }
        ReminderSetting setting = loadOrCreate(userId);
        if (request.getTodoRemindEnabled() != null) {
            setting.setTodoRemindEnabled(request.getTodoRemindEnabled());
        }
        if (request.getDigestEnabled() != null) {
            setting.setDigestEnabled(request.getDigestEnabled());
        }
        if (request.getQuietEnabled() != null) {
            setting.setQuietEnabled(request.getQuietEnabled());
        }
        if (request.getDigestTime() != null) {
            setting.setDigestTime(parseTime(request.getDigestTime()));
        }
        if (request.getQuietStart() != null) {
            setting.setQuietStart(parseTime(request.getQuietStart()));
        }
        if (request.getQuietEnd() != null) {
            setting.setQuietEnd(parseTime(request.getQuietEnd()));
        }
        if (setting.getQuietStart() != null && setting.getQuietStart().equals(setting.getQuietEnd())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始时间和结束时间不能相同");
        }
        reminderSettingMapper.updateById(setting);
        return convert.toVO(setting);
    }

    @Override
    @Transactional
    public ReminderSummaryVO summary(Long userId) {
        ReminderSetting setting = loadOrCreate(userId);
        return ReminderSummaryVO.builder()
                .digestEnabled(Boolean.TRUE.equals(setting.getDigestEnabled()))
                .digestTime(setting.getDigestTime())
                .quietEnabled(Boolean.TRUE.equals(setting.getQuietEnabled()))
                .quietStart(setting.getQuietStart())
                .quietEnd(setting.getQuietEnd())
                .build();
    }

    private ReminderSetting loadOrCreate(Long userId) {
        personalDataGuard.requireActive(userId);
        ReminderSetting setting = reminderSettingMapper.selectOne(Wrappers.<ReminderSetting>lambdaQuery()
                .eq(ReminderSetting::getUserId, userId)
                .last("LIMIT 1"));
        if (setting != null) {
            return setting;
        }
        setting = ReminderSettingConvert.defaults(userId);
        reminderSettingMapper.insert(setting);
        return setting;
    }

    /** HH:mm，00:00–23:55，分钟为 5 的倍数 */
    static LocalTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间格式为 HH:mm，分钟为 5 的倍数");
        }
        LocalTime time;
        try {
            time = LocalTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间格式为 HH:mm，分钟为 5 的倍数");
        }
        if (time.getMinute() % 5 != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间格式为 HH:mm，分钟为 5 的倍数");
        }
        return time.withSecond(0).withNano(0);
    }
}

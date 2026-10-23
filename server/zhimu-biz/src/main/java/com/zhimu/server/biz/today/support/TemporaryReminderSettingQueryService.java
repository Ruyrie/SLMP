package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.api.ReminderSettingQueryService;
import com.zhimu.server.common.vo.ReminderSummaryVO;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/** 临时实现：返回默认提醒设置（每日汇总 07:00 开启，免打扰 22:00–07:00 开启）。第 3 批删除 */
@Service
public class TemporaryReminderSettingQueryService implements ReminderSettingQueryService {

    @Override
    public ReminderSummaryVO summary(Long userId) {
        return ReminderSummaryVO.builder()
                .digestEnabled(true).digestTime(LocalTime.of(7, 0))
                .quietEnabled(true).quietStart(LocalTime.of(22, 0)).quietEnd(LocalTime.of(7, 0))
                .build();
    }
}
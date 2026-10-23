package com.zhimu.server.biz.today.api;

import com.zhimu.server.common.vo.ReminderSummaryVO;

/** 提醒设置摘要（模块03 实现，第 3 批）；在此之前返回默认值 */
public interface ReminderSettingQueryService {

    ReminderSummaryVO summary(Long userId);
}
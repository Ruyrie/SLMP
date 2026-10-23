package com.zhimu.server.biz.today.service;

import com.zhimu.server.biz.today.request.ReminderSettingPatchRequest;
import com.zhimu.server.biz.today.vo.ReminderSettingVO;

/** 提醒设置（API-T10、T11）。 */
public interface ReminderSettingService {

    ReminderSettingVO get(Long userId);

    ReminderSettingVO patch(ReminderSettingPatchRequest request, Long userId);
}

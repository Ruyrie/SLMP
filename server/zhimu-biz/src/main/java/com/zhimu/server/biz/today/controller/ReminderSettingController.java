package com.zhimu.server.biz.today.controller;

import com.zhimu.server.biz.today.request.ReminderSettingPatchRequest;
import com.zhimu.server.biz.today.service.ReminderSettingService;
import com.zhimu.server.biz.today.vo.ReminderSettingVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-T10、T11 提醒设置（本人，不需要养殖场上下文）。 */
@RestController
@RequestMapping("/api/v1/reminder-settings")
@RequiredArgsConstructor
public class ReminderSettingController {

    private final ReminderSettingService reminderSettingService;

    @GetMapping
    public ApiResponse<ReminderSettingVO> get() {
        return ApiResponse.ok(reminderSettingService.get(UserContext.requireUserId()));
    }

    @PatchMapping
    public ApiResponse<ReminderSettingVO> patch(@RequestBody ReminderSettingPatchRequest request) {
        return ApiResponse.ok(reminderSettingService.patch(request, UserContext.requireUserId()));
    }
}

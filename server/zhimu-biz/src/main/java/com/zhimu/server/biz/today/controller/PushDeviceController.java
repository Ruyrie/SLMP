package com.zhimu.server.biz.today.controller;

import com.zhimu.server.biz.today.request.PushDeviceRequest;
import com.zhimu.server.biz.today.service.PushDeviceService;
import com.zhimu.server.biz.today.vo.PushDeviceVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-T20、T21 推送设备登记与解绑（本人）。 */
@RestController
@RequestMapping("/api/v1/push-devices")
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @PutMapping
    public ApiResponse<PushDeviceVO> register(@Valid @RequestBody PushDeviceRequest request) {
        return ApiResponse.ok(pushDeviceService.register(request, UserContext.requireUserId()));
    }

    /** 退出登录时调用；设备不属于本人或不存在时直接成功 */
    @DeleteMapping("/{registration_id}")
    public ApiResponse<Object> unbind(@PathVariable("registration_id") String registrationId) {
        pushDeviceService.unbind(registrationId, UserContext.requireUserId());
        return new ApiResponse<>(0, ApiResponse.OK_MESSAGE, null);
    }
}

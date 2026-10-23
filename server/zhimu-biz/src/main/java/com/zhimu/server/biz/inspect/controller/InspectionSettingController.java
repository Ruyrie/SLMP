package com.zhimu.server.biz.inspect.controller;

import com.zhimu.server.biz.inspect.request.InspectionSettingRequest;
import com.zhimu.server.biz.inspect.service.InspectionSettingService;
import com.zhimu.server.biz.inspect.vo.InspectionSettingVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-I04 / I05 巡检截止时间与提醒规则。 */
@RestController
@RequestMapping("/api/v1/inspection-settings")
@RequireFarm
@RequiredArgsConstructor
public class InspectionSettingController {

    private final InspectionSettingService settingService;

    @GetMapping("/current")
    public ApiResponse<InspectionSettingVO> current() {
        return ApiResponse.ok(settingService.current());
    }

    @PutMapping("/current")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<InspectionSettingVO> update(@RequestBody InspectionSettingRequest request) {
        return ApiResponse.ok(settingService.update(request));
    }
}

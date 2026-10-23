package com.zhimu.server.biz.farm.controller;

import com.zhimu.server.biz.farm.query.FarmListQuery;
import com.zhimu.server.biz.farm.query.NameCheckQuery;
import com.zhimu.server.biz.farm.request.CreateFarmRequest;
import com.zhimu.server.biz.farm.request.FarmVersionRequest;
import com.zhimu.server.biz.farm.request.SubmitFarmChangeRequest;
import com.zhimu.server.biz.farm.request.UpdateFarmBasicRequest;
import com.zhimu.server.biz.farm.request.UpdateRegistrationRequest;
import com.zhimu.server.biz.farm.service.FarmAuditService;
import com.zhimu.server.biz.farm.service.FarmChangeService;
import com.zhimu.server.biz.farm.service.FarmService;
import com.zhimu.server.biz.farm.vo.FarmAuditVO;
import com.zhimu.server.biz.farm.vo.FarmBasicVO;
import com.zhimu.server.biz.farm.vo.FarmChangeVO;
import com.zhimu.server.biz.farm.vo.FarmContextVO;
import com.zhimu.server.biz.farm.vo.FarmDetailVO;
import com.zhimu.server.biz.farm.vo.FarmListItemVO;
import com.zhimu.server.biz.farm.vo.NameCheckVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-F01–F14：养殖场列表、上下文、切换、名称检查、新建与审核、信息与资料变更。 */
@RestController
@RequestMapping("/api/v1/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;
    private final FarmAuditService auditService;
    private final FarmChangeService changeService;

    /** F01 我的养殖场列表 */
    @GetMapping
    public ApiResponse<PageVO<FarmListItemVO>> listFarms(
            FarmListQuery query,
            @RequestHeader(value = "X-Farm-Id", required = false) String farmIdHeader) {
        Long headerFarmId = parseId(farmIdHeader);
        return ApiResponse.ok(farmService.listFarms(UserContext.requireUserId(), headerFarmId, query));
    }

    /** F02 养殖场上下文（允许待审核、已驳回） */
    @GetMapping("/{farmId:\\d+}/context")
    @RequireFarm(allowStatus = {FarmStatus.PENDING, FarmStatus.REJECTED, FarmStatus.NORMAL,
            FarmStatus.DISABLED})
    public ApiResponse<FarmContextVO> context(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(farmService.getContext(farmId, UserContext.requireUserId()));
    }

    /** F03 切换当前养殖场（待审核、已驳回也允许，权限全部 false 并带 restrict_tip） */
    @PostMapping("/{farmId:\\d+}/switch")
    @RequireFarm(allowStatus = {FarmStatus.NORMAL, FarmStatus.DISABLED, FarmStatus.PENDING,
            FarmStatus.REJECTED})
    public ApiResponse<FarmContextVO> switchFarm(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(farmService.switchFarm(farmId, UserContext.requireUserId()));
    }

    /** F04 名称重名检查（不按 X-Farm-Id 建立上下文） */
    @GetMapping("/name-check")
    @RateLimit(key = "farm-name-check", limit = 30, by = RateLimit.LimitBy.USER)
    public ApiResponse<NameCheckVO> checkName(NameCheckQuery query) {
        return ApiResponse.ok(farmService.checkName(UserContext.requireUserId(), query));
    }

    /** F05 新建养殖场并提交审核 */
    @PostMapping
    @Idempotent(required = true)
    public ApiResponse<FarmAuditVO> create(@RequestBody CreateFarmRequest request) {
        FarmAuditVO vo = auditService.create(UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已提交审核，结果会发到消息中心", vo);
    }

    /** F06 审核结果（不参与养殖场状态守卫，只校验所有者） */
    @GetMapping("/{farmId:\\d+}/audit")
    public ApiResponse<FarmAuditVO> audit(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(auditService.getAudit(farmId, UserContext.requireUserId()));
    }

    /** F07 修改注册信息 / 重新提交 */
    @PutMapping("/{farmId:\\d+}/registration")
    @RequireFarm(allowStatus = {FarmStatus.PENDING, FarmStatus.REJECTED})
    @Idempotent(required = true)
    public ApiResponse<FarmAuditVO> updateRegistration(@PathVariable("farmId") Long farmId,
                                                       @RequestBody UpdateRegistrationRequest request) {
        FarmAuditVO vo = auditService.resubmit(farmId, UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已重新提交审核", vo);
    }

    /** F08 删除未通过审核的养殖场 */
    @DeleteMapping("/{farmId:\\d+}")
    @RequireFarm(allowStatus = {FarmStatus.PENDING, FarmStatus.REJECTED})
    public ApiResponse<Object> deleteFarm(@PathVariable("farmId") Long farmId) {
        String name = farmService.deleteFarm(farmId, UserContext.requireUserId());
        return new ApiResponse<>(0, "已删除" + name, null);
    }

    /** F09 养殖场信息 */
    @GetMapping("/{farmId:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<FarmDetailVO> detail(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(farmService.detail(farmId, UserContext.requireUserId()));
    }

    /** F10 修改基本信息 */
    @PatchMapping("/{farmId:\\d+}/basic")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<FarmBasicVO> updateBasic(@PathVariable("farmId") Long farmId,
                                                @RequestBody UpdateFarmBasicRequest request) {
        FarmBasicVO vo = farmService.updateBasic(farmId, UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已保存基本信息", vo);
    }

    /** F11 停用养殖场 */
    @PostMapping("/{farmId:\\d+}/disable")
    @FarmRoles(FarmRole.OWNER)
    public ApiResponse<FarmDetailVO> disable(@PathVariable("farmId") Long farmId,
                                             @RequestBody FarmVersionRequest request) {
        FarmDetailVO vo = farmService.disable(farmId, UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已停用" + vo.getName(), vo);
    }

    /** F12 重新启用 */
    @PostMapping("/{farmId:\\d+}/enable")
    @FarmRoles(FarmRole.OWNER)
    @RequireFarm(allowStatus = {FarmStatus.NORMAL, FarmStatus.DISABLED})
    public ApiResponse<FarmDetailVO> enable(@PathVariable("farmId") Long farmId,
                                            @RequestBody FarmVersionRequest request) {
        FarmDetailVO vo = farmService.enable(farmId, UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已重新启用" + vo.getName(), vo);
    }

    /** F13 最近一次资料变更（没有记录时 data 为 null） */
    @GetMapping("/{farmId:\\d+}/changes/latest")
    @FarmRoles(FarmRole.OWNER)
    public ApiResponse<FarmChangeVO> latestChange(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(changeService.latest(farmId, UserContext.requireUserId()));
    }

    /** F14 提交资料变更 */
    @PostMapping("/{farmId:\\d+}/changes")
    @FarmRoles(FarmRole.OWNER)
    @Idempotent(required = true)
    public ApiResponse<FarmChangeVO> submitChange(@PathVariable("farmId") Long farmId,
                                                  @RequestBody SubmitFarmChangeRequest request) {
        FarmChangeVO vo = changeService.submit(farmId, UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已提交资料变更，审核期间旧信息照常有效", vo);
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

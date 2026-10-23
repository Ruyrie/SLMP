package com.zhimu.server.biz.common.controller;

import com.zhimu.server.biz.common.request.ChangeRequestListQuery;
import com.zhimu.server.biz.common.request.ChangeRequestSubmitRequest;
import com.zhimu.server.biz.common.service.impl.ChangeRequestApplicationService;
import com.zhimu.server.biz.common.vo.ChangeRequestFormVO;
import com.zhimu.server.biz.common.vo.ChangeRequestVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-U01–U04 修改申请（04 号 15.3）。
 * 不挂 @RequireFarm：跨养殖场按申请/原记录所属 farm 判权，忽略当前 X-Farm-Id；员工调用返回 40303。
 */
@RestController
@RequestMapping("/api/v1/change-requests")
@RequiredArgsConstructor
public class ChangeRequestController {

    private final ChangeRequestApplicationService changeRequestApplicationService;

    /** API-U01 提交修改申请的表单上下文 */
    @GetMapping("/form")
    public ApiResponse<ChangeRequestFormVO> form(@RequestParam("biz_type") String bizType,
                                                 @RequestParam("biz_id") Long bizId) {
        return ApiResponse.ok(changeRequestApplicationService.form(bizType, bizId));
    }

    /** API-U02 提交修改申请（UPDATE / DELETE / VOID），返回 ChangeRequestVO，不能为 null */
    @PostMapping
    @Idempotent
    public ApiResponse<ChangeRequestVO> submit(@RequestBody ChangeRequestSubmitRequest request) {
        return ApiResponse.ok(changeRequestApplicationService.submit(request));
    }

    /** API-U03 我的修改申请：跨养殖场聚合，status 可空，默认过滤 CANCELED */
    @GetMapping
    public ApiResponse<PageVO<ChangeRequestVO>> list(@Valid ChangeRequestListQuery query) {
        return ApiResponse.ok(changeRequestApplicationService.list(query));
    }

    /** API-U04 修改申请详情 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<ChangeRequestVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(changeRequestApplicationService.detail(id));
    }
}

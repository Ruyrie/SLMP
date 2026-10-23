package com.zhimu.server.biz.feeding.controller;

import com.zhimu.server.biz.feeding.request.FeedRecordCreateRequest;
import com.zhimu.server.biz.feeding.request.FeedRecordUpdateRequest;
import com.zhimu.server.biz.feeding.request.FeedRecordVoidRequest;
import com.zhimu.server.biz.feeding.service.FeedRecordService;
import com.zhimu.server.biz.feeding.vo.FeedRecordVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-G04–G08：确认投喂、记录详情/修改/作废、按幂等键查询。 */
@RestController
@RequestMapping("/api/v1/feed-records")
@RequireFarm
@RequiredArgsConstructor
public class FeedRecordController {

    private final FeedRecordService feedRecordService;

    /** G04 确认投喂（要求 Idempotency-Key） */
    @PostMapping
    @Idempotent(required = true)
    public ApiResponse<FeedRecordVO.Detail> create(
            @RequestBody FeedRecordCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        FeedRecordVO.Detail detail = feedRecordService.create(request, idemKey);
        String message = Boolean.TRUE.equals(detail.getIsProxy())
                ? "已代" + (detail.getProxyExecutorName() == null ? "执行人" : detail.getProxyExecutorName())
                + "登记投喂，待办已自动完成"
                : "已登记投喂，待办已自动完成";
        return new ApiResponse<>(0, message, detail);
    }

    /** G05 投喂记录详情 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<FeedRecordVO.Detail> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(feedRecordService.detail(id));
    }

    /** G06 修改当天投喂记录 */
    @PutMapping("/{id:\\d+}")
    public ApiResponse<FeedRecordVO.Detail> update(@PathVariable("id") Long id,
                                                   @RequestBody FeedRecordUpdateRequest request) {
        return new ApiResponse<>(0, "已修改投喂记录，饲料出库已同步更正",
                feedRecordService.update(id, request));
    }

    /** G07 作废当天投喂记录 */
    @PostMapping("/{id:\\d+}/void")
    public ApiResponse<FeedRecordVO.Detail> voidRecord(@PathVariable("id") Long id,
                                                       @RequestBody FeedRecordVoidRequest request) {
        return new ApiResponse<>(0, "已作废投喂记录，饲料出库已同步作废",
                feedRecordService.voidRecord(id, request));
    }

    /** G08 按幂等键查询投喂（提交超时后） */
    @GetMapping("/lookup")
    public ApiResponse<FeedRecordVO.Lookup> lookup(@RequestParam("request_id") String requestId) {
        return ApiResponse.ok(feedRecordService.lookup(requestId));
    }
}

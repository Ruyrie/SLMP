package com.zhimu.server.biz.ai.controller;

import com.zhimu.server.biz.ai.query.AiMessageQuery;
import com.zhimu.server.biz.ai.query.AiSessionQuery;
import com.zhimu.server.biz.ai.request.AiSessionBatchRequest;
import com.zhimu.server.biz.ai.service.AiSessionService;
import com.zhimu.server.biz.ai.vo.AiMessageSliceVO;
import com.zhimu.server.biz.ai.vo.AiSessionDetailVO;
import com.zhimu.server.biz.ai.vo.AiSessionItemVO;
import com.zhimu.server.biz.ai.vo.LinkableBatchesVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-K01–K05：历史会话、会话详情、按轮次切片、修改关联批次、可关联批次。
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@RequireFarm
public class AiSessionController {

    private final AiSessionService sessionService;

    @GetMapping("/sessions")
    public ApiResponse<PageVO<AiSessionItemVO>> sessions(AiSessionQuery query) {
        return ApiResponse.ok(sessionService.listSessions(UserContext.requireUserId(), query));
    }

    @GetMapping("/sessions/{id:\\d+}")
    public ApiResponse<AiSessionDetailVO> session(@PathVariable("id") Long id) {
        return ApiResponse.ok(sessionService.detail(UserContext.requireUserId(), id));
    }

    @GetMapping("/sessions/{id:\\d+}/messages")
    public ApiResponse<AiMessageSliceVO> messages(@PathVariable("id") Long id, AiMessageQuery query) {
        return ApiResponse.ok(sessionService.messages(UserContext.requireUserId(), id, query));
    }

    @PatchMapping("/sessions/{id:\\d+}")
    public ApiResponse<AiSessionDetailVO> changeBatch(@PathVariable("id") Long id,
                                                      @RequestBody(required = false) AiSessionBatchRequest request) {
        Long batchId = request == null ? null : request.getBatchId();
        return ApiResponse.ok(sessionService.changeBatch(UserContext.requireUserId(), id, batchId));
    }

    @GetMapping("/linkable-batches")
    public ApiResponse<LinkableBatchesVO> linkableBatches() {
        return ApiResponse.ok(sessionService.linkableBatches(UserContext.requireUserId()));
    }
}

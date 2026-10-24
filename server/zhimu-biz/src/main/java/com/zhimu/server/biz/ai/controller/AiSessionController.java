package com.zhimu.server.biz.ai.controller;

import com.zhimu.server.biz.ai.query.AiMessageQuery;
import com.zhimu.server.biz.ai.query.AiSessionQuery;
import com.zhimu.server.biz.ai.request.AiSessionBatchRequest;
import com.zhimu.server.biz.ai.request.AiSessionCreateRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-K01–K05：历史会话、会话详情、按轮次切片、修改会话、可关联批次；
 * 以及新建空会话、删除会话（App 旧接口保持不变）。
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

    /** 新建空会话：可带 title、batch_id；归属当前用户与当前养殖场 */
    @PostMapping("/sessions")
    public ApiResponse<AiSessionDetailVO> createSession(
            @RequestBody(required = false) AiSessionCreateRequest request) {
        return ApiResponse.ok(sessionService.create(UserContext.requireUserId(), request));
    }

    @GetMapping("/sessions/{id:\\d+}")
    public ApiResponse<AiSessionDetailVO> session(@PathVariable("id") Long id) {
        return ApiResponse.ok(sessionService.detail(UserContext.requireUserId(), id));
    }

    @GetMapping("/sessions/{id:\\d+}/messages")
    public ApiResponse<AiMessageSliceVO> messages(@PathVariable("id") Long id, AiMessageQuery query) {
        return ApiResponse.ok(sessionService.messages(UserContext.requireUserId(), id, query));
    }

    /** 修改会话：支持 title 和/或 batch_id；只传 title 时保留原关联批次 */
    @PatchMapping("/sessions/{id:\\d+}")
    public ApiResponse<AiSessionDetailVO> changeSession(@PathVariable("id") Long id,
                                                        @RequestBody(required = false) AiSessionBatchRequest request) {
        return ApiResponse.ok(sessionService.update(UserContext.requireUserId(), id, request));
    }

    /** 删除会话：逻辑删除本人会话及其消息，重复删除返回成功 */
    @DeleteMapping("/sessions/{id:\\d+}")
    public ApiResponse<Object> deleteSession(@PathVariable("id") Long id) {
        sessionService.delete(UserContext.requireUserId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/linkable-batches")
    public ApiResponse<LinkableBatchesVO> linkableBatches() {
        return ApiResponse.ok(sessionService.linkableBatches(UserContext.requireUserId()));
    }
}

package com.zhimu.server.biz.ai.controller;

import com.zhimu.server.biz.ai.service.KnowledgeViewService;
import com.zhimu.server.biz.ai.vo.KnowledgeDetailVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-K12 知识原文。
 */
@RestController
@RequestMapping("/api/v1/knowledge-items")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeViewService knowledgeViewService;

    @GetMapping("/{id:\\d+}")
    public ApiResponse<KnowledgeDetailVO> detail(@PathVariable("id") Long id,
                                                 @RequestParam(value = "citation_id", required = false) Long citationId) {
        return ApiResponse.ok(knowledgeViewService.detail(UserContext.requireUserId(), id, citationId));
    }
}

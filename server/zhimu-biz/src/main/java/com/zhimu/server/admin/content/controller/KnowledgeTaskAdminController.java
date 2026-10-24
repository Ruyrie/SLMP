package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.KnowledgeTaskQuery;
import com.zhimu.server.admin.content.request.KnowledgeTaskCreateRequest;
import com.zhimu.server.admin.content.request.TaskCloseRequest;
import com.zhimu.server.admin.content.service.KnowledgeTaskService;
import com.zhimu.server.admin.content.vo.KnowledgeTaskVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WA-C04 知识补充任务：ADM-C54–C56。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class KnowledgeTaskAdminController {

    private final KnowledgeTaskService taskService;

    /** ADM-C54 补充任务列表 */
    @GetMapping("/knowledge-tasks")
    @AdminPermission("WA-C04:VIEW")
    public ApiResponse<PageVO<KnowledgeTaskVO>> list(@Valid KnowledgeTaskQuery query) {
        return ApiResponse.ok(taskService.page(query));
    }

    /** ADM-C55 创建补充任务 */
    @PostMapping("/knowledge-tasks")
    @Idempotent(required = true)
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeTaskVO> create(@Valid @RequestBody KnowledgeTaskCreateRequest request) {
        return new ApiResponse<>(0, "已创建知识补充任务", taskService.create(request));
    }

    /** ADM-C56 关闭任务 */
    @PostMapping("/knowledge-tasks/{id:\\d+}/close")
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeTaskVO> close(@PathVariable("id") Long id,
                                              @Valid @RequestBody TaskCloseRequest request) {
        return new ApiResponse<>(0, "已关闭任务", taskService.close(id, request));
    }
}

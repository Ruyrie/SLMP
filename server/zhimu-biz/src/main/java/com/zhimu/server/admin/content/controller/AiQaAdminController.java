package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.service.AiQaAdminService;
import com.zhimu.server.admin.content.vo.AiQaVos;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端-02 WA-C05 AI 问答与更正（ADM-C57–C64）。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
@AdminPermission("WA-C05:VIEW")
public class AiQaAdminController {

    private final AiQaAdminService service;

    /** ADM-C65 导出问答记录 */
    @PostMapping("/ai-qa/export")
    @AdminPermission("WA-C05:HANDLE")
    public ApiResponse<AiQaVos.ExportVO> export(@RequestBody(required = false) ContentQueries.QaQuery query) {
        return new ApiResponse<>(0, "已开始导出，完成后在浏览器下载",
                service.export(query == null ? new ContentQueries.QaQuery() : query));
    }

    /** ADM-C57 问答指标卡 */
    @GetMapping("/ai-qa/summary")
    public ApiResponse<AiQaVos.SummaryVO> summary(@Valid ContentQueries.QaSummaryQuery query) {
        return ApiResponse.ok(service.summary(query));
    }

    /** ADM-C58 问答记录 */
    @GetMapping("/ai-qa")
    public ApiResponse<PageVO<AiQaVos.RowVO>> list(@Valid ContentQueries.QaQuery query) {
        return ApiResponse.ok(service.page(query));
    }

    /** ADM-C59 问答详情 */
    @GetMapping("/ai-qa/{message_id:\\d+}")
    public ApiResponse<AiQaVos.DetailVO> detail(@PathVariable("message_id") Long messageId) {
        return ApiResponse.ok(service.detail(messageId));
    }

    /** ADM-C60 发布更正说明 */
    @PostMapping("/ai-qa/{message_id:\\d+}/correction")
    @Idempotent(required = true)
    @AdminPermission("WA-C05:HANDLE")
    public ApiResponse<AiQaVos.CorrectionVO> publishCorrection(
            @PathVariable("message_id") Long messageId,
            @RequestBody ContentRequests.CorrectionPublishRequest request) {
        return new ApiResponse<>(0, "已发布更正说明，提问人再次打开会话时可以看到",
                service.publishCorrection(messageId, request));
    }

    /** ADM-C61 修改更正说明 */
    @PutMapping("/ai-qa/{message_id:\\d+}/correction")
    @AdminPermission("WA-C05:HANDLE")
    public ApiResponse<AiQaVos.CorrectionVO> updateCorrection(
            @PathVariable("message_id") Long messageId,
            @RequestBody ContentRequests.CorrectionUpdateRequest request) {
        return new ApiResponse<>(0, "已修改更正说明", service.updateCorrection(messageId, request));
    }

    /** ADM-C62 撤回更正说明 */
    @PostMapping("/ai-qa/{message_id:\\d+}/correction/withdraw")
    @AdminPermission("WA-C05:HANDLE")
    public ApiResponse<AiQaVos.CorrectionVO> withdrawCorrection(
            @PathVariable("message_id") Long messageId,
            @RequestBody(required = false) ContentRequests.CorrectionWithdrawRequest request) {
        return new ApiResponse<>(0, "已撤回更正说明", service.withdrawCorrection(messageId, request));
    }

    /** ADM-C63 标为无需处理 */
    @PostMapping("/ai-qa/{message_id:\\d+}/no-action")
    @AdminPermission("WA-C05:HANDLE")
    public ApiResponse<Void> noAction(@PathVariable("message_id") Long messageId,
                                      @RequestBody(required = false) ContentRequests.NoActionRequest request) {
        service.noAction(messageId, request);
        return new ApiResponse<>(0, "已标为无需处理", null);
    }

    /** ADM-C64 相似问题与建议标题 */
    @GetMapping("/ai-qa/{message_id:\\d+}/task-suggestion")
    public ApiResponse<AiQaVos.TaskSuggestionVO> taskSuggestion(@PathVariable("message_id") Long messageId) {
        return ApiResponse.ok(service.taskSuggestion(messageId));
    }

    /**
     * 转为知识补充任务（ADM-C55 的 WA-C05 入口）。知识任务表由管理端-02 下篇落地前，
     * 这里先写处理状态、抽查记录与被指派人管理端消息，任务 id 暂为空。
     */
    @PostMapping("/ai-qa/{message_id:\\d+}/task")
    @AdminPermission({"WA-C05:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<AiQaVos.TaskRefVO> createTask(@PathVariable("message_id") Long messageId,
                                                     @RequestBody ContentRequests.TaskCreateRequest request) {
        return new ApiResponse<>(0, "已创建知识补充任务，已通知被指派人", service.createTask(messageId, request));
    }
}

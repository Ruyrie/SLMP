package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.service.NoticeAdminService;
import com.zhimu.server.admin.content.service.NoticeAudienceService;
import com.zhimu.server.admin.content.service.NoticeDispatchService;
import com.zhimu.server.admin.content.vo.NoticeVos;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端-02 WA-C01 通知发布（ADM-C01–C10）。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
@AdminPermission("WA-C01:VIEW")
public class NoticeAdminController {

    private final NoticeAdminService noticeAdminService;
    private final NoticeAudienceService audienceService;
    private final NoticeDispatchService dispatchService;

    /** ADM-C01 通知指标卡 */
    @GetMapping("/notices/summary")
    public ApiResponse<NoticeVos.SummaryVO> summary() {
        return ApiResponse.ok(noticeAdminService.summary());
    }

    /** ADM-C02 通知列表与分段计数 */
    @GetMapping("/notices")
    public ApiResponse<PageVO<NoticeVos.RowVO>> list(@Valid ContentQueries.NoticeQuery query) {
        return ApiResponse.ok(noticeAdminService.page(query));
    }

    /** ADM-C03 预计送达账号与养殖场数 */
    @PostMapping("/notices/audience-estimate")
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<NoticeVos.AudienceVO> estimate(@RequestBody ContentRequests.AudienceRequest request) {
        return ApiResponse.ok(audienceService.estimate(request == null ? new ContentRequests.AudienceRequest() : request));
    }

    /** ADM-C04 新建：存草稿 / 定时 / 立即发送 */
    @PostMapping("/notices")
    @Idempotent(required = true)
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<NoticeVos.NoticeVO> create(@RequestBody @Valid ContentRequests.NoticeSaveRequest request) {
        NoticeVos.NoticeVO vo = noticeAdminService.create(request);
        if ("SENT".equals(vo.getStatus())) {
            dispatchService.dispatch(vo.getId());
            NoticeVos.NoticeVO saved = noticeAdminService.listOne(vo.getId());
            int count = saved.getDeliveredCount() != null && saved.getDeliveredCount() > 0
                    ? saved.getDeliveredCount() : saved.getEstAccountCount();
            return new ApiResponse<>(0, "已发送，正在推送给 " + count + " 个账号", saved);
        }
        String message = "SCHEDULED".equals(vo.getStatus())
                ? "已设为定时发送，" + scheduledText(vo.getScheduledAt()) + " 发送"
                : "已保存草稿";
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-C05 编辑草稿或待发送 */
    @PutMapping("/notices/{id:\\d+}")
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<NoticeVos.NoticeVO> update(@PathVariable("id") Long id,
                                                  @RequestBody @Valid ContentRequests.NoticeSaveRequest request) {
        NoticeVos.NoticeVO vo = noticeAdminService.update(id, request);
        if ("SENT".equals(vo.getStatus())) {
            dispatchService.dispatch(vo.getId());
            NoticeVos.NoticeVO saved = noticeAdminService.listOne(vo.getId());
            int count = saved.getDeliveredCount() != null && saved.getDeliveredCount() > 0
                    ? saved.getDeliveredCount() : saved.getEstAccountCount();
            return new ApiResponse<>(0, "已发送，正在推送给 " + count + " 个账号", saved);
        }
        String message = "SCHEDULED".equals(vo.getStatus())
                ? "已设为定时发送，" + scheduledText(vo.getScheduledAt()) + " 发送"
                : "已保存草稿";
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-C06 详情、阅读情况、操作记录 */
    @GetMapping("/notices/{id:\\d+}")
    public ApiResponse<NoticeVos.DetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(noticeAdminService.detail(id));
    }

    /** ADM-C07 取消发送 */
    @PostMapping("/notices/{id:\\d+}/cancel")
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<NoticeVos.NoticeVO> cancel(@PathVariable("id") Long id,
                                                  @RequestBody(required = false) ContentRequests.NoticeReasonRequest request) {
        return new ApiResponse<>(0, "已取消发送", noticeAdminService.cancel(id, request));
    }

    /** ADM-C08 撤回 */
    @PostMapping("/notices/{id:\\d+}/revoke")
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<NoticeVos.NoticeVO> revoke(@PathVariable("id") Long id,
                                                  @RequestBody(required = false) ContentRequests.NoticeReasonRequest request) {
        return new ApiResponse<>(0, "已撤回这条通知", noticeAdminService.revoke(id, request));
    }

    /** ADM-C09 删除草稿 */
    @DeleteMapping("/notices/{id:\\d+}")
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<Void> delete(@PathVariable("id") Long id,
                                    @RequestParam(value = "version", required = false) Integer version) {
        noticeAdminService.delete(id, version);
        return new ApiResponse<>(0, "已删除草稿", null);
    }

    /** ADM-C10 导出阅读数据（只导出已发送/已撤回） */
    @PostMapping("/notices/read-stats/export")
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<NoticeVos.ExportVO> export(@RequestBody(required = false) ContentQueries.NoticeQuery query) {
        return new ApiResponse<>(0, "已开始导出，完成后在浏览器下载",
                noticeAdminService.export(query == null ? new ContentQueries.NoticeQuery() : query));
    }

    /** 养殖场搜索（通知接收范围“指定养殖场”用，随 ADM-C03 提供） */
    @GetMapping("/notices/farm-options")
    @AdminPermission("WA-C01:HANDLE")
    public ApiResponse<List<NoticeVos.FarmBrief>> farmOptions(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(audienceService.searchFarms(keyword));
    }

    private static String scheduledText(java.time.LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm").format(time);
    }
}

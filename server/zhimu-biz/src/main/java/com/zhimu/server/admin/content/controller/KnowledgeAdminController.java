package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.AdminKnowledgeQuery;
import com.zhimu.server.admin.content.request.KnowledgePublishRequest;
import com.zhimu.server.admin.content.request.KnowledgeRollbackRequest;
import com.zhimu.server.admin.content.request.KnowledgeSaveRequest;
import com.zhimu.server.admin.content.request.OffShelfRequest;
import com.zhimu.server.admin.content.request.VersionRequest;
import com.zhimu.server.admin.content.service.KnowledgeAdminService;
import com.zhimu.server.admin.content.vo.KnowledgeEditVO;
import com.zhimu.server.admin.content.vo.KnowledgePageVO;
import com.zhimu.server.admin.content.vo.KnowledgeVO;
import com.zhimu.server.admin.content.vo.KnowledgeVersionItemVO;
import com.zhimu.server.admin.content.vo.KnowledgeVersionVO;
import com.zhimu.server.common.api.ApiResponse;
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

/** WA-C04 知识库管理：ADM-C44–C53（C66 删除草稿为补口扩展）。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class KnowledgeAdminController {

    private final KnowledgeAdminService knowledgeService;

    /** ADM-C44 知识条目列表 */
    @GetMapping("/knowledge")
    @AdminPermission("WA-C04:VIEW")
    public ApiResponse<KnowledgePageVO> list(@Valid AdminKnowledgeQuery query) {
        return ApiResponse.ok(knowledgeService.page(query));
    }

    /** ADM-C45 编辑数据（线上、草稿、引用） */
    @GetMapping("/knowledge/{id:\\d+}")
    @AdminPermission("WA-C04:VIEW")
    public ApiResponse<KnowledgeEditVO> edit(@PathVariable("id") Long id) {
        return ApiResponse.ok(knowledgeService.edit(id));
    }

    /** ADM-C46 新建条目 */
    @PostMapping("/knowledge")
    @Idempotent(required = true)
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeVO> create(@Valid @RequestBody KnowledgeSaveRequest request) {
        KnowledgeVO vo = knowledgeService.create(request);
        String message = "ON_SHELF".equals(vo.getStatus()) ? "已发布，AI 将按新版本回答" : "已保存草稿";
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-C47 保存草稿 */
    @PutMapping("/knowledge/{id:\\d+}/draft")
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeVO> saveDraft(@PathVariable("id") Long id,
                                              @Valid @RequestBody KnowledgeSaveRequest request) {
        return new ApiResponse<>(0, "已保存草稿", knowledgeService.saveDraft(id, request));
    }

    /** ADM-C48 发布新版本 */
    @PostMapping("/knowledge/{id:\\d+}/publish")
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeVO> publish(@PathVariable("id") Long id,
                                            @Valid @RequestBody KnowledgePublishRequest request) {
        KnowledgeVO vo = knowledgeService.publish(id, request);
        return new ApiResponse<>(0, "已发布版本 " + vo.getOnlineVersionNo() + "，AI 将按新版本回答", vo);
    }

    /** ADM-C49 版本列表 */
    @GetMapping("/knowledge/{id:\\d+}/versions")
    @AdminPermission("WA-C04:VIEW")
    public ApiResponse<List<KnowledgeVersionItemVO>> versions(@PathVariable("id") Long id) {
        return ApiResponse.ok(knowledgeService.versions(id));
    }

    /** ADM-C50 版本正文与差异 */
    @GetMapping("/knowledge/{id:\\d+}/versions/{version_no:\\d+}")
    @AdminPermission("WA-C04:VIEW")
    public ApiResponse<KnowledgeVersionVO> version(@PathVariable("id") Long id,
                                                   @PathVariable("version_no") Integer versionNo) {
        return ApiResponse.ok(knowledgeService.version(id, versionNo));
    }

    /** ADM-C51 回退版本 */
    @PostMapping("/knowledge/{id:\\d+}/rollback")
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeVO> rollback(@PathVariable("id") Long id,
                                             @Valid @RequestBody KnowledgeRollbackRequest request) {
        KnowledgeVO vo = knowledgeService.rollback(id, request);
        return new ApiResponse<>(0, "已回退到版本 " + request.getTargetVersionNo() + " 的内容，生成版本 "
                + vo.getNewVersionNo(), vo);
    }

    /** ADM-C52 下架 */
    @PostMapping("/knowledge/{id:\\d+}/off-shelf")
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeVO> offShelf(@PathVariable("id") Long id,
                                             @Valid @RequestBody OffShelfRequest request) {
        return new ApiResponse<>(0, "已下架这条知识", knowledgeService.offShelf(id, request));
    }

    /** ADM-C53 上架 */
    @PostMapping("/knowledge/{id:\\d+}/on-shelf")
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<KnowledgeVO> onShelf(@PathVariable("id") Long id,
                                            @Valid @RequestBody VersionRequest request) {
        return new ApiResponse<>(0, "已上架", knowledgeService.onShelf(id, request.getVersion()));
    }

    /** ADM-C66（扩展）删除草稿 */
    @DeleteMapping("/knowledge/{id:\\d+}")
    @AdminPermission("WA-C04:HANDLE")
    public ApiResponse<Void> deleteDraft(@PathVariable("id") Long id,
                                         @RequestParam("version") Integer version) {
        knowledgeService.deleteDraft(id, version);
        return ApiResponse.ok();
    }
}

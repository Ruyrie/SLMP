package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.request.InspectItemSaveRequest;
import com.zhimu.server.admin.base.request.InspectTemplateCreateRequest;
import com.zhimu.server.admin.base.request.TemplateItemOrderRequest;
import com.zhimu.server.admin.base.request.TemplatePublishRequest;
import com.zhimu.server.admin.base.request.TemplateRollbackRequest;
import com.zhimu.server.admin.base.request.VersionRequest;
import com.zhimu.server.admin.base.service.InspectTemplateAdminService;
import com.zhimu.server.admin.base.vo.InspectItemVO;
import com.zhimu.server.admin.base.vo.InspectTemplateListVO;
import com.zhimu.server.admin.base.vo.InspectTemplateVO;
import com.zhimu.server.admin.base.vo.TemplateVersionVO;
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

/** 管理端-03 WA-D04：ADM-D39–D48 巡检项目模板。 */
@RestController
@RequestMapping("/admin/v1/inspect-templates")
@RequiredArgsConstructor
@AdminPermission("WA-D04:VIEW")
public class InspectTemplateAdminController {

    private final InspectTemplateAdminService service;

    /** ADM-D39 巡检模板列表（未建模板的启用品类 template_id 为空） */
    @GetMapping
    public ApiResponse<List<InspectTemplateListVO>> list() {
        return ApiResponse.ok(service.list());
    }

    /** ADM-D40 模板详情与用户端预览 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<InspectTemplateVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** ADM-D41 新建模板 */
    @PostMapping
    @Idempotent(required = true)
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<InspectTemplateVO> create(@Valid @RequestBody InspectTemplateCreateRequest request) {
        InspectTemplateVO vo = service.create(request);
        return new ApiResponse<>(0, "已保存“" + vo.getName() + "”", vo);
    }

    /** ADM-D42 添加项目 */
    @PostMapping("/{id:\\d+}/items")
    @Idempotent(required = true)
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<InspectItemVO> addItem(@PathVariable("id") Long id,
                                              @Valid @RequestBody InspectItemSaveRequest request) {
        return new ApiResponse<>(0, "已保存，发布后生效", service.addItem(id, request));
    }

    /** ADM-D43 编辑项目、结果、启停 */
    @PutMapping("/{id:\\d+}/items/{item_id:\\d+}")
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<InspectItemVO> updateItem(@PathVariable("id") Long id,
                                                 @PathVariable("item_id") Long itemId,
                                                 @Valid @RequestBody InspectItemSaveRequest request) {
        return new ApiResponse<>(0, "已保存，发布后生效", service.updateItem(id, itemId, request));
    }

    /** ADM-D44 删除未发布的新增项（线上项目只能停用） */
    @DeleteMapping("/{id:\\d+}/items/{item_id:\\d+}")
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<Void> deleteItem(@PathVariable("id") Long id,
                                        @PathVariable("item_id") Long itemId,
                                        @RequestParam(value = "version", required = false) Integer version) {
        service.deleteItem(id, itemId, version);
        return new ApiResponse<>(0, "已删除，发布后生效", null);
    }

    /** ADM-D45 调整顺序 */
    @PutMapping("/{id:\\d+}/items/order")
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<Void> order(@PathVariable("id") Long id,
                                   @Valid @RequestBody TemplateItemOrderRequest request) {
        service.order(id, request);
        return new ApiResponse<>(0, "已调整顺序，发布后生效", null);
    }

    /** ADM-D46 发布修改 */
    @PostMapping("/{id:\\d+}/publish")
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<TemplateVersionVO<InspectItemVO>> publish(
            @PathVariable("id") Long id, @Valid @RequestBody TemplatePublishRequest request) {
        TemplateVersionVO<InspectItemVO> vo = service.publish(id, request);
        return new ApiResponse<>(0,
                "已发布第 " + vo.getVersionNo() + " 版，下一次登记巡检起生效", vo);
    }

    /** ADM-D47 放弃未发布的修改 */
    @PostMapping("/{id:\\d+}/discard")
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<Void> discard(@PathVariable("id") Long id,
                                     @Valid @RequestBody VersionRequest request) {
        service.discard(id, request.getVersion());
        return new ApiResponse<>(0, "已放弃未发布的修改", null);
    }

    /** ADM-D48 版本记录 */
    @GetMapping("/{id:\\d+}/versions")
    public ApiResponse<List<TemplateVersionVO<InspectItemVO>>> versions(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.versions(id));
    }

    /** 回退到历史版本（复制为草稿，发布后生效；契约文档记为 ADM-D48A） */
    @PostMapping("/{id:\\d+}/rollback")
    @AdminPermission("WA-D04:HANDLE")
    public ApiResponse<InspectTemplateVO> rollback(@PathVariable("id") Long id,
                                                   @Valid @RequestBody TemplateRollbackRequest request) {
        InspectTemplateVO vo = service.rollback(id, request);
        return new ApiResponse<>(0, "已回退到第 " + request.getTargetVersionNo() + " 版的内容，发布后生效", vo);
    }
}

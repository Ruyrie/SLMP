package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.query.ImmuneTemplateQuery;
import com.zhimu.server.admin.base.request.ImmuneItemSaveRequest;
import com.zhimu.server.admin.base.request.ImmuneTemplateCreateRequest;
import com.zhimu.server.admin.base.request.TemplateItemOrderRequest;
import com.zhimu.server.admin.base.request.TemplatePublishRequest;
import com.zhimu.server.admin.base.request.TemplateRollbackRequest;
import com.zhimu.server.admin.base.request.VersionRequest;
import com.zhimu.server.admin.base.service.ImmuneTemplateAdminService;
import com.zhimu.server.admin.base.vo.ImmuneItemVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateGroupVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateVO;
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

/** 管理端-03 WA-D02：ADM-D14–D23 免疫程序模板。 */
@RestController
@RequestMapping("/admin/v1/immune-templates")
@RequiredArgsConstructor
@AdminPermission("WA-D02:VIEW")
public class ImmuneTemplateAdminController {

    private final ImmuneTemplateAdminService service;

    /** ADM-D14 模板列表（按品类分组，含未配置品种） */
    @GetMapping
    public ApiResponse<List<ImmuneTemplateGroupVO>> list(@Valid ImmuneTemplateQuery query) {
        return ApiResponse.ok(service.list(query));
    }

    /** ADM-D15 模板详情（有草稿时为草稿项并带 change_type / before） */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<ImmuneTemplateVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** ADM-D16 新建 / 复制模板 */
    @PostMapping
    @Idempotent(required = true)
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<ImmuneTemplateVO> create(@Valid @RequestBody ImmuneTemplateCreateRequest request) {
        ImmuneTemplateVO vo = service.create(request);
        return new ApiResponse<>(0, "已保存“" + vo.getName() + "”", vo);
    }

    /** ADM-D17 添加项目 */
    @PostMapping("/{id:\\d+}/items")
    @Idempotent(required = true)
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<ImmuneItemVO> addItem(@PathVariable("id") Long id,
                                             @Valid @RequestBody ImmuneItemSaveRequest request) {
        return new ApiResponse<>(0, "已保存，发布后生效", service.addItem(id, request));
    }

    /** ADM-D18 编辑项目 */
    @PutMapping("/{id:\\d+}/items/{item_id:\\d+}")
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<ImmuneItemVO> updateItem(@PathVariable("id") Long id,
                                                @PathVariable("item_id") Long itemId,
                                                @Valid @RequestBody ImmuneItemSaveRequest request) {
        return new ApiResponse<>(0, "已保存，发布后生效", service.updateItem(id, itemId, request));
    }

    /** ADM-D19 删除项目（已发布来源项标记 DELETED，发布时移除） */
    @DeleteMapping("/{id:\\d+}/items/{item_id:\\d+}")
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<Void> deleteItem(@PathVariable("id") Long id,
                                        @PathVariable("item_id") Long itemId,
                                        @RequestParam(value = "version", required = false) Integer version) {
        service.deleteItem(id, itemId, version);
        return new ApiResponse<>(0, "已删除，发布后生效", null);
    }

    /** ADM-D20 调整顺序 */
    @PutMapping("/{id:\\d+}/items/order")
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<Void> order(@PathVariable("id") Long id,
                                   @Valid @RequestBody TemplateItemOrderRequest request) {
        service.order(id, request);
        return new ApiResponse<>(0, "已调整顺序，发布后生效", null);
    }

    /** ADM-D21 发布修改 */
    @PostMapping("/{id:\\d+}/publish")
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<TemplateVersionVO<ImmuneItemVO>> publish(@PathVariable("id") Long id,
                                                                @Valid @RequestBody TemplatePublishRequest request) {
        TemplateVersionVO<ImmuneItemVO> vo = service.publish(id, request);
        return new ApiResponse<>(0,
                "已发布第 " + vo.getVersionNo() + " 版，之后入栏的批次按新模板生成免疫计划", vo);
    }

    /** ADM-D22 放弃未发布的修改 */
    @PostMapping("/{id:\\d+}/discard")
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<Void> discard(@PathVariable("id") Long id,
                                     @Valid @RequestBody VersionRequest request) {
        service.discard(id, request.getVersion());
        return new ApiResponse<>(0, "已放弃未发布的修改", null);
    }

    /** ADM-D23 版本记录 */
    @GetMapping("/{id:\\d+}/versions")
    public ApiResponse<List<TemplateVersionVO<ImmuneItemVO>>> versions(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.versions(id));
    }

    /** 回退到历史版本（复制为草稿，发布后生效；服务端文档未编号，契约文档记为 ADM-D23A） */
    @PostMapping("/{id:\\d+}/rollback")
    @AdminPermission("WA-D02:HANDLE")
    public ApiResponse<ImmuneTemplateVO> rollback(@PathVariable("id") Long id,
                                                  @Valid @RequestBody TemplateRollbackRequest request) {
        ImmuneTemplateVO vo = service.rollback(id, request);
        return new ApiResponse<>(0, "已回退到第 " + request.getTargetVersionNo() + " 版的内容，发布后生效", vo);
    }
}

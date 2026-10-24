package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.ContentStatusQuery;
import com.zhimu.server.admin.content.request.CategoryOrderRequest;
import com.zhimu.server.admin.content.request.ContentCategorySaveRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.vo.ContentCategoryVO;
import com.zhimu.server.common.api.ApiResponse;
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

import java.util.List;

/** WA-C03 / WA-C04 分类管理：ADM-C40–C43。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class ContentCategoryAdminController {

    private final ContentCategoryAdminService categoryService;

    /** ADM-C40 分类列表 */
    @GetMapping("/content-categories")
    @AdminPermission({"WA-C03:VIEW", "WA-C04:VIEW"})
    public ApiResponse<List<ContentCategoryVO>> list(@Valid ContentStatusQuery query) {
        return ApiResponse.ok(categoryService.list(query.getStatus()));
    }

    /** ADM-C41 新增分类 */
    @PostMapping("/content-categories")
    @Idempotent(required = true)
    @AdminPermission({"WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<ContentCategoryVO> create(@Valid @RequestBody ContentCategorySaveRequest request) {
        return new ApiResponse<>(0, "已保存分类", categoryService.create(request));
    }

    /** ADM-C42 改名、停用 / 启用 */
    @PutMapping("/content-categories/{id:\\d+}")
    @AdminPermission({"WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<ContentCategoryVO> update(@PathVariable("id") Long id,
                                                 @Valid @RequestBody ContentCategorySaveRequest request) {
        return new ApiResponse<>(0, "已保存分类", categoryService.update(id, request));
    }

    /** ADM-C43 分类排序 */
    @PutMapping("/content-categories/order")
    @AdminPermission({"WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<List<ContentCategoryVO>> reorder(@Valid @RequestBody CategoryOrderRequest request) {
        return new ApiResponse<>(0, "已保存分类顺序", categoryService.reorder(request));
    }
}

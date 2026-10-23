package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.query.DictItemQuery;
import com.zhimu.server.admin.base.request.DictItemSaveRequest;
import com.zhimu.server.admin.base.request.DictStatusRequest;
import com.zhimu.server.admin.base.request.ItemOrderRequest;
import com.zhimu.server.admin.base.service.DictAdminService;
import com.zhimu.server.admin.base.vo.DictItemVO;
import com.zhimu.server.admin.base.vo.DictTypeVO;
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

/** 管理端-03 WA-D05：ADM-D49–D54 其他字典。 */
@RestController
@RequestMapping("/admin/v1/dicts")
@RequiredArgsConstructor
@AdminPermission("WA-D05:VIEW")
public class DictAdminController {

    private final DictAdminService dictService;

    /** ADM-D49 字典导航与项数（本批 8 类；财务分类待模块10） */
    @GetMapping
    public ApiResponse<List<DictTypeVO>> types() {
        return ApiResponse.ok(dictService.types());
    }

    /** ADM-D50 字典项列表（含停用；症状、栏舍类型可按品类/畜种分段） */
    @GetMapping("/{dict_type}/items")
    public ApiResponse<List<DictItemVO>> items(@PathVariable("dict_type") String dictType,
                                               @Valid DictItemQuery query) {
        return ApiResponse.ok(dictService.items(dictType, query));
    }

    /** ADM-D51 新增字典项 */
    @PostMapping("/{dict_type}/items")
    @Idempotent(required = true)
    @AdminPermission("WA-D05:HANDLE")
    public ApiResponse<DictItemVO> create(@PathVariable("dict_type") String dictType,
                                          @Valid @RequestBody DictItemSaveRequest request) {
        return new ApiResponse<>(0, "已保存", dictService.create(dictType, request));
    }

    /** ADM-D52 编辑字典项 */
    @PutMapping("/{dict_type}/items/{id:\\d+}")
    @AdminPermission("WA-D05:HANDLE")
    public ApiResponse<DictItemVO> update(@PathVariable("dict_type") String dictType,
                                          @PathVariable("id") Long id,
                                          @Valid @RequestBody DictItemSaveRequest request) {
        return new ApiResponse<>(0, "已保存", dictService.update(dictType, id, request));
    }

    /** ADM-D53 拖动排序（保存即生效） */
    @PutMapping("/{dict_type}/items/order")
    @AdminPermission("WA-D05:HANDLE")
    public ApiResponse<Void> order(@PathVariable("dict_type") String dictType,
                                   @Valid @RequestBody ItemOrderRequest request) {
        dictService.order(dictType, request);
        return new ApiResponse<>(0, "已调整顺序", null);
    }

    /** ADM-D54 停用 / 启用字典项 */
    @PostMapping("/{dict_type}/items/{id:\\d+}/status")
    @AdminPermission("WA-D05:HANDLE")
    public ApiResponse<DictItemVO> changeStatus(@PathVariable("dict_type") String dictType,
                                                @PathVariable("id") Long id,
                                                @Valid @RequestBody DictStatusRequest request) {
        DictItemVO vo = dictService.changeStatus(dictType, id, request);
        String action = "DISABLED".equals(vo.getStatus()) ? "已停用“" : "已启用“";
        return new ApiResponse<>(0, action + vo.getName() + "”", vo);
    }
}

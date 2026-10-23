package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.query.CategoryTreeQuery;
import com.zhimu.server.admin.base.request.BreedSaveRequest;
import com.zhimu.server.admin.base.request.CategorySaveRequest;
import com.zhimu.server.admin.base.request.FeedStdDraftRequest;
import com.zhimu.server.admin.base.request.StdDraftRequest;
import com.zhimu.server.admin.base.request.StdPublishRequest;
import com.zhimu.server.admin.base.request.StdRollbackRequest;
import com.zhimu.server.admin.base.request.StdWeightDraftRequest;
import com.zhimu.server.admin.base.service.BreedAdminService;
import com.zhimu.server.admin.base.service.CategoryAdminService;
import com.zhimu.server.admin.base.service.StdAdminService;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.vo.BreedDetailVO;
import com.zhimu.server.admin.base.vo.BreedVO;
import com.zhimu.server.admin.base.vo.CategoryNodeVO;
import com.zhimu.server.admin.base.vo.CategoryVO;
import com.zhimu.server.admin.base.vo.StdImpactVO;
import com.zhimu.server.admin.base.vo.StdVersionVO;
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

/** 管理端-03 WA-D01：ADM-D01–D13 品类品种与标准体重/饲喂标准。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
@AdminPermission("WA-D01:VIEW")
public class CategoryAdminController {

    private final CategoryQueryService categoryQuery;
    private final BreedQueryService breedQuery;
    private final CategoryAdminService categoryService;
    private final BreedAdminService breedService;
    private final StdAdminService stdService;

    /** ADM-D01 品类品种树（含已停用） */
    @GetMapping("/categories/tree")
    public ApiResponse<List<CategoryNodeVO>> tree(@Valid CategoryTreeQuery query) {
        return ApiResponse.ok(categoryQuery.tree(query.getKeyword()));
    }

    /** ADM-D02 新增品类 */
    @PostMapping("/categories")
    @Idempotent(required = true)
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<CategoryVO> createCategory(@Valid @RequestBody CategorySaveRequest request) {
        CategoryVO vo = categoryService.create(request);
        return new ApiResponse<>(0, "已保存“" + vo.getName() + "”", vo);
    }

    /** ADM-D03 编辑 / 停用 / 启用品类 */
    @PutMapping("/categories/{id:\\d+}")
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<CategoryVO> updateCategory(@PathVariable("id") Long id,
                                                  @Valid @RequestBody CategorySaveRequest request) {
        CategoryVO vo = categoryService.update(id, request);
        return new ApiResponse<>(0, "已保存“" + vo.getName() + "”", vo);
    }

    /** ADM-D04 品种详情 */
    @GetMapping("/breeds/{id:\\d+}")
    public ApiResponse<BreedDetailVO> breedDetail(@PathVariable("id") Long id) {
        BreedDetailVO vo = breedQuery.detail(id);
        if (vo == null) {
            throw BaseErrors.notFound();
        }
        return ApiResponse.ok(vo);
    }

    /** ADM-D05 新增品种 */
    @PostMapping("/breeds")
    @Idempotent(required = true)
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<BreedVO> createBreed(@Valid @RequestBody BreedSaveRequest request) {
        BreedVO vo = breedService.create(request);
        return new ApiResponse<>(0, "已保存" + vo.getName(), vo);
    }

    /** ADM-D06 编辑 / 停用 / 启用品种 */
    @PutMapping("/breeds/{id:\\d+}")
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<BreedVO> updateBreed(@PathVariable("id") Long id,
                                            @Valid @RequestBody BreedSaveRequest request) {
        BreedVO vo = breedService.update(id, request);
        return new ApiResponse<>(0, "已保存" + vo.getName(), vo);
    }

    /** ADM-D07 线上、待生效版本与草稿 */
    @GetMapping("/breeds/{id:\\d+}/standards/{std_type}")
    public ApiResponse<Object> standard(@PathVariable("id") Long id,
                                        @PathVariable("std_type") String stdType) {
        requireStdType(stdType);
        return ApiResponse.ok("feed".equalsIgnoreCase(stdType) ? stdService.feed(id) : stdService.weight(id));
    }

    /**
     * ADM-D08 保存标准草稿。weight 与 feed 共用路径，请求体字段按 std_type 取：
     * weight 用 tolerance_rate / suggested_weigh_times / effective_mode / points[]；
     * feed 用 deviation_rate / stages[]。
     */
    @PutMapping("/breeds/{id:\\d+}/standards/{std_type}/draft")
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<Object> saveDraft(@PathVariable("id") Long id,
                                         @PathVariable("std_type") String stdType,
                                         @RequestBody StdDraftRequest request) {
        requireStdType(stdType);
        if ("feed".equalsIgnoreCase(stdType)) {
            FeedStdDraftRequest feed = new FeedStdDraftRequest();
            feed.setDeviationRate(request.getDeviationRate());
            feed.setStages(request.getStages() == null ? List.of() : request.getStages());
            feed.setVersion(request.getVersion());
            return ApiResponse.ok(stdService.saveFeedDraft(id, feed));
        }
        StdWeightDraftRequest weight = new StdWeightDraftRequest();
        weight.setToleranceRate(request.getToleranceRate());
        weight.setSuggestedWeighTimes(request.getSuggestedWeighTimes());
        weight.setEffectiveMode(request.getEffectiveMode());
        weight.setEffectiveDate(request.getEffectiveDate());
        weight.setPoints(request.getPoints() == null ? List.of() : request.getPoints());
        weight.setVersion(request.getVersion());
        return ApiResponse.ok(stdService.saveWeightDraft(id, weight));
    }

    /** ADM-D09 放弃草稿 */
    @DeleteMapping("/breeds/{id:\\d+}/standards/{std_type}/draft")
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<Void> discardDraft(@PathVariable("id") Long id,
                                          @PathVariable("std_type") String stdType,
                                          @RequestParam(value = "version", required = false) Integer version) {
        stdService.discard(id, stdType, version);
        return ApiResponse.ok();
    }

    /** ADM-D10 发布影响预估 */
    @GetMapping("/breeds/{id:\\d+}/standards/{std_type}/impact")
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<StdImpactVO> impact(@PathVariable("id") Long id,
                                           @PathVariable("std_type") String stdType) {
        return ApiResponse.ok(stdService.impact(id, stdType));
    }

    /** ADM-D11 发布新版本 */
    @PostMapping("/breeds/{id:\\d+}/standards/{std_type}/publish")
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<StdVersionVO> publish(@PathVariable("id") Long id,
                                             @PathVariable("std_type") String stdType,
                                             @Valid @RequestBody StdPublishRequest request) {
        StdAdminService.PublishResult result = stdService.publish(id, stdType, request);
        return new ApiResponse<>(0, result.message(), result.version());
    }

    /** ADM-D12 版本记录 */
    @GetMapping("/breeds/{id:\\d+}/standards/{std_type}/versions")
    public ApiResponse<List<StdVersionVO>> versions(@PathVariable("id") Long id,
                                                    @PathVariable("std_type") String stdType) {
        return ApiResponse.ok(stdService.versions(id, stdType));
    }

    /** ADM-D13 回退版本 */
    @PostMapping("/breeds/{id:\\d+}/standards/{std_type}/rollback")
    @AdminPermission("WA-D01:HANDLE")
    public ApiResponse<StdVersionVO> rollback(@PathVariable("id") Long id,
                                              @PathVariable("std_type") String stdType,
                                              @Valid @RequestBody StdRollbackRequest request) {
        StdAdminService.PublishResult result = stdService.rollback(id, stdType, request);
        return new ApiResponse<>(0, result.message(), result.version());
    }

    private static void requireStdType(String stdType) {
        if (!"weight".equalsIgnoreCase(stdType) && !"feed".equalsIgnoreCase(stdType)) {
            throw BaseErrors.notFound();
        }
    }
}

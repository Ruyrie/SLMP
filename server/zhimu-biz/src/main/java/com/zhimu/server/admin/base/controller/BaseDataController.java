package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.query.StdQuery;
import com.zhimu.server.admin.base.vo.BaseCategoryVO;
import com.zhimu.server.admin.base.vo.BaseDictItemVO;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import com.zhimu.server.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公共基础数据读取：API-U09、U10、U12。不挂 @RequireFarm（待审核养殖场注册时也要读），
 * 返回字段对所有角色相同；U11/U13 由 3B 在本控制器追加。
 */
@RestController
@RequestMapping("/api/v1/base")
@RequiredArgsConstructor
public class BaseDataController {

    private final CategoryQueryService categoryQueryService;
    private final DictQueryService dictQueryService;
    private final StdQueryService stdQueryService;

    /** API-U09 启用的品类与品种（数组；含 has_std_weight / has_feed_std / has_immune_template） */
    @GetMapping("/categories")
    public ApiResponse<List<BaseCategoryVO>> categories() {
        return ApiResponse.ok(categoryQueryService.enabledTree());
    }

    /** API-U10 字典项（只含启用项）；species 或 category_id 可选，PEN_TYPE/VACCINATE_METHOD 可按 X-Farm-Id 推断畜种 */
    @GetMapping("/dicts/{dict_type}")
    public ApiResponse<List<BaseDictItemVO>> dicts(
            @PathVariable("dict_type") String dictType,
            @RequestParam(value = "species", required = false) String species,
            @RequestParam(value = "category_id", required = false) Long categoryId,
            @RequestHeader(value = "X-Farm-Id", required = false) String farmIdHeader) {
        return ApiResponse.ok(dictQueryService.listForApp(dictType, species, categoryId, parseId(farmIdHeader)));
    }

    /** API-U12 当前生效的标准体重与饲喂标准；传 day_age 时附 at_day（本批 App 主代码未调用，无联调） */
    @GetMapping("/breeds/{id}/standards")
    public ApiResponse<BreedStandardsVO> breedStandards(@PathVariable("id") Long breedId,
                                                        @Valid StdQuery query) {
        return ApiResponse.ok(stdQueryService.standards(breedId, query.getDayAge()));
    }

    private static Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

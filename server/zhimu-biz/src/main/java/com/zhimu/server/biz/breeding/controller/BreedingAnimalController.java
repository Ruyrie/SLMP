package com.zhimu.server.biz.breeding.controller;

import com.zhimu.server.biz.breeding.query.BreedingAnimalQuery;
import com.zhimu.server.biz.breeding.query.BreedingOptionQuery;
import com.zhimu.server.biz.breeding.request.AnimalExitRequest;
import com.zhimu.server.biz.breeding.request.BreedingAnimalRequest;
import com.zhimu.server.biz.breeding.service.BreedingAnimalService;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalDetailVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalItemVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalOptionVO;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.RegisterContextVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-B02、B04–B09、B11：种畜档案与登记表单带入。 */
@RestController
@RequestMapping("/api/v1")
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
@RequiredArgsConstructor
public class BreedingAnimalController {

    private final BreedingAnimalService animalService;

    /** API-B02 种畜列表（status 为 ANIMAL_FILTER） */
    @GetMapping("/breeding-animals")
    public ApiResponse<PageVO<BreedingAnimalItemVO>> list(@Valid BreedingAnimalQuery query) {
        return ApiResponse.ok(animalService.list(query));
    }

    /** API-B04 登记下拉候选（purpose 为 MATING / FARROWING / WEANING） */
    @GetMapping("/breeding-animals/options")
    public ApiResponse<PageVO<BreedingAnimalOptionVO>> options(@Valid BreedingOptionQuery query) {
        return ApiResponse.ok(animalService.options(query));
    }

    /** API-B05 种畜档案 */
    @GetMapping("/breeding-animals/{id:\\d+}")
    public ApiResponse<BreedingAnimalDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(animalService.detail(id));
    }

    /** API-B06 繁育记录 */
    @GetMapping("/breeding-animals/{id:\\d+}/records")
    public ApiResponse<PageVO<BreedingRecordVO>> records(@PathVariable("id") Long id,
                                                         @RequestParam(value = "page", defaultValue = "1") int page,
                                                         @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(animalService.records(id, page, size));
    }

    /** API-B11 登记表单带入（type 为 REGISTER_TYPE） */
    @GetMapping("/breeding-animals/{id:\\d+}/register-context")
    public ApiResponse<RegisterContextVO> registerContext(@PathVariable("id") Long id,
                                                          @RequestParam("type") String type) {
        return ApiResponse.ok(animalService.registerContext(id, type));
    }

    /** API-B07 新增母猪 */
    @PostMapping("/breeding-animals")
    @Idempotent(required = true)
    public ApiResponse<BreedingAnimalDetailVO> create(@Valid @RequestBody BreedingAnimalRequest request,
                                                      @RequestHeader(value = "Idempotency-Key", required = false)
                                                      String idempotencyKey) {
        return ApiResponse.ok(animalService.create(request, idempotencyKey));
    }

    /** API-B08 编辑档案 */
    @PutMapping("/breeding-animals/{id:\\d+}")
    public ApiResponse<BreedingAnimalDetailVO> update(@PathVariable("id") Long id,
                                                      @Valid @RequestBody BreedingAnimalRequest request) {
        return ApiResponse.ok(animalService.update(id, request));
    }

    /** API-B09 登记死亡 / 淘汰 */
    @PostMapping("/breeding-animals/{id:\\d+}/exit")
    @Idempotent(required = true)
    public ApiResponse<BreedingAnimalDetailVO> exit(@PathVariable("id") Long id,
                                                    @Valid @RequestBody AnimalExitRequest request,
                                                    @RequestHeader(value = "Idempotency-Key", required = false)
                                                    String idempotencyKey) {
        return ApiResponse.ok(animalService.exit(id, request, idempotencyKey));
    }
}

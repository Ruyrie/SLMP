package com.zhimu.server.biz.breeding.service;

import com.zhimu.server.biz.breeding.query.BreedingAnimalQuery;
import com.zhimu.server.biz.breeding.query.BreedingOptionQuery;
import com.zhimu.server.biz.breeding.request.AnimalExitRequest;
import com.zhimu.server.biz.breeding.request.BreedingAnimalRequest;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalDetailVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalItemVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalOptionVO;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.RegisterContextVO;
import com.zhimu.server.common.api.PageVO;

/** 种畜档案、繁育记录查询与登记表单带入（API-B02、B04–B09、B11）。 */
public interface BreedingAnimalService {

    PageVO<BreedingAnimalItemVO> list(BreedingAnimalQuery query);

    PageVO<BreedingAnimalOptionVO> options(BreedingOptionQuery query);

    BreedingAnimalDetailVO detail(Long animalId);

    PageVO<BreedingRecordVO> records(Long animalId, int page, int size);

    RegisterContextVO registerContext(Long animalId, String type);

    BreedingAnimalDetailVO create(BreedingAnimalRequest request, String idempotencyKey);

    BreedingAnimalDetailVO update(Long animalId, BreedingAnimalRequest request);

    BreedingAnimalDetailVO exit(Long animalId, AnimalExitRequest request, String idempotencyKey);
}

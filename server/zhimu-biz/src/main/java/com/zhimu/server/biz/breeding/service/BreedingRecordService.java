package com.zhimu.server.biz.breeding.service;

import com.zhimu.server.biz.breeding.request.FarrowingRequest;
import com.zhimu.server.biz.breeding.request.FarrowingUpdateRequest;
import com.zhimu.server.biz.breeding.request.MatingRequest;
import com.zhimu.server.biz.breeding.request.MatingUpdateRequest;
import com.zhimu.server.biz.breeding.request.PregnancyCheckRequest;
import com.zhimu.server.biz.breeding.request.PregnancyCheckUpdateRequest;
import com.zhimu.server.biz.breeding.request.WeaningRequest;
import com.zhimu.server.biz.breeding.request.WeaningUpdateRequest;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.FarrowingDetailVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;

/** 配种、妊检、产仔、断奶的登记与修改（API-B12–B21）。 */
public interface BreedingRecordService {

    RegisterResultVO createMating(MatingRequest request, String idempotencyKey);

    BreedingRecordVO updateMating(Long recordId, MatingUpdateRequest request);

    RegisterResultVO createCheck(PregnancyCheckRequest request, String idempotencyKey);

    BreedingRecordVO updateCheck(Long recordId, PregnancyCheckUpdateRequest request);

    RegisterResultVO createFarrowing(FarrowingRequest request, String idempotencyKey);

    FarrowingDetailVO farrowing(Long farrowingId);

    BreedingRecordVO updateFarrowing(Long farrowingId, FarrowingUpdateRequest request);

    RegisterResultVO createWeaning(WeaningRequest request, String idempotencyKey);

    BreedingRecordVO updateWeaning(Long recordId, WeaningUpdateRequest request);
}

package com.zhimu.server.biz.breeding.service;

import com.zhimu.server.biz.breeding.request.CandlingRequest;
import com.zhimu.server.biz.breeding.request.HatchRequest;
import com.zhimu.server.biz.breeding.request.IncubationRequest;
import com.zhimu.server.biz.breeding.request.IncubationUpdateRequest;
import com.zhimu.server.biz.breeding.vo.IncubationDetailVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;

/** 孵化：入孵 → 照蛋 → 出雏（API-B26、B28–B31）。 */
public interface IncubationService {

    RegisterResultVO create(IncubationRequest request, String idempotencyKey);

    IncubationDetailVO detail(Long incubationId);

    IncubationDetailVO update(Long incubationId, IncubationUpdateRequest request);

    IncubationDetailVO candling(Long incubationId, CandlingRequest request, String idempotencyKey);

    RegisterResultVO hatch(Long incubationId, HatchRequest request, String idempotencyKey);
}

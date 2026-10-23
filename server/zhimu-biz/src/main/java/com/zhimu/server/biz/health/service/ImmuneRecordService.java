package com.zhimu.server.biz.health.service;

import com.zhimu.server.biz.health.request.ImmuneRecordCreateRequest;
import com.zhimu.server.biz.health.request.ImmuneRecordUpdateRequest;
import com.zhimu.server.biz.health.vo.ImmuneRecordVO;

/** 接种记录（API-D14–D16）。 */
public interface ImmuneRecordService {

    ImmuneRecordVO create(ImmuneRecordCreateRequest request);

    ImmuneRecordVO detail(Long recordId);

    ImmuneRecordVO update(Long recordId, ImmuneRecordUpdateRequest request);
}

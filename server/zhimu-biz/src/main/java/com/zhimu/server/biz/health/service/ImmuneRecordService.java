package com.zhimu.server.biz.health.service;

import com.zhimu.server.biz.health.request.ImmuneRecordCreateRequest;
import com.zhimu.server.biz.health.request.ImmuneRecordUpdateRequest;
import com.zhimu.server.biz.health.request.NotifyManagerRequest;
import com.zhimu.server.biz.health.vo.ImmuneRecordVO;
import com.zhimu.server.biz.health.vo.NotifyResultVO;

/** 接种记录（API-D14–D16）与员工通知场长。 */
public interface ImmuneRecordService {

    ImmuneRecordVO create(ImmuneRecordCreateRequest request);

    ImmuneRecordVO detail(Long recordId);

    ImmuneRecordVO update(Long recordId, ImmuneRecordUpdateRequest request);

    /** 员工通知场长处理本人登记的免疫记录（请求 / 响应与 API-D27 一致） */
    NotifyResultVO notifyManager(Long recordId, NotifyManagerRequest request);
}

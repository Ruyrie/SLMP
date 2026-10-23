package com.zhimu.server.biz.health.service;

import com.zhimu.server.biz.health.query.MedicationDrugOptionQuery;
import com.zhimu.server.biz.health.query.MedicationTargetQuery;
import com.zhimu.server.biz.health.request.MedicationCreateRequest;
import com.zhimu.server.biz.health.request.MedicationPreviewRequest;
import com.zhimu.server.biz.health.request.MedicationUpdateRequest;
import com.zhimu.server.biz.health.request.NotifyManagerRequest;
import com.zhimu.server.biz.health.vo.DeleteImpactVO;
import com.zhimu.server.biz.health.vo.MedicationCreateResultVO;
import com.zhimu.server.biz.health.vo.MedicationDrugOptionsVO;
import com.zhimu.server.biz.health.vo.MedicationRecordVO;
import com.zhimu.server.biz.health.vo.MedicationTargetVO;
import com.zhimu.server.biz.health.vo.NotifyResultVO;
import com.zhimu.server.biz.health.vo.ReleaseDatePreviewVO;
import com.zhimu.server.biz.health.vo.ToastVO;
import com.zhimu.server.biz.health.vo.WithdrawalVO;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 用药与休药（API-D17–D27）。 */
public interface MedicationService {

    WithdrawalVO withdrawal(Long batchId);

    PageVO<MedicationRecordVO> records(Long batchId, PageQuery query);

    List<MedicationTargetVO> targets(MedicationTargetQuery query);

    MedicationDrugOptionsVO drugOptions(MedicationDrugOptionQuery query);

    ReleaseDatePreviewVO preview(MedicationPreviewRequest request);

    MedicationCreateResultVO create(MedicationCreateRequest request);

    MedicationRecordVO detail(Long recordId);

    MedicationRecordVO update(Long recordId, MedicationUpdateRequest request);

    DeleteImpactVO deleteImpact(Long recordId);

    ToastVO delete(Long recordId, Integer version);

    NotifyResultVO notifyManager(Long recordId, NotifyManagerRequest request);
}

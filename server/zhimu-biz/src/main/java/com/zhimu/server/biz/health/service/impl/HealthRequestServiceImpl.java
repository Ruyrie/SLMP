package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.health.entity.DisinfectRecord;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.DisinfectRecordMapper;
import com.zhimu.server.biz.health.mapper.ImmuneRecordMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.health.service.HealthRequestService;
import com.zhimu.server.biz.health.vo.HealthRequestStatusVO;
import com.zhimu.server.common.context.FarmContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** API-D41 按请求编号查询登记结果：只查本人当前养殖场提交的记录。 */
@Service
@RequiredArgsConstructor
public class HealthRequestServiceImpl implements HealthRequestService {

    private final ImmuneRecordMapper immuneRecordMapper;
    private final MedicationRecordMapper medicationRecordMapper;
    private final DisinfectRecordMapper disinfectRecordMapper;

    @Override
    public HealthRequestStatusVO findByRequest(String clientRequestId, String recordType) {
        HealthRequestStatusVO vo = new HealthRequestStatusVO();
        vo.setStatus("NOT_FOUND");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return vo;
        }
        Long farmId = FarmContext.requireFarmId();
        String type = recordType == null ? null : recordType.toUpperCase();
        if (type == null || "IMMUNE_RECORD".equals(type)) {
            ImmuneRecord record = immuneRecordMapper.selectByClientRequestId(farmId, clientRequestId);
            if (record != null) {
                vo.setStatus("DONE");
                vo.setBizType("IMMUNE_RECORD");
                vo.setBizIds(List.of(record.getId()));
                return vo;
            }
        }
        if (type == null || "MEDICATION_RECORD".equals(type)) {
            List<MedicationRecord> records = medicationRecordMapper.selectByClientRequest(farmId, clientRequestId);
            if (!records.isEmpty()) {
                vo.setStatus("DONE");
                vo.setBizType("MEDICATION_RECORD");
                vo.setGroupNo(records.get(0).getGroupNo());
                vo.setBizIds(records.stream().map(MedicationRecord::getId).toList());
                return vo;
            }
        }
        if (type == null || "DISINFECT_RECORD".equals(type)) {
            DisinfectRecord record = disinfectRecordMapper.selectByClientRequestId(farmId, clientRequestId);
            if (record != null) {
                vo.setStatus("DONE");
                vo.setBizType("DISINFECT_RECORD");
                vo.setBizIds(List.of(record.getId()));
            }
        }
        return vo;
    }
}

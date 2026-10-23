package com.zhimu.server.biz.breeding.convert;

import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.entity.MatingRecord;
import com.zhimu.server.biz.breeding.entity.PregnancyCheck;
import com.zhimu.server.biz.breeding.entity.WeaningRecord;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 繁育记录与登记结果的 VO 转换。 */
@Component
public class BreedingRecordConvert {

    public BreedingRecordVO mating(MatingRecord record, UserBriefVO registrant) {
        BreedingRecordVO vo = base(BreedingCodes.RECORD_MATING, record.getId(),
                BeijingTime.format(record.getMatingDate()), registrant, record.getVersion());
        vo.setMatingMethod(record.getMatingMethod());
        vo.setMatingMethodText(BreedingCodes.matingMethodText(record.getMatingMethod()));
        vo.setBoarInfo(record.getBoarInfo());
        return vo;
    }

    public BreedingRecordVO check(PregnancyCheck record, UserBriefVO registrant) {
        BreedingRecordVO vo = base(BreedingCodes.RECORD_PREG_CHECK, record.getId(),
                BeijingTime.format(record.getCheckDate()), registrant, record.getVersion());
        vo.setCheckResult(record.getResult());
        vo.setCheckResultText(BreedingCodes.pregResultText(record.getResult()));
        vo.setCheckMethod(record.getCheckMethod());
        vo.setCheckMethodText(BreedingCodes.checkMethodText(record.getCheckMethod()));
        return vo;
    }

    public BreedingRecordVO farrowing(FarrowingRecord record, String batchNo, UserBriefVO registrant) {
        BreedingRecordVO vo = base(BreedingCodes.RECORD_FARROWING, record.getId(),
                BeijingTime.format(record.getFarrowDate()), registrant, record.getVersion());
        vo.setParityNo(record.getParityNo());
        vo.setTotalBorn(record.getTotalBorn());
        vo.setLiveBorn(record.getLiveBorn());
        vo.setStillborn(record.getStillborn());
        vo.setBatchId(record.getBatchId());
        vo.setBatchNo(batchNo);
        boolean transferred = record.getBatchId() != null;
        vo.setCanRequestChange(transferred);
        vo.setChangeState(new ChangeRequestStateVO(!transferred, null, null));
        return vo;
    }

    public BreedingRecordVO weaning(WeaningRecord record, UserBriefVO registrant) {
        BreedingRecordVO vo = base(BreedingCodes.RECORD_WEANING, record.getId(),
                BeijingTime.format(record.getWeanDate()), registrant, record.getVersion());
        vo.setWeanedCount(record.getWeanedCount());
        vo.setLitterWeight(record.getLitterWeight());
        return vo;
    }

    public BreedingRecordVO exit(BreedingAnimal animal, UserBriefVO registrant) {
        BreedingRecordVO vo = base(BreedingCodes.RECORD_EXIT, animal.getId(),
                BeijingTime.format(animal.getExitDate()), registrant, animal.getVersion());
        vo.setExitType(animal.getExitType());
        vo.setExitTypeText(BreedingCodes.exitTypeText(animal.getExitType()));
        vo.setExitReason(animal.getExitReason());
        vo.setCreatedAt(BeijingTime.format(animal.getExitAt()));
        vo.setChangeState(new ChangeRequestStateVO(false, null, null));
        vo.setCanRequestChange(true);
        return vo;
    }

    private static BreedingRecordVO base(String recordType, Long recordId, String recordDate,
                                         UserBriefVO registrant, Integer version) {
        BreedingRecordVO vo = new BreedingRecordVO();
        vo.setRecordType(recordType);
        vo.setRecordTypeText(BreedingCodes.recordText(recordType));
        vo.setRecordId(recordId == null ? 0 : recordId);
        vo.setRecordDate(recordDate);
        vo.setRegistrant(registrant);
        vo.setVersion(version == null ? 0 : version);
        vo.setCanRequestChange(false);
        vo.setHasChangeLog(false);
        vo.setChangeState(new ChangeRequestStateVO(true, null, null));
        return vo;
    }

    public RegisterResultVO registerResult(String recordType, Long recordId, BreedingAnimal animal, Long cycleId,
                                           Boolean isFirst, LocalDate expectedDueDate, Long batchId, String batchNo,
                                           Boolean todoCompleted) {
        RegisterResultVO vo = new RegisterResultVO();
        vo.setRecordType(recordType);
        vo.setRecordId(recordId == null ? 0 : recordId);
        if (animal != null) {
            vo.setAnimalId(animal.getId());
            vo.setEarTag(animal.getEarTag());
            vo.setAnimalStatus(animal.getStatus());
            vo.setAnimalStatusText(BreedingCodes.statusText(animal.getStatus()));
        }
        vo.setCycleId(cycleId);
        vo.setIsFirst(isFirst);
        vo.setExpectedDueDate(BeijingTime.format(expectedDueDate));
        vo.setBatchId(batchId);
        vo.setBatchNo(batchNo);
        vo.setTodoCompleted(todoCompleted);
        return vo;
    }
}

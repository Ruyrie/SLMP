package com.zhimu.server.biz.inspect.support;

import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.InspectionSetting;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.inspect.mapper.InspectReferenceMapper;
import com.zhimu.server.biz.inspect.mapper.InspectTemplateMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionSettingMapper;
import com.zhimu.server.biz.inspect.query.BatchPenStockRow;
import com.zhimu.server.biz.inspect.query.FarmGeoRow;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.inspect.query.InspectTemplateHeadRow;
import com.zhimu.server.biz.inspect.query.PenResponsibleRow;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.MediaItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 巡检与上报共用的只读查询、权限与模板加载。 */
@Component
@RequiredArgsConstructor
public class InspectSupport {

    private final InspectReferenceMapper referenceMapper;
    private final InspectTemplateMapper templateMapper;
    private final InspectionSettingMapper settingMapper;
    private final AbnormalReportMapper reportMapper;
    private final PenQueryService penQueryService;
    private final BatchQueryService batchQueryService;
    private final BizMediaService bizMediaService;

    public FarmContext.FarmMember member() {
        return FarmContext.require();
    }

    public Long farmId() {
        return member().farmId();
    }

    public String farmName() {
        return member().farmName();
    }

    public Long userId() {
        return member().userId();
    }

    public boolean manager() {
        return member().isManagerView();
    }

    public void requireManager(String message) {
        if (!manager()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, message);
        }
    }

    public List<FarmMemberRow> memberList() {
        List<FarmMemberRow> rows = referenceMapper.selectMembers(farmId());
        return rows == null ? List.of() : rows;
    }

    public Map<Long, FarmMemberRow> memberMap() {
        Map<Long, FarmMemberRow> map = new LinkedHashMap<>();
        for (FarmMemberRow row : memberList()) {
            map.put(row.getUserId(), row);
        }
        return map;
    }

    public List<PenResponsibleRow> penResponsibles() {
        List<PenResponsibleRow> rows = referenceMapper.selectPenResponsibles(farmId());
        return rows == null ? List.of() : rows;
    }

    public List<PenInfo> pens() {
        List<PenInfo> pens = penQueryService.listPens(farmId(), false);
        if (pens == null) {
            return List.of();
        }
        List<PenInfo> visible = new ArrayList<>();
        for (PenInfo pen : pens) {
            if (member().canAccessPen(pen.penId())) {
                visible.add(pen);
            }
        }
        return visible;
    }

    public PenInfo requirePen(Long penId) {
        if (penId == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Map<Long, PenInfo> found = penQueryService.getPens(List.of(penId));
        PenInfo pen = found == null ? null : found.get(penId);
        if (pen == null || !farmId().equals(pen.farmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        FarmContext.checkPen(penId);
        return pen;
    }

    public String penName(Long penId) {
        if (penId == null) {
            return null;
        }
        Map<Long, PenInfo> found = penQueryService.getPens(List.of(penId));
        PenInfo pen = found == null ? null : found.get(penId);
        return pen == null ? referenceMapper.selectPenName(penId) : pen.penName();
    }

    public List<BatchInfo> activeBatches() {
        List<BatchInfo> batches = batchQueryService.listActive(farmId());
        return batches == null ? List.of() : batches;
    }

    public List<BatchInfo> batchesOfPen(Long penId) {
        List<BatchInfo> result = new ArrayList<>();
        for (BatchInfo batch : activeBatches()) {
            if (batch.penIds() != null && batch.penIds().contains(penId)) {
                result.add(batch);
            }
        }
        return result;
    }

    public BatchInfo requireActiveBatch(Long batchId) {
        BatchInfo batch = batchQueryService.requireBatch(batchId);
        if (batch.farmId() == null || !batch.farmId().equals(farmId()) || !batch.active()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return batch;
    }

    public List<BatchPenStockRow> activeBatchPens() {
        List<BatchPenStockRow> rows = referenceMapper.selectActiveBatchPens(farmId());
        return rows == null ? List.of() : rows;
    }

    public int batchPenStock(Long batchId, Long penId) {
        BatchPenStockRow row = referenceMapper.selectBatchPenStock(batchId, penId);
        return row == null || row.getStockCount() == null ? 0 : row.getStockCount();
    }

    public FarmGeoRow farmGeo() {
        return referenceMapper.selectFarm(farmId());
    }

    public InspectTemplateBundle templateForCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个栏舍还没有可用的巡检模板");
        }
        InspectTemplateHeadRow head = templateMapper.selectPublishedByCategory(categoryId);
        return InspectTemplateBundle.build(head, templateMapper.selectItems(head == null ? null : head.getVersionId()));
    }

    public InspectTemplateBundle templateForVersion(Long templateId, Integer version) {
        InspectTemplateHeadRow head = templateMapper.selectVersion(templateId, version);
        return InspectTemplateBundle.build(head, templateMapper.selectItems(head == null ? null : head.getVersionId()));
    }

    public InspectionSetting setting() {
        InspectionSetting setting = settingMapper.selectByFarmId(farmId());
        if (setting == null) {
            setting = new InspectionSetting();
            setting.setFarmId(farmId());
            setting.setDeadlineTime(InspectRules.DEFAULT_DEADLINE);
            setting.setRemindBeforeMin(60);
        }
        return setting;
    }

    public LocalTime deadline() {
        LocalTime time = setting().getDeadlineTime();
        return time == null ? InspectRules.DEFAULT_DEADLINE : time;
    }

    public List<MediaItemVO> media(BizType type, Long bizId) {
        List<MediaItemVO> media = bizMediaService.list(type, bizId);
        return media == null ? List.of() : media;
    }

    public String reportStatus(Long reportId) {
        if (reportId == null) {
            return null;
        }
        AbnormalReport report = reportMapper.selectById(reportId);
        return report == null ? null : report.getStatus();
    }

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        FarmMemberRow member = memberMap().get(userId);
        return member == null ? referenceMapper.selectUserName(userId) : member.getName();
    }

    public List<String> namesOfPens(Collection<Long> penIds) {
        List<String> names = new ArrayList<>();
        for (PenResponsibleRow row : penResponsibles()) {
            if (penIds.contains(row.getPenId()) && row.getName() != null && !names.contains(row.getName())) {
                names.add(row.getName());
            }
        }
        return names;
    }
}

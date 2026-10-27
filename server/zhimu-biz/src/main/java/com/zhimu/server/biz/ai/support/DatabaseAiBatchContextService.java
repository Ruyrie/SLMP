package com.zhimu.server.biz.ai.support;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.biz.feeding.api.BatchGrowthQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 从业务服务读取真实批次，列表、会话关联与提问使用同一套数据和权限规则。 */
@Service
@RequiredArgsConstructor
public class DatabaseAiBatchContextService implements AiBatchContextService {

    private static final JsonMapper JSON = ZhimuJackson.newPlainMapper();

    private final BatchQueryService batchQueryService;
    private final PenQueryService penQueryService;
    private final BaseDataQueryService baseDataQueryService;
    private final BreedQueryService breedQueryService;
    private final BatchGrowthQueryService growthQueryService;

    @Override
    public List<LinkedBatchVO> listLinkable(Long farmId, boolean staff, Iterable<Long> penIds) {
        if (farmId == null) {
            return List.of();
        }
        Set<Long> allowed = staff ? toSet(penIds) : null;
        List<BatchInfo> batches = batchQueryService.listActive(farmId).stream()
                .filter(batch -> linkable(batch, farmId, allowed))
                .sorted(Comparator.comparing(BatchInfo::batchNo,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (batches.isEmpty()) {
            return List.of();
        }
        List<Long> visiblePens = batches.stream().flatMap(batch -> visiblePens(batch, allowed).stream())
                .distinct().toList();
        Map<Long, PenInfo> pens = penQueryService.getPens(visiblePens);
        var weights = growthQueryService.latestWeights(farmId, batches.stream().map(BatchInfo::batchId).toList());
        return batches.stream().map(batch -> toVo(batch, allowed, pens, weights.get(batch.batchId()))).toList();
    }

    @Override
    public LinkedBatchVO resolve(Long farmId, Long batchId, boolean staff, Iterable<Long> penIds) {
        if (farmId == null || batchId == null) {
            return null;
        }
        BatchInfo batch;
        try {
            batch = batchQueryService.requireBatch(batchId);
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.NOT_FOUND.getCode()) {
                return null;
            }
            throw e;
        }
        Set<Long> allowed = staff ? toSet(penIds) : null;
        if (!linkable(batch, farmId, allowed)) {
            return null;
        }
        var pens = penQueryService.getPens(visiblePens(batch, allowed));
        var weight = growthQueryService.latestWeights(farmId, List.of(batchId)).get(batchId);
        return toVo(batch, allowed, pens, weight);
    }

    private boolean linkable(BatchInfo batch, Long farmId, Set<Long> allowed) {
        return batch != null && Objects.equals(farmId, batch.farmId()) && batch.active()
                && batch.currentCount() != null && batch.currentCount() > 0
                && !visiblePens(batch, allowed).isEmpty();
    }

    private List<Long> visiblePens(BatchInfo batch, Set<Long> allowed) {
        return batch.penIds() == null ? List.of() : batch.penIds().stream()
                .filter(Objects::nonNull).filter(id -> allowed == null || allowed.contains(id)).distinct().toList();
    }

    private LinkedBatchVO toVo(BatchInfo batch, Set<Long> allowed, Map<Long, PenInfo> pens,
                               BatchGrowthQueryService.LatestWeight weight) {
        var category = baseDataQueryService.getCategory(batch.categoryId());
        var breed = batch.breedId() == null ? null : breedQueryService.info(batch.breedId());
        List<Long> visible = visiblePens(batch, allowed);
        List<PenInfo> orderedPens = visible.stream().map(pens::get).filter(Objects::nonNull)
                .filter(pen -> Objects.equals(batch.farmId(), pen.farmId()))
                .sorted(Comparator.comparing(PenInfo::penCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        LinkedBatchVO vo = new LinkedBatchVO();
        vo.setBatchId(batch.batchId());
        vo.setBatchNo(batch.batchNo());
        vo.setCategoryName(category == null ? "未知畜种" : category.speciesText());
        vo.setStageName(category == null ? "未知品类" : category.name());
        vo.setBreedName(breed == null ? "未知品种" : breed.name());
        vo.setCountUnitText(category == null ? "" : category.countUnitText());
        vo.setPenId(orderedPens.isEmpty() ? visible.getFirst() : orderedPens.getFirst().penId());
        vo.setPenName(orderedPens.stream().map(PenInfo::penName).filter(Objects::nonNull)
                .collect(Collectors.joining("、")));
        long days = batch.entryDate() == null ? 0 : ChronoUnit.DAYS.between(batch.entryDate(), BeijingTime.today());
        vo.setDayAge((int) Math.max(0, (batch.entryAgeDays() == null ? 0 : batch.entryAgeDays()) + days));
        vo.setCurrentCount(batch.currentCount());
        if (weight != null) {
            vo.setLatestAvgWeight(weight.avgWeight());
            vo.setLatestWeighDate(weight.weighDate());
        }
        return vo;
    }

    private static Set<Long> toSet(Iterable<Long> ids) {
        Set<Long> result = new HashSet<>();
        if (ids != null) {
            ids.forEach(result::add);
        }
        return result;
    }

    @Override
    public LinkedBatchVO fromSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(snapshotJson, LinkedBatchVO.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String toSnapshot(LinkedBatchVO batch) {
        return batch == null ? null : JSON.writeValueAsString(batch);
    }
}

package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 临时批次目录：模块04（栏舍与批次）落地前，给模块11 的关联批次面板与提问背景提供与 App Mock 一致的数据。
 * 数据：东山猪场 1001 的 3001/3002/3003/3004；员工只看到负责栏舍（如李明负责 2003，只看到 3001）。
 * 模块04 落地后删除本类，改由 BatchQueryService 提供。
 */
@Service
public class TemporaryAiBatchContextService implements AiBatchContextService {

    private static final JsonMapper JSON = ZhimuJackson.newPlainMapper();

    private static final List<LinkedBatchVO> BATCHES = List.of(
            batch(3001L, "ZM2610-031", "生猪", "育肥猪", "杜长大", 2003L, "3 号育肥舍", 96, 200),
            batch(3003L, "ZM2610-045", "生猪", "保育猪", "杜长大", 2001L, "1 号保育舍", 35, 320),
            batch(3004L, "ZM2609-038", "生猪", "保育猪", "杜长大", 2002L, "2 号保育舍", 63, 200),
            batch(3002L, "ZM2610-019", "生猪", "育肥猪", "杜长大", 2004L, "4 号育肥舍", 82, 150));

    @Override
    public List<LinkedBatchVO> listLinkable(Long farmId, boolean staff, Iterable<Long> penIds) {
        if (!Objects.equals(farmId, 1001L)) {
            return new ArrayList<>();
        }
        Set<Long> allowed = staff ? toSet(penIds) : null;
        List<LinkedBatchVO> result = new ArrayList<>();
        for (LinkedBatchVO batch : BATCHES) {
            if (allowed == null || allowed.contains(batch.getPenId())) {
                result.add(copy(batch));
            }
        }
        return result;
    }

    @Override
    public LinkedBatchVO resolve(Long farmId, Long batchId, boolean staff, Iterable<Long> penIds) {
        if (farmId == null || batchId == null || !Objects.equals(farmId, 1001L)) {
            return null;
        }
        Set<Long> allowed = staff ? toSet(penIds) : null;
        for (LinkedBatchVO batch : BATCHES) {
            if (batch.getBatchId().equals(batchId)) {
                if (allowed != null && !allowed.contains(batch.getPenId())) {
                    return null;
                }
                return copy(batch);
            }
        }
        return null;
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

    private static Set<Long> toSet(Iterable<Long> penIds) {
        Set<Long> result = new java.util.LinkedHashSet<>();
        if (penIds != null) {
            penIds.forEach(result::add);
        }
        return result;
    }

    private static LinkedBatchVO copy(LinkedBatchVO source) {
        LinkedBatchVO vo = new LinkedBatchVO();
        vo.setBatchId(source.getBatchId());
        vo.setBatchNo(source.getBatchNo());
        vo.setCategoryName(source.getCategoryName());
        vo.setStageName(source.getStageName());
        vo.setBreedName(source.getBreedName());
        vo.setPenId(source.getPenId());
        vo.setPenName(source.getPenName());
        vo.setDayAge(source.getDayAge());
        vo.setCurrentCount(source.getCurrentCount());
        vo.setCountUnitText(source.getCountUnitText());
        return vo;
    }

    private static LinkedBatchVO batch(Long id, String no, String category, String stage, String breed, Long penId,
                                       String penName, int dayAge, int currentCount) {
        LinkedBatchVO vo = new LinkedBatchVO();
        vo.setBatchId(id);
        vo.setBatchNo(no);
        vo.setCategoryName(category);
        vo.setStageName(stage);
        vo.setBreedName(breed);
        vo.setPenId(penId);
        vo.setPenName(penName);
        vo.setDayAge(dayAge);
        vo.setCurrentCount(currentCount);
        vo.setCountUnitText("头");
        return vo;
    }
}

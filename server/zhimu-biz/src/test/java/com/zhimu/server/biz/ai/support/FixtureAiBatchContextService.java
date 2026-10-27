package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Static test data for AI session service tests; never registered in the application. */
public class FixtureAiBatchContextService implements AiBatchContextService {

    private static final JsonMapper JSON = ZhimuJackson.newPlainMapper();

    private static final List<LinkedBatchVO> BATCHES = List.of(
            batch(3001L, "ZM2610-031", "生猪", "育肥猪", "杜长大", 2003L, "3 号育肥舍", 96, 200),
            batch(3003L, "ZM2610-045", "生猪", "保育猪", "杜长大", 2001L, "1 号保育舍", 35, 320),
            batch(3004L, "ZM2609-038", "生猪", "保育猪", "杜长大", 2002L, "2 号保育舍", 63, 200),
            batch(3002L, "ZM2610-019", "生猪", "育肥猪", "杜长大", 2004L, "4 号育肥舍", 82, 150));

    /** 西岭鸡场在栏批次；家禽按“只”计数。 */
    private static final List<LinkedBatchVO> POULTRY_BATCHES = List.of(
            poultryBatch(3201L, "ZM2610-026", "肉鸡", "肉鸡", "白羽肉鸡", 2202L, "2 号鸡舍", 18, 9300),
            poultryBatch(3202L, "ZM2610-027", "肉鸡", "肉鸡", "白羽肉鸡", 2201L, "1 号鸡舍", 18, 9300),
            poultryBatch(3211L, "ZM2603-015", "肉鸡", "种鸡", "白羽肉鸡", 2301L, "1 号种鸡舍", 221, 3200),
            poultryBatch(3212L, "ZM2603-016", "肉鸡", "种鸡", "白羽肉鸡", 2302L, "2 号种鸡舍", 213, 2800),
            poultryBatch(3213L, "ZM2603-017", "肉鸡", "种鸡", "白羽肉鸡", 2303L, "种禽舍", 218, 3000));

    @Override
    public List<LinkedBatchVO> listLinkable(Long farmId, boolean staff, Iterable<Long> penIds) {
        List<LinkedBatchVO> source = source(farmId);
        Set<Long> allowed = staff ? toSet(penIds) : null;
        List<LinkedBatchVO> result = new ArrayList<>();
        for (LinkedBatchVO batch : source) {
            if (allowed == null || allowed.contains(batch.getPenId())) {
                result.add(copy(batch));
            }
        }
        return result;
    }

    @Override
    public LinkedBatchVO resolve(Long farmId, Long batchId, boolean staff, Iterable<Long> penIds) {
        if (farmId == null || batchId == null) {
            return null;
        }
        Set<Long> allowed = staff ? toSet(penIds) : null;
        for (LinkedBatchVO batch : source(farmId)) {
            if (batch.getBatchId().equals(batchId)) {
                if (allowed != null && !allowed.contains(batch.getPenId())) {
                    return null;
                }
                return copy(batch);
            }
        }
        return null;
    }

    private static List<LinkedBatchVO> source(Long farmId) {
        if (Objects.equals(farmId, 1001L)) {
            return BATCHES;
        }
        return Objects.equals(farmId, 1002L) ? POULTRY_BATCHES : List.of();
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
        LinkedBatchVO vo = baseBatch(id, no, category, stage, breed, penId, penName, dayAge, currentCount);
        vo.setCountUnitText("头");
        return vo;
    }

    private static LinkedBatchVO poultryBatch(Long id, String no, String category, String stage, String breed,
                                              Long penId, String penName, int dayAge, int currentCount) {
        LinkedBatchVO vo = baseBatch(id, no, category, stage, breed, penId, penName, dayAge, currentCount);
        vo.setCountUnitText("只");
        return vo;
    }

    private static LinkedBatchVO baseBatch(Long id, String no, String category, String stage, String breed,
                                           Long penId, String penName, int dayAge, int currentCount) {
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
        return vo;
    }
}

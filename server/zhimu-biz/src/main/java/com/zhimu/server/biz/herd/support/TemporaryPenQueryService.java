package com.zhimu.server.biz.herd.support;

import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时实现：模块04（第 3 批）实现正式栏舍与存栏前，给模块02 的 F18/F24/注销预览与列表提供示例数据。
 * 数据与 App Mock 对齐（东山猪场 1001 的 2001、2003–2007，存栏 870；西岭鸡场 1002 存栏 18600）。
 * 第 3 批模块04 落地后删除本类。
 */
@Service
public class TemporaryPenQueryService implements PenQueryService {

    private static final List<PenInfo> PENS = List.of(
            pen(2001L, 1001L, "BY-01", "1 号保育舍", "NURSERY", "保育舍", 300),
            pen(2003L, 1001L, "YF-03", "3 号育肥舍", "FATTENING", "育肥舍", 500),
            pen(2004L, 1001L, "YF-04", "4 号育肥舍", "FATTENING", "育肥舍", 500),
            pen(2005L, 1001L, "YF-05", "5 号育肥舍", "FATTENING", "育肥舍", 500),
            pen(2006L, 1001L, "MZ-01", "母猪舍", "SOW", "母猪舍", 200),
            pen(2007L, 1001L, "CF-01", "产房", "FARROWING", "产房", 100),
            pen(3001L, 1002L, "YC-01", "1 号育雏舍", "BROODING", "育雏舍", 5000),
            pen(3002L, 1002L, "RJ-01", "肉鸡舍", "BROILER", "肉鸡舍", 10000),
            pen(3003L, 1002L, "ZJ-01", "种鸡舍", "BREEDER", "种鸡舍", 3000));

    private static final Map<Long, Integer> CURRENT_STOCK = Map.of(1001L, 870, 1002L, 18600);

    @Override
    public List<PenInfo> listPens(Long farmId, boolean includeDisabled) {
        return PENS.stream()
                .filter(pen -> pen.farmId().equals(farmId))
                .filter(pen -> includeDisabled || pen.enabled())
                .toList();
    }

    @Override
    public Map<Long, PenInfo> getPens(Collection<Long> penIds) {
        Map<Long, PenInfo> result = new LinkedHashMap<>();
        if (penIds == null || penIds.isEmpty()) {
            return result;
        }
        for (PenInfo pen : PENS) {
            if (penIds.contains(pen.penId())) {
                result.put(pen.penId(), pen);
            }
        }
        return result;
    }

    @Override
    public Map<Long, Integer> farmCurrentStock(Collection<Long> farmIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (farmIds == null) {
            return result;
        }
        for (Long farmId : farmIds) {
            result.put(farmId, CURRENT_STOCK.getOrDefault(farmId, 0));
        }
        return result;
    }

    private static PenInfo pen(Long penId, Long farmId, String code, String name, String type, String typeText,
                               Integer capacity) {
        return new PenInfo(penId, farmId, code, name, type, typeText, capacity, "ENABLED");
    }
}

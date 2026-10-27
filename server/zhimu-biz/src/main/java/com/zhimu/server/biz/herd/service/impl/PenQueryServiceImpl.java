package com.zhimu.server.biz.herd.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 栏舍与存栏查询（模块04 实现，替换 TemporaryPenQueryService）：
 * 供模块02 的 F18/F24、列表与注销预览使用。跨养殖场查询用 TenantSkip 并显式按 farm_id 过滤。
 */
@Service
@RequiredArgsConstructor
public class PenQueryServiceImpl implements PenQueryService {

    private final PenMapper penMapper;
    private final BatchMapper batchMapper;

    @Override
    public List<PenInfo> listPensForCategory(Long farmId, Long categoryId) {
        if (farmId == null || categoryId == null) return List.of();
        return TenantSkip.call(() -> penMapper.selectList(Wrappers.<Pen>lambdaQuery()
                        .eq(Pen::getFarmId, farmId).eq(Pen::getCategoryId, categoryId)
                        .eq(Pen::getStatus, "ENABLED").orderByAsc(Pen::getPenCode)))
                .stream().map(PenQueryServiceImpl::toInfo).toList();
    }

    @Override
    public List<PenInfo> listPens(Long farmId, boolean includeDisabled) {
        if (farmId == null) {
            return List.of();
        }
        List<Pen> pens = TenantSkip.call(() -> penMapper.selectList(Wrappers.<Pen>lambdaQuery()
                .eq(Pen::getFarmId, farmId)
                .eq(!includeDisabled, Pen::getStatus, "ENABLED")
                .orderByAsc(Pen::getPenCode)));
        return pens.stream().map(PenQueryServiceImpl::toInfo).toList();
    }

    @Override
    public Map<Long, PenInfo> getPens(Collection<Long> penIds) {
        Map<Long, PenInfo> result = new LinkedHashMap<>();
        if (penIds == null || penIds.isEmpty()) {
            return result;
        }
        List<Pen> pens = TenantSkip.call(() -> penMapper.selectList(Wrappers.<Pen>lambdaQuery()
                .in(Pen::getId, penIds)));
        for (Pen pen : pens) {
            result.put(pen.getId(), toInfo(pen));
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
            result.put(farmId, 0);
        }
        if (result.isEmpty()) {
            return result;
        }
        List<Batch> batches = TenantSkip.call(() -> batchMapper.selectList(Wrappers.<Batch>lambdaQuery()
                .in(Batch::getFarmId, farmIds)
                .eq(Batch::getStatus, "ACTIVE")));
        for (Batch batch : batches) {
            result.merge(batch.getFarmId(), batch.getCurrentCount() == null ? 0 : batch.getCurrentCount(), Integer::sum);
        }
        return result;
    }

    private static PenInfo toInfo(Pen pen) {
        return new PenInfo(pen.getId(), pen.getFarmId(), pen.getPenCode(), pen.getPenName(), pen.getPenType(),
                penTypeText(pen.getPenType()), pen.getCapacity(), pen.getStatus());
    }

    private static String penTypeText(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "NURSERY" -> "保育舍";
            case "FATTENING" -> "育肥舍";
            case "SOW" -> "母猪舍";
            case "FARROWING" -> "产房";
            case "BROODING" -> "育雏舍";
            case "BROILER" -> "肉鸡舍";
            case "BREEDER" -> "种鸡舍";
            default -> code;
        };
    }
}

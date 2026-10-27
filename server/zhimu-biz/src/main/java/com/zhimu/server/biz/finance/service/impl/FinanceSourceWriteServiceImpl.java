package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.finance.api.FinanceSourceWriteService;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FinanceSourceWriteServiceImpl implements FinanceSourceWriteService {
    private final FinanceRecordMapper recordMapper;
    private final FinanceCategoryMapper categoryMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long syncReturnRefundIncome(ReturnRefundIncome source) {
        if (source == null || source.farmId() == null || source.outboundId() == null
                || source.amount() == null || source.amount().signum() <= 0 || source.refundDate() == null
                || source.amount().stripTrailingZeros().scale() > 2
                || source.amount().compareTo(new java.math.BigDecimal("9999999999.99")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退款记账来源无效");
        }
        return TenantSkip.call(() -> {
            FinanceRecord record = recordMapper.selectOne(Wrappers.<FinanceRecord>lambdaQuery()
                    .eq(FinanceRecord::getFarmId, source.farmId())
                    .eq(FinanceRecord::getSourceType, FinanceConst.SOURCE_DRUG_RETURN)
                    .eq(FinanceRecord::getSourceId, source.outboundId()));
            String detail = source.detailText();
            if (detail != null && detail.codePointCount(0, detail.length()) > 100) {
                detail = detail.substring(0, detail.offsetByCodePoints(0, 100));
            }
            if (source.financeRecordId() != null) {
                if (record == null || !source.financeRecordId().equals(record.getId())
                        || !FinanceConst.INCOME.equals(record.getRecordType())
                        || !FinanceConst.RECORD_NORMAL.equals(record.getStatus())) {
                    throw new BizException(ErrorCode.STATE_CHANGED, "关联退款收入状态异常，请刷新后重试");
                }
                record.setAmount(FinanceSupport.scale2(source.amount()));
                record.setRecordDate(source.refundDate());
                record.setDetailText(detail);
                record.setUpdatedBy(source.actorId());
                if (recordMapper.updateById(record) == 0) throw new BizException(ErrorCode.VERSION_CONFLICT);
                return record.getId();
            }
            if (record != null) throw new BizException(ErrorCode.STATE_CHANGED, "该退货单已有关联退款收入，请刷新后重试");
            FinanceCategory category = categoryMapper.selectOne(Wrappers.<FinanceCategory>lambdaQuery()
                    .eq(FinanceCategory::getDirection, FinanceConst.INCOME)
                    .eq(FinanceCategory::getAutoSource, FinanceConst.SOURCE_DRUG_RETURN)
                    .eq(FinanceCategory::getStatus, "ENABLED").last("LIMIT 1"));
            if (category == null) throw new BizException(ErrorCode.SERVER_ERROR, "退款自动记账分类不存在或已停用");
            record = new FinanceRecord();
            record.setFarmId(source.farmId());
            record.setRecordType(FinanceConst.INCOME);
            record.setSourceType(FinanceConst.SOURCE_DRUG_RETURN);
            record.setSourceId(source.outboundId());
            record.setSourceNo(source.outboundNo());
            record.setCategoryId(category.getId());
            record.setAmount(FinanceSupport.scale2(source.amount()));
            record.setRecordDate(source.refundDate());
            record.setDetailText(detail);
            record.setVoucherCount(0);
            record.setStatus(FinanceConst.RECORD_NORMAL);
            record.setVersion(0);
            record.setCreatedBy(0L);
            record.setUpdatedBy(source.actorId());
            recordMapper.insert(record);
            return record.getId();
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long syncInboundExpense(InboundExpense source) {
        if (source.farmId() == null || source.sourceId() == null
                || !(FinanceConst.SOURCE_FEED.equals(source.sourceType()) || FinanceConst.SOURCE_DRUG.equals(source.sourceType()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入库记账来源无效");
        }
        // 审核端同样调用此接口；显式限定来源养殖场，不依赖当前端的默认养殖场。
        return TenantSkip.call(() -> synchronize(source));
    }

    private Long synchronize(InboundExpense source) {
        FinanceRecord record = recordMapper.selectOne(Wrappers.<FinanceRecord>lambdaQuery()
                .eq(FinanceRecord::getFarmId, source.farmId())
                .eq(FinanceRecord::getSourceType, source.sourceType())
                .eq(FinanceRecord::getSourceId, source.sourceId()));
        if (!source.active() && record == null) return null;
        if (source.active() && (source.amount() == null || source.amount().signum() <= 0 || source.recordDate() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入库记账金额和日期无效");
        }
        String detail = source.detailText();
        if (detail != null && detail.length() > 100) detail = detail.substring(0, 100);
        if (record == null) {
            FinanceCategory category = categoryMapper.selectOne(Wrappers.<FinanceCategory>lambdaQuery()
                    .eq(FinanceCategory::getDirection, FinanceConst.EXPENSE)
                    .eq(FinanceCategory::getAutoSource, source.sourceType())
                    .eq(FinanceCategory::getStatus, "ENABLED").last("LIMIT 1"));
            if (category == null) throw new BizException(ErrorCode.SERVER_ERROR, "入库自动记账分类不存在或已停用");
            record = new FinanceRecord();
            record.setFarmId(source.farmId());
            record.setRecordType(FinanceConst.EXPENSE);
            record.setSourceType(source.sourceType());
            record.setSourceId(source.sourceId());
            record.setSourceNo(source.sourceNo());
            record.setCategoryId(category.getId());
            record.setAmount(FinanceSupport.scale2(source.amount()));
            record.setRecordDate(source.recordDate());
            record.setDetailText(detail);
            record.setVoucherCount(0);
            record.setStatus(FinanceConst.RECORD_NORMAL);
            record.setVersion(0);
            record.setCreatedBy(0L);
            record.setUpdatedBy(source.actorId());
            recordMapper.insert(record);
            return record.getId();
        }
        String status = source.active() ? FinanceConst.RECORD_NORMAL : FinanceConst.RECORD_VOID;
        boolean unchanged = status.equals(record.getStatus()) && (!source.active()
                || (record.getAmount().compareTo(FinanceSupport.scale2(source.amount())) == 0
                && Objects.equals(record.getRecordDate(), source.recordDate())
                && Objects.equals(record.getSourceNo(), source.sourceNo())
                && Objects.equals(record.getDetailText(), detail)));
        if (unchanged) return record.getId();
        record.setStatus(status);
        record.setUpdatedBy(source.actorId());
        if (source.active()) {
            record.setAmount(FinanceSupport.scale2(source.amount()));
            record.setRecordDate(source.recordDate());
            record.setSourceNo(source.sourceNo());
            record.setDetailText(detail);
        }
        record.setVoidSource(null);
        record.setVoidedAt(null);
        record.setVoidedBy(null);
        // 显式清空重新开启同步的作废字段，避免 NOT_NULL 更新策略保留旧作废状态。
        int updated = recordMapper.update(record, Wrappers.<FinanceRecord>lambdaUpdate()
                .eq(FinanceRecord::getId, record.getId()).eq(FinanceRecord::getFarmId, source.farmId())
                .set(FinanceRecord::getVoidSource, source.active() ? null : "SOURCE_VOID")
                .set(FinanceRecord::getVoidedAt, source.active() ? null : BeijingTime.now())
                .set(FinanceRecord::getVoidedBy, source.active() ? null : source.actorId()));
        if (updated == 0) throw new BizException(ErrorCode.STATE_CHANGED, "入库对应记账已变化，请刷新后重试");
        return record.getId();
    }
}

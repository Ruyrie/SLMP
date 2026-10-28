package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.BatchGrowthQueryService;
import com.zhimu.server.biz.finance.entity.MarketPrice;
import com.zhimu.server.biz.finance.entity.SaleAdvice;
import com.zhimu.server.biz.finance.entity.SaleAdviceRisk;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.SaleAdviceMapper;
import com.zhimu.server.biz.finance.mapper.SaleAdviceRiskMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.service.SaleAdviceService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.SaleAdviceVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGateway;
import com.zhimu.server.integration.ai.AiMessage;
import com.zhimu.server.integration.ai.AiScene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API-E33–E36：当天建议复用、休药期规则生成、AI 生成与历史查询。 */
@Service
@RequiredArgsConstructor
public class SaleAdviceServiceImpl implements SaleAdviceService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int DAILY_REGENERATE_LIMIT = 5;

    private final SaleAdviceMapper adviceMapper;
    private final SaleAdviceRiskMapper riskMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final BatchQueryService batchQueryService;
    private final MarketPriceService marketPriceService;
    private final AiGateway aiGateway;
    private final FinanceSupport support;
    private final BatchGrowthQueryService growthQueryService;

    @Override
    @Transactional
    public SaleAdviceVos.SaleAdviceVO latest(Long batchId) {
        Long farmId = support.farmId();
        BatchInfo batch = support.requireBatch(batchId, farmId);
        if (!batch.active()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该批次已结束，不能获取出栏建议");
        }
        List<SaleAdvice> today = todayAdvices(farmId, batchId);
        for (SaleAdvice advice : today) {
            if (FinanceConst.ADVICE_SUCCESS.equals(advice.getStatus())
                    || FinanceConst.ADVICE_GENERATING.equals(advice.getStatus())) {
                return toVo(advice, batch);
            }
        }
        if (!today.isEmpty()) {
            return toVo(today.get(0), batch);
        }
        return toVo(create(batch), batch);
    }

    @Override
    @Transactional
    public SaleAdviceVos.SaleAdviceVO regenerate(Long batchId) {
        Long farmId = support.farmId();
        BatchInfo batch = support.requireBatch(batchId, farmId);
        if (!batch.active()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该批次已结束，不能获取出栏建议");
        }
        List<SaleAdvice> today = todayAdvices(farmId, batchId);
        for (SaleAdvice advice : today) {
            if (FinanceConst.ADVICE_GENERATING.equals(advice.getStatus())) {
                return toVo(advice, batch);
            }
        }
        if (today.size() >= DAILY_REGENERATE_LIMIT) {
            throw new BizException(ErrorCode.TOO_FREQUENT, "今天重新生成次数已用完，明天再试");
        }
        return toVo(create(batch), batch);
    }

    @Override
    public PageVO<SaleAdviceVos.SaleAdviceVO> history(FinanceQueries.SaleAdviceQuery query) {
        Long farmId = support.farmId();
        List<SaleAdvice> advices = adviceMapper.selectList(Wrappers.<SaleAdvice>lambdaQuery()
                .eq(SaleAdvice::getFarmId, farmId)
                .eq(SaleAdvice::getStatus, FinanceConst.ADVICE_SUCCESS)
                .eq(query.getBatchId() != null, SaleAdvice::getBatchId, query.getBatchId())
                .orderByDesc(SaleAdvice::getGeneratedAt)
                .orderByDesc(SaleAdvice::getId));
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, advices.size());
        int to = Math.min(from + size, advices.size());
        List<SaleAdviceVos.SaleAdviceVO> list = new ArrayList<>();
        for (SaleAdvice advice : advices.subList(from, to)) {
            list.add(historyVo(advice));
        }
        return new PageVO<>(list, advices.size(), page, size);
    }

    @Override
    public SaleAdviceVos.SaleAdviceVO get(Long id) {
        SaleAdvice advice = id == null ? null : adviceMapper.selectById(id);
        if (advice == null || !support.farmId().equals(advice.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toVo(advice, batchOf(advice.getBatchId()));
    }

    // ===== 生成 =====

    private SaleAdvice create(BatchInfo batch) {
        SaleAdvice withdrawal = currentWithdrawal(batch.batchId());
        if (withdrawal != null) {
            return ruleAdvice(batch, withdrawal);
        }
        return aiAdvice(batch);
    }

    private SaleAdvice ruleAdvice(BatchInfo batch, SaleAdvice withdrawal) {
        LocalDate sellable = withdrawal.getSellableDate();
        SaleAdvice advice = baseAdvice(batch, "RULE");
        advice.setStatus(FinanceConst.ADVICE_SUCCESS);
        advice.setConclusion(FinanceConst.CONCL_NOT);
        advice.setSellableDate(sellable);
        advice.setInWithdrawal(true);
        advice.setWithdrawalDaysLeft(Math.max(0, FinanceSupport.daysBetween(BeijingTime.today(), sellable)));
        advice.setWindowStart(sellable);
        advice.setWindowEnd(sellable.plusDays(7));
        advice.setReasonWithdrawal(withdrawal.getReasonWithdrawal());
        advice.setReasonAge(ageReason(batch));
        advice.setReasonWeight(weightReason(batch));
        advice.setReasonPrice(withdrawal.getReasonPrice());
        advice.setBasisInsufficient(Boolean.TRUE.equals(withdrawal.getBasisInsufficient()));
        advice.setBasisNote(withdrawal.getBasisNote());
        advice.setAiModel("rule");
        advice.setPromptVersion("v1");
        advice.setGeneratedAt(BeijingTime.now());
        adviceMapper.insert(advice);
        saveRisks(advice.getId(), ruleRisks(batch, sellable));
        return advice;
    }

    private SaleAdvice aiAdvice(BatchInfo batch) {
        SaleAdvice advice = baseAdvice(batch, "AI");
        advice.setStatus(FinanceConst.ADVICE_GENERATING);
        advice.setInWithdrawal(false);
        advice.setStartedAt(BeijingTime.now());
        adviceMapper.insert(advice);
        if (!aiGateway.isEnabled()) {
            fail(advice, "出栏建议暂时无法生成，请稍后重新生成。");
            return advice;
        }
        try {
            String prompt = buildPrompt(batch);
            AiChatResult result = aiGateway.chat(AiChatRequest.builder()
                    .scene(AiScene.SALE_ADVICE)
                    .message(AiMessage.system("你是养猪经营顾问。只输出 JSON，字段：conclusion（SELL_SOON/KEEP_FEEDING/NOT_SELLABLE）、"
                            + "window_start、window_end（yyyy-MM-dd）、risks（0–3 条，每条不超过 60 字）。"))
                    .message(AiMessage.user(prompt))
                    .jsonMode(true)
                    .maxTokens(800)
                    .build());
            JsonNode node = JSON.readTree(result.getContent());
            String conclusion = node.path("conclusion").asText("");
            if (!List.of(FinanceConst.CONCL_SELL, FinanceConst.CONCL_KEEP).contains(conclusion)) {
                conclusion = FinanceConst.CONCL_KEEP;
            }
            advice.setConclusion(conclusion);
            LocalDate today = BeijingTime.today();
            LocalDate start = parseDate(node.path("window_start").asText(null), today.plusDays(7));
            LocalDate end = parseDate(node.path("window_end").asText(null), start.plusDays(6));
            if (start.isBefore(today)) {
                start = today;
            }
            if (end.isBefore(start) || end.isAfter(start.plusDays(30))) {
                end = start.plusDays(6);
            }
            advice.setWindowStart(start);
            advice.setWindowEnd(end);
            advice.setReasonWithdrawal(null);
            advice.setReasonAge(ageReason(batch));
            advice.setReasonWeight(weightReason(batch));
            advice.setReasonPrice(priceReason(batch));
            advice.setAiModel(result.getModel());
            advice.setPromptVersion("v1");
            advice.setStatus(FinanceConst.ADVICE_SUCCESS);
            advice.setGeneratedAt(BeijingTime.now());
            adviceMapper.updateById(advice);
            List<String> risks = new ArrayList<>();
            JsonNode riskNode = node.path("risks");
            if (riskNode.isArray()) {
                for (JsonNode item : riskNode) {
                    String text = item.asText("");
                    if (!text.isBlank() && risks.size() < 3) {
                        risks.add(text.length() > 60 ? text.substring(0, 60) : text);
                    }
                }
            }
            saveRisks(advice.getId(), risks);
        } catch (Exception e) {
            fail(advice, "出栏建议暂时无法生成，请稍后重新生成。");
        }
        return advice;
    }

    private void fail(SaleAdvice advice, String message) {
        advice.setStatus(FinanceConst.ADVICE_FAILED);
        advice.setErrorMessage(message);
        advice.setGeneratedAt(BeijingTime.now());
        adviceMapper.updateById(advice);
    }

    private SaleAdvice baseAdvice(BatchInfo batch, String mode) {
        SaleAdvice advice = new SaleAdvice();
        advice.setFarmId(batch.farmId());
        advice.setBatchId(batch.batchId());
        advice.setGenMode(mode);
        advice.setInWithdrawal(false);
        advice.setBasisInsufficient(false);
        advice.setInputSnapshotJson(snapshot(batch));
        advice.setCreatedBy(UserContext.requireUserId());
        return advice;
    }

    private String buildPrompt(BatchInfo batch) {
        MarketPrice price = marketPriceService.latest(batch.farmId(), batch.categoryId(),
                FinanceConst.MARKET_SLAUGHTER, BeijingTime.today());
        return "批次：" + batch.batchNo() + "；品类：" + support.categoryName(batch.categoryId())
                + "；入栏日期：" + FinanceSupport.format(batch.entryDate())
                + "；日龄：" + ((batch.entryAgeDays() == null ? 0 : batch.entryAgeDays())
                + FinanceSupport.daysBetween(batch.entryDate(), BeijingTime.today()))
                + "；当前存栏：" + batch.currentCount() + "；累计死淘：" + batch.deathCount()
                + "；已出栏：" + batch.soldCount()
                + "；体重：" + weightReason(batch)
                + (price == null ? "；近期无行情价" : "；今日出栏价：" + price.getPrice() + " 元/千克")
                + "。请给出是否近期出栏的建议。";
    }

    private String snapshot(BatchInfo batch) {
        MarketPrice price = marketPriceService.latest(batch.farmId(), batch.categoryId(),
                FinanceConst.MARKET_SLAUGHTER, BeijingTime.today());
        return FinanceSupport.json(Map.of(
                "batch_id", batch.batchId(),
                "batch_no", batch.batchNo(),
                "category_id", batch.categoryId(),
                "entry_date", FinanceSupport.format(batch.entryDate()),
                "current_count", batch.currentCount() == null ? 0 : batch.currentCount(),
                "death_count", batch.deathCount() == null ? 0 : batch.deathCount(),
                "sold_count", batch.soldCount() == null ? 0 : batch.soldCount(),
                "price", price == null ? "" : price.getPrice(),
                "price_date", price == null ? "" : FinanceSupport.format(price.getPriceDate())));
    }

    private String ageReason(BatchInfo batch) {
        int dayAge = (batch.entryAgeDays() == null ? 0 : batch.entryAgeDays())
                + FinanceSupport.daysBetween(batch.entryDate(), BeijingTime.today());
        return dayAge + " 日龄，存栏 " + (batch.currentCount() == null ? 0 : batch.currentCount()) + " 头。";
    }

    /** 体重依据：最近一次称重均重与同日龄品种标准对比；没有称重时给当前日龄的标准体重（不超过 100 字） */
    private String weightReason(BatchInfo batch) {
        BatchGrowthQueryService.WeighSnapshot weigh;
        try {
            weigh = growthQueryService.latestWeighing(batch.farmId(), batch.batchId(), batch.breedId(), dayAge(batch));
        } catch (RuntimeException e) {
            weigh = null;
        }
        if (weigh == null || weigh.avgWeight() == null) {
            if (weigh != null && weigh.currentStdWeight() != null) {
                return "暂无称重记录；当前 " + dayAge(batch) + " 日龄品种标准体重约 " + kg(weigh.currentStdWeight())
                        + " 千克。";
            }
            return "暂无称重记录，建议出栏前抽样称重。";
        }
        StringBuilder text = new StringBuilder("最近一次称重（" + weigh.weighDate().getMonthValue() + "月"
                + weigh.weighDate().getDayOfMonth() + "日）均重 " + kg(weigh.avgWeight()) + " 千克");
        if (weigh.stdWeight() != null && weigh.deviationRatio() != null) {
            BigDecimal percent = weigh.deviationRatio().abs().multiply(BigDecimal.valueOf(100))
                    .setScale(0, java.math.RoundingMode.HALF_UP);
            int sign = weigh.deviationRatio().signum();
            text.append("，").append(sign == 0 || percent.signum() == 0 ? "与同日龄标准 " + kg(weigh.stdWeight())
                    + " 千克持平" : (sign < 0 ? "低于" : "高于") + "同日龄标准 " + kg(weigh.stdWeight()) + " 千克约 "
                    + percent.toPlainString() + "%");
        } else {
            text.append("，该品种暂无标准体重");
        }
        text.append("。");
        if (weigh.currentStdWeight() != null && weigh.dayAge() != null && weigh.dayAge() < dayAge(batch)) {
            text.append("当前日龄标准约 ").append(kg(weigh.currentStdWeight())).append(" 千克。");
        }
        String value = text.toString();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private static int dayAge(BatchInfo batch) {
        return (batch.entryAgeDays() == null ? 0 : batch.entryAgeDays())
                + FinanceSupport.daysBetween(batch.entryDate(), BeijingTime.today());
    }

    private static String kg(BigDecimal value) {
        return value.setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String priceReason(BatchInfo batch) {
        MarketPrice latest = marketPriceService.latest(batch.farmId(), batch.categoryId(),
                FinanceConst.MARKET_SLAUGHTER, BeijingTime.today());
        if (latest == null) {
            return "近 7 天没有行情价。";
        }
        return "最新出栏价 " + latest.getPrice() + " 元/千克（" + FinanceSupport.format(latest.getPriceDate()) + "）。";
    }

    private List<String> ruleRisks(BatchInfo batch, LocalDate sellable) {
        List<String> risks = new ArrayList<>();
        MarketPrice latest = marketPriceService.latest(batch.farmId(), batch.categoryId(),
                FinanceConst.MARKET_SLAUGHTER, BeijingTime.today());
        if (latest != null && latest.getPrice() != null) {
            BigDecimal today = latest.getPrice();
            if (today.compareTo(new BigDecimal("1")) > 0) {
                risks.add("休药期满后注意观察行情变化。");
            }
        }
        risks.add("休药期满后价格可能回落，建议分批出栏。");
        if (risks.size() > 3) {
            risks = risks.subList(0, 3);
        }
        return risks;
    }

    private void saveRisks(Long adviceId, List<String> risks) {
        if (risks == null) {
            return;
        }
        int sort = 1;
        for (String risk : risks) {
            if (risk == null || risk.isBlank() || sort > 3) {
                continue;
            }
            SaleAdviceRisk entity = new SaleAdviceRisk();
            entity.setAdviceId(adviceId);
            entity.setSortNo(sort++);
            entity.setContent(risk.length() > 60 ? risk.substring(0, 60) : risk);
            entity.setCreatedAt(BeijingTime.now());
            riskMapper.insert(entity);
        }
    }

    // ===== 查询与转换 =====

    private List<SaleAdvice> todayAdvices(Long farmId, Long batchId) {
        LocalDate today = BeijingTime.today();
        return adviceMapper.selectList(Wrappers.<SaleAdvice>lambdaQuery()
                .eq(SaleAdvice::getFarmId, farmId)
                .eq(SaleAdvice::getBatchId, batchId)
                .ge(SaleAdvice::getCreatedAt, today.atStartOfDay())
                .orderByDesc(SaleAdvice::getCreatedAt)
                .orderByDesc(SaleAdvice::getId));
    }

    private SaleAdvice currentWithdrawal(Long batchId) {
        LocalDate today = BeijingTime.today();
        return adviceMapper.selectList(Wrappers.<SaleAdvice>lambdaQuery()
                        .eq(SaleAdvice::getBatchId, batchId)
                        .eq(SaleAdvice::getInWithdrawal, true)
                        .ge(SaleAdvice::getSellableDate, today)
                        .orderByDesc(SaleAdvice::getGeneratedAt)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private SaleAdviceVos.SaleAdviceVO toVo(SaleAdvice advice, BatchInfo batch) {
        SaleAdviceVos.SaleAdviceVO vo = new SaleAdviceVos.SaleAdviceVO();
        vo.setId(advice.getId());
        vo.setBatchId(advice.getBatchId());
        vo.setBatchNo(batch == null ? null : batch.batchNo());
        vo.setPenName(batch == null ? null : penName(batch));
        vo.setStatus(advice.getStatus());
        vo.setConclusion(advice.getConclusion());
        vo.setConclusionText(FinanceConst.conclusionText(advice.getConclusion()));
        vo.setInWithdrawal(Boolean.TRUE.equals(advice.getInWithdrawal()));
        vo.setBasisInsufficient(Boolean.TRUE.equals(advice.getBasisInsufficient()));
        vo.setGeneratedAt(FinanceSupport.format(advice.getGeneratedAt()));
        vo.setWindowStart(FinanceSupport.format(advice.getWindowStart()));
        vo.setWindowEnd(FinanceSupport.format(advice.getWindowEnd()));
        vo.setSellableDate(FinanceSupport.format(advice.getSellableDate()));
        vo.setWithdrawalDaysLeft(advice.getWithdrawalDaysLeft());
        SaleAdviceVos.AdviceReasons reasons = new SaleAdviceVos.AdviceReasons();
        reasons.setWithdrawal(advice.getReasonWithdrawal());
        reasons.setAge(advice.getReasonAge());
        reasons.setWeight(advice.getReasonWeight());
        reasons.setPrice(advice.getReasonPrice());
        vo.setReasons(reasons);
        vo.setBasisNote(advice.getBasisNote());
        vo.setBatchEndDate(batchEndDate(batch));
        vo.setSourceLabel(advice.getGenMode());
        List<SaleAdviceRisk> risks = riskMapper.selectList(Wrappers.<SaleAdviceRisk>lambdaQuery()
                .eq(SaleAdviceRisk::getAdviceId, advice.getId())
                .orderByAsc(SaleAdviceRisk::getSortNo));
        risks.forEach(risk -> vo.getRisks().add(risk.getContent()));
        return vo;
    }

    /** 历史建议与 App advice_history.json 一致：返回与详情相同的字段（栏舍、窗口期、理由、风险等） */
    private SaleAdviceVos.SaleAdviceVO historyVo(SaleAdvice advice) {
        return toVo(advice, batchOf(advice.getBatchId()));
    }

    /** 批次已出栏结束时返回最后一张有效出栏单的出栏日期（App“已于 X 出栏”）；在养批次为 null */
    private String batchEndDate(BatchInfo batch) {
        if (batch == null || batch.active()) {
            return null;
        }
        SaleOrder last = saleOrderMapper.selectOne(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getBatchId, batch.batchId())
                .ne(SaleOrder::getPaymentStatus, "VOIDED")
                .orderByDesc(SaleOrder::getSaleDate)
                .last("LIMIT 1"));
        return last == null ? null : FinanceSupport.format(last.getSaleDate());
    }

    private BatchInfo batchOf(Long batchId) {
        try {
            return batchQueryService.requireBatch(batchId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String penName(BatchInfo batch) {
        if (batch.penIds() == null || batch.penIds().isEmpty()) {
            return null;
        }
        return support.penName(batch.penIds().get(0));
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}

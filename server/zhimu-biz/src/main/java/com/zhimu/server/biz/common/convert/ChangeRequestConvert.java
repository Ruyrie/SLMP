package com.zhimu.server.biz.common.convert;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.vo.ChangeRequestVO;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeStatus;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 修改申请实体 → ChangeRequestVO（U02–U04、管理端共用）。
 * 字段名按 App lib_common ChangeRequestVO：farm_id / farm_name / field_code / recalc_summary / biz_id / batch_id / after_value。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeRequestConvert {

    private final ObjectProvider<AccountQueryService> accountQueryService;
    private final ObjectProvider<FarmAccountService> farmAccountService;

    public ChangeRequestVO toVo(ChangeRequest request, String farmName, UserBriefVO submittedBy) {
        if (request == null) {
            return null;
        }
        return ChangeRequestVO.builder()
                .id(request.getId())
                .requestNo(request.getRequestNo())
                .bizType(request.getBizType())
                .bizTypeText(bizTypeText(request.getBizType()))
                .bizId(request.getBizId())
                .farmId(request.getFarmId())
                .farmName(farmName)
                .batchId(request.getBatchId())
                .recordLocator(request.getRecordLocator())
                .changeType(request.getChangeType())
                .fieldCode(request.getFieldCode())
                .fieldName(request.getFieldName())
                .valueUnit(request.getValueUnit())
                .beforeDisplay(request.getBeforeDisplay())
                .afterDisplay(request.getAfterDisplay())
                .afterValue(ChangeRequestJson.afterValue(request.getAfterJson(), request.getFieldCode()))
                .reason(request.getReason())
                .impacts(impacts(request))
                .recalcTags(ChangeRequestJson.splitTags(request.getRecalcTags()))
                .status(request.getStatus())
                .statusText(statusText(request.getStatus()))
                .submittedBy(submittedBy)
                .submittedAt(request.getSubmittedAt())
                .reviewedAt(request.getReviewedAt())
                .reviewComment(request.getReviewComment())
                .rejectReason(request.getRejectReason())
                .recalcSummary(request.getRecalcSummary())
                .build();
    }

    public UserBriefVO brief(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountQueryService service = accountQueryService.getIfAvailable();
        if (service == null) {
            return UserBriefVO.builder().userId(userId).build();
        }
        try {
            AccountBrief brief = service.getBrief(userId);
            return brief == null ? UserBriefVO.builder().userId(userId).build()
                    : UserBriefVO.builder().userId(userId).name(brief.name()).build();
        } catch (RuntimeException e) {
            log.debug("查询提交人姓名失败 user_id={}", userId, e);
            return UserBriefVO.builder().userId(userId).build();
        }
    }

    /** 批量取提交人（列表每页最多 100 条，通常只有本人） */
    public Map<Long, UserBriefVO> briefs(Collection<Long> userIds) {
        Map<Long, UserBriefVO> result = new LinkedHashMap<>();
        Set<Long> ids = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long id : userIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        AccountQueryService service = accountQueryService.getIfAvailable();
        if (service == null) {
            ids.forEach(id -> result.put(id, UserBriefVO.builder().userId(id).build()));
            return result;
        }
        try {
            Map<Long, AccountBrief> briefs = service.listBriefs(ids);
            if (briefs != null) {
                briefs.forEach((id, brief) -> result.put(id, UserBriefVO.builder()
                        .userId(id)
                        .name(brief == null ? null : brief.name())
                        .build()));
            }
        } catch (RuntimeException e) {
            log.debug("批量查询账号姓名失败 {}", ids, e);
        }
        for (Long id : ids) {
            result.putIfAbsent(id, UserBriefVO.builder().userId(id).build());
        }
        return result;
    }

    /** 场名用模块02 的 FarmAccountService.findMyFarmBrief 补（不直接读 t_farm） */
    public Map<Long, String> farmNames(Collection<Long> farmIds, Long userId) {
        Map<Long, String> result = new LinkedHashMap<>();
        Set<Long> ids = new LinkedHashSet<>();
        if (farmIds != null) {
            for (Long id : farmIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty() || userId == null) {
            return result;
        }
        FarmAccountService service = farmAccountService.getIfAvailable();
        if (service == null) {
            return result;
        }
        for (Long farmId : ids) {
            try {
                FarmBriefVO brief = service.findMyFarmBrief(userId, farmId);
                if (brief != null && brief.getName() != null) {
                    result.put(farmId, brief.getName());
                }
            } catch (RuntimeException e) {
                log.debug("补充养殖场名称失败 farm_id={}", farmId, e);
            }
        }
        return result;
    }

    public static String bizTypeText(String code) {
        com.zhimu.server.common.enums.BizType type = CodeEnum.of(com.zhimu.server.common.enums.BizType.class, code);
        return type == null ? null : ChangeBizTypes.textOf(type);
    }

    public static String statusText(String status) {
        ChangeStatus value = CodeEnum.of(ChangeStatus.class, status);
        return value == null ? null : value.getText();
    }

    public static List<String> impacts(ChangeRequest request) {
        List<String> impacts = new ArrayList<>();
        if (flag(request.getImpactStock())) {
            impacts.add(ChangeImpact.STOCK.getText());
        }
        if (flag(request.getImpactInventory())) {
            impacts.add(ChangeImpact.INVENTORY.getText());
        }
        if (flag(request.getImpactCost())) {
            impacts.add(ChangeImpact.COST.getText());
        }
        if (flag(request.getImpactSellableDate())) {
            impacts.add(ChangeImpact.SELLABLE_DATE.getText());
        }
        if (flag(request.getImpactIncome())) {
            impacts.add(ChangeImpact.INCOME.getText());
        }
        if (flag(request.getImpactStats())) {
            impacts.add(ChangeImpact.STATS.getText());
        }
        return impacts;
    }

    private static boolean flag(Integer value) {
        return value != null && value == 1;
    }
}

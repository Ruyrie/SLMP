package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.query.FarmListRow;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** FarmQueryService：跨养殖场的养殖场简要信息 */
@Service
@RequiredArgsConstructor
public class FarmQueryServiceImpl implements FarmQueryService {

    private final FarmMapper farmMapper;
    private final BaseDataQueryService baseData;
    private final FarmAssembler assembler;

    @Override
    public List<FarmBriefVO> listFarmsOfUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        List<FarmBriefVO> result = new ArrayList<>();
        for (FarmListRow row : rows) {
            Farm farm = new Farm();
            farm.setId(row.getFarmId());
            farm.setAuditStatus(row.getAuditStatus());
            farm.setDisabled(row.getDisabled());
            farm.setFrozen(row.getFrozen());
            FarmStatus status = FarmStatuses.of(farm);
            CategoryInfo category = row.getMainCategoryId() == null ? null
                    : baseData.getCategory(row.getMainCategoryId());
            result.add(FarmBriefVO.builder()
                    .farmId(row.getFarmId())
                    .name(row.getName())
                    .speciesText(category == null ? null : category.speciesText())
                    .status(status.name())
                    .statusText(status.getText())
                    .role(row.getRole())
                    .build());
        }
        return result;
    }
}

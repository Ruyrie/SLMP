package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.vo.BannedDrugHitVO;
import com.zhimu.server.integration.ai.BannedDrugGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 禁用药防线扩展点实现（04 号 9.1 节）：模块07/11 在 AI 提问前与回答后调用。
 * 匹配口径与 U11/模块08 一致：t_banned_drug 的名称、别名（顿号/逗号分隔）与文本互相包含。
 * 全仓库只能有一个 BannedDrugGuard Bean。
 */
@Service
@RequiredArgsConstructor
public class BannedDrugGuardImpl implements BannedDrugGuard {

    private final DrugCatalogQueryService drugCatalogQueryService;

    @Override
    public List<Hit> check(String text) {
        List<Hit> hits = new ArrayList<>();
        for (BannedDrugHitVO hit : drugCatalogQueryService.checkBannedDrugs(text)) {
            hits.add(new Hit(hit.getBannedDrugId(), hit.getName(), hit.getName()));
        }
        return hits;
    }
}

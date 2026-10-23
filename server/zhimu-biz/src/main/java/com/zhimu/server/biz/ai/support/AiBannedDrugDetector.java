package com.zhimu.server.biz.ai.support;

import com.zhimu.server.integration.ai.BannedDrugGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 禁用药预检：优先用平台 {@link BannedDrugGuard}（管理端-03 落地后注册），
 * 未注册时用内置常见禁用药物名称兜底，保证提问与回答两条防线都能命中。
 */
@Component
public class AiBannedDrugDetector {

    private static final List<String> BUILT_IN = List.of("克伦特罗", "瘦肉精", "沙丁胺醇", "莱克多巴胺",
            "氯霉素", "呋喃唑酮", "呋喃西林", "己烯雌酚", "孔雀石绿", "硝基呋喃");

    private final ObjectProvider<BannedDrugGuard> guard;

    public AiBannedDrugDetector(ObjectProvider<BannedDrugGuard> guard) {
        this.guard = guard;
    }

    /** @return 命中的药物名称；未命中返回 null */
    public String match(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        BannedDrugGuard platformGuard = guard.getIfAvailable();
        if (platformGuard != null) {
            try {
                List<BannedDrugGuard.Hit> hits = platformGuard.check(text);
                if (hits != null && !hits.isEmpty()) {
                    BannedDrugGuard.Hit hit = hits.get(0);
                    return hit.drugName() == null ? hit.matchedWord() : hit.drugName();
                }
            } catch (RuntimeException ignored) {
                // 平台比对失败时继续用内置名单
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String name : BUILT_IN) {
            if (lower.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
        return null;
    }
}

package com.zhimu.server.integration.ai;

import java.util.List;

/**
 * 禁用药防线扩展点（04 号 9.1 节）：由业务模块按 t_banned_drug 的名称与别名实现，模块07、11 在提问前与回答后调用。
 * AI 网关本身不调用。
 */
public interface BannedDrugGuard {

    /**
     * 检查一段文本。
     *
     * @return 命中项，未命中返回空列表
     */
    List<Hit> check(String text);

    /**
     * @param drugId      禁用药 ID
     * @param drugName    禁用药名称
     * @param matchedWord 文本中命中的词（名称或别名）
     */
    record Hit(Long drugId, String drugName, String matchedWord) {
    }
}

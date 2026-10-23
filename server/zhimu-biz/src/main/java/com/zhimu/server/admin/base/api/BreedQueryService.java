package com.zhimu.server.admin.base.api;

import com.zhimu.server.admin.base.vo.BreedDetailVO;

import java.util.List;

/** 品种查询（管理端 D04–D06；3B 的药品/模板与模块04 使用）。 */
public interface BreedQueryService {

    /** 品种详情；不存在返回 null */
    BreedDetailVO detail(Long breedId);

    /** 启用品种的简要信息；不存在返回 null */
    BreedInfo info(Long breedId);

    /** 某品类下的启用品种，按 sort_no */
    List<BreedInfo> listEnabled(Long categoryId);

    /** 品种简要信息（category_id 用于推断畜种，offspring_breed_id 用于产仔预填） */
    record BreedInfo(Long id, Long categoryId, String name, Integer gestationDays, Integer incubationDays,
                     Integer pregnancyCheckDays, Long offspringBreedId, boolean enabled) {
    }
}

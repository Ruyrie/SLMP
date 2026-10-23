package com.zhimu.server.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.base.entity.BannedDrugHit;

/** 禁用药命中记录（t_banned_drug_hit）只追加，不提供 update/delete 封装。 */
public interface BannedDrugHitMapper extends BaseMapper<BannedDrugHit> {
}

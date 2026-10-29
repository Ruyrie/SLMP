package com.zhimu.server.biz.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 免疫计划项目 Mapper。 */
public interface ImmunePlanMapper extends BaseMapper<ImmunePlan> {

    /**
     * 计划日期不晚于 lastPlanDate（今天 − 7 天）仍未登记的待接种 / 已延期项目标为漏免。
     * 不改乐观锁版本号：状态由系统按日期推导，不应让用户手里的页面因此提示“已被他人修改”。
     */
    @Update("UPDATE t_immune_plan SET status = 'MISSED', missed_at = #{now}, updated_at = #{now} "
            + "WHERE farm_id = #{farmId} AND status IN ('PENDING','POSTPONED') AND pre_entry = 0 "
            + "AND record_id IS NULL AND plan_date <= #{lastPlanDate}")
    int markMissed(@Param("farmId") Long farmId, @Param("lastPlanDate") LocalDate lastPlanDate,
                   @Param("now") LocalDateTime now);
}

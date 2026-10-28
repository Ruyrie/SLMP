package com.zhimu.server.biz.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** SaleOrder 单表访问。 */
public interface SaleOrderMapper extends BaseMapper<SaleOrder> {

    /** 收款提醒接收人：本场在职所有者与场长（只读 t_farm_member） */
    @Select("SELECT user_id FROM t_farm_member WHERE farm_id = #{farmId} AND active_flag = 1 "
            + "AND role IN ('OWNER', 'MANAGER') ORDER BY FIELD(role, 'OWNER', 'MANAGER'), id")
    List<Long> selectManagerUserIds(@Param("farmId") Long farmId);
}

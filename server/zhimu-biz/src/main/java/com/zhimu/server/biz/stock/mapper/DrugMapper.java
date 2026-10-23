package com.zhimu.server.biz.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.stock.entity.Drug;
import org.apache.ibatis.annotations.Update;

/** 药品档案 Mapper。 */
public interface DrugMapper extends BaseMapper<Drug> {

    @Update("""
            UPDATE t_drug
               SET is_unit_locked = 1, version = version + 1, updated_at = NOW()
             WHERE id = #{id}
            """)
    int markUnitLocked(Long id);
}

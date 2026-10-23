package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.InspectionSetting;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 巡检设置 Mapper。 */
public interface InspectionSettingMapper extends BaseMapper<InspectionSetting> {

    @Select("SELECT * FROM t_inspection_setting WHERE farm_id = #{farmId} LIMIT 1")
    InspectionSetting selectByFarmId(@Param("farmId") Long farmId);
}

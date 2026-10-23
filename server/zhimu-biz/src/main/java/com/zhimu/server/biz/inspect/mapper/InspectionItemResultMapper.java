package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 巡检项结果 Mapper：修改时整组物理替换。 */
public interface InspectionItemResultMapper extends BaseMapper<InspectionItemResult> {

    @Select("SELECT * FROM t_inspection_item_result WHERE inspection_id = #{inspectionId} ORDER BY sort_no, id")
    List<InspectionItemResult> selectByInspectionId(@Param("inspectionId") Long inspectionId);

    @Delete("DELETE FROM t_inspection_item_result WHERE inspection_id = #{inspectionId}")
    int deleteByInspectionId(@Param("inspectionId") Long inspectionId);
}

package com.zhimu.server.biz.farm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.query.FarmListRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 养殖场 Mapper：单表用通用方法，F01 跨养殖场列表用 XML。 */
public interface FarmMapper extends BaseMapper<Farm> {

    /** 本人在场、未删除的养殖场（含成员行信息），按 F01 需要的字段一次查出 */
    List<FarmListRow> selectRowsOfUser(@Param("userId") Long userId);

    /**
     * 按 ID 取养殖场（含已删除）：用于区分 40401 与 41001“养殖场已被删除”。
     * 自定义 SQL 不经过逻辑删除插件，调用方自行判断 deleted。
     */
    @Select("SELECT * FROM t_farm WHERE id = #{id} LIMIT 1")
    Farm selectIncludingDeleted(@Param("id") Long id);
}

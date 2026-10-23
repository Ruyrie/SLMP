package com.zhimu.server.admin.base.mapper.readonly;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 基础数据推断养殖场畜种的只读查询（用户已批准管理端只读 Mapper 读表；只 SELECT，不写）。
 * U10 在未传 species/category_id 且带 X-Farm-Id 时，用养殖场主要品类推断 PEN_TYPE / VACCINATE_METHOD 的畜种。
 * 只读 t_farm.main_category_id；不修改模块02 的实体与 Mapper。
 */
public interface FarmMainCategoryReadMapper {

    @Select("SELECT main_category_id FROM t_farm WHERE id = #{farmId} AND deleted = 0")
    Long selectMainCategoryId(@Param("farmId") Long farmId);
}

package com.zhimu.server.biz.inspect.mapper;

import com.zhimu.server.biz.inspect.query.InspectTemplateHeadRow;
import com.zhimu.server.biz.inspect.query.InspectTemplateItemRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 巡检模板只读查询（管理端-03 的 V2_08 数据；不调用 U13）。 */
public interface InspectTemplateReadMapper {

    @Select("SELECT t.id AS templateId, t.online_version_no AS versionNo, v.id AS versionId "
            + "FROM t_inspect_template t JOIN t_inspect_template_version v "
            + "ON v.template_id = t.id AND v.version_no = t.online_version_no "
            + "WHERE t.category_id = #{categoryId} AND v.status = 'PUBLISHED' LIMIT 1")
    InspectTemplateHeadRow selectPublishedByCategory(@Param("categoryId") Long categoryId);

    @Select("SELECT t.id AS templateId, v.version_no AS versionNo, v.id AS versionId "
            + "FROM t_inspect_template t JOIN t_inspect_template_version v ON v.template_id = t.id "
            + "WHERE t.id = #{templateId} AND v.version_no = #{versionNo} LIMIT 1")
    InspectTemplateHeadRow selectVersion(@Param("templateId") Long templateId,
                                         @Param("versionNo") Integer versionNo);

    @Select("SELECT i.id AS itemId, i.item_name AS itemName, i.sort_no AS itemSort, "
            + "o.id AS optionId, o.option_name AS optionName, o.is_abnormal AS optionAbnormal, "
            + "o.sort_no AS optionSort "
            + "FROM t_inspect_item i JOIN t_inspect_option o ON o.item_id = i.id "
            + "WHERE i.version_id = #{versionId} AND i.enabled = 1 "
            + "ORDER BY i.sort_no, i.id, o.sort_no, o.id")
    List<InspectTemplateItemRow> selectItems(@Param("versionId") Long versionId);
}


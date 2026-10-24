package com.zhimu.server.admin.content.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 内容运营跨表只读查询：管理员、品类、品种、养殖场、问答搜索与操作日志。 */
public interface ContentLookupMapper {

    List<NameRow> selectAdminNames(@Param("ids") List<Long> ids);

    List<NameRow> selectCategoryNames(@Param("ids") List<Long> ids);

    /** 按名称取启用的分类（问答转任务建议分类） */
    Long selectCategoryIdByName(@Param("name") String name);

    List<NameRow> selectBreedNames(@Param("ids") List<Long> ids);

    /** 品种所属品类，校验发布配置时使用；品种不存在返回 null */
    Long selectBreedCategoryId(@Param("breedId") Long breedId);

    /** 按品类与名称取启用品种 ID，导入行情时匹配“品种”列；未找到返回 null */
    Long selectEnabledBreedIdByName(@Param("categoryId") Long categoryId, @Param("name") String name);

    List<NameRow> selectUserNames(@Param("ids") List<Long> ids);

    List<Long> selectFarmIdsByName(@Param("keyword") String keyword);

    List<Long> selectUserIdsByName(@Param("keyword") String keyword);

    /** 操作日志时间线（t_operation_log） */
    List<TimelineRow> selectTimeline(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /** 有指定权限码的正常管理员（知识补充任务提醒用） */
    List<Long> selectAdminIdsByPermission(@Param("permission") String permission);
}

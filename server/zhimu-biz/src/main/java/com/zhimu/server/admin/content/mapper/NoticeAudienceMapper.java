package com.zhimu.server.admin.content.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 通知接收人解析：直接读 t_user / t_farm / t_farm_member（跨表只读）。 */
public interface NoticeAudienceMapper {

    /** 全部正常账号 */
    List<AudienceRow> selectNormalUsers();

    /** 已通过审核养殖场中的在场成员（全部用户范围取最高角色，含已停用/冻结场） */
    List<AudienceRow> selectMembershipsOfNormalUsers();

    /** 按条件筛选：角色、省份、品类；已通过审核、未停用、未冻结的养殖场 */
    List<AudienceRow> selectConditionMembers(@Param("roles") List<String> roles,
                                             @Param("provinceCodes") List<String> provinceCodes,
                                             @Param("categoryIds") List<Long> categoryIds);

    /** 指定养殖场：所选养殖场的全部在场成员 */
    List<AudienceRow> selectFarmMembers(@Param("farmIds") List<Long> farmIds);

    /** 批量取养殖场简要信息（用于条件回填与名称） */
    List<FarmBriefRow> selectFarms(@Param("ids") List<Long> ids);

    /** 按名称搜索已通过审核的养殖场 */
    List<FarmBriefRow> searchFarms(@Param("keyword") String keyword);
}

package com.zhimu.server.biz.breeding.mapper;

import com.zhimu.server.biz.breeding.support.BreedingMemberRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 本模块只读引用模块02 的养殖场成员关系（登记人/执行人角色回显）。
 * 模块02 目前没有提供“按用户取本场角色”的 api，这里用只读 SQL 取数，后续可替换为跨模块 api 调用。
 */
public interface BreedingRefMapper {

    /** 养殖场在场成员（所有者、场长、员工） */
    @Select("""
            SELECT user_id, role
            FROM t_farm_member
            WHERE farm_id = #{farmId} AND active_flag = 1 AND status = 'ACTIVE'
            """)
    List<BreedingMemberRow> memberRoles(@Param("farmId") Long farmId);
}

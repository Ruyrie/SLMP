package com.zhimu.server.admin.system.mapper.readonly;

import com.zhimu.server.admin.system.query.AdminFarmMemberRow;
import com.zhimu.server.admin.system.query.AdminFarmOwnerRow;
import com.zhimu.server.admin.system.query.AdminFarmTimelineRow;
import com.zhimu.server.admin.system.vo.AdminFarmItemVO;
import com.zhimu.server.biz.farm.entity.Farm;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** ADM-S15/S16 养殖场管理只读查询：列表汇总、成员与平台记录。 */
public interface AdminFarmQueryMapper {

    List<AdminFarmItemVO> selectFarms(@Param("status") String status,
                                      @Param("keyword") String keyword,
                                      @Param("categoryId") Long categoryId,
                                      @Param("provincePrefix") String provincePrefix,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    long countFarms(@Param("status") String status,
                    @Param("keyword") String keyword,
                    @Param("categoryId") Long categoryId,
                    @Param("provincePrefix") String provincePrefix);

    long countFarmsByStatus(@Param("status") String status,
                            @Param("keyword") String keyword,
                            @Param("categoryId") Long categoryId,
                            @Param("provincePrefix") String provincePrefix);

    Farm selectFarm(@Param("id") Long id);

    AdminFarmOwnerRow selectOwner(@Param("farmId") Long farmId);

    Integer selectStockCount(@Param("farmId") Long farmId);

    Integer selectActiveBatchCount(@Param("farmId") Long farmId);

    Integer selectRecords7d(@Param("farmId") Long farmId,
                            @Param("start") LocalDate start,
                            @Param("end") LocalDate end);

    Integer selectActiveMembers7d(@Param("farmId") Long farmId,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    Integer selectMemberTotal(@Param("farmId") Long farmId);

    List<AdminFarmMemberRow> selectMembers(@Param("farmId") Long farmId);

    int countInvites(@Param("farmId") Long farmId, @Param("status") String status);

    List<AdminFarmTimelineRow> selectTimeline(@Param("farmId") Long farmId);

    Long selectPendingChangeId(@Param("farmId") Long farmId);

    String selectAdminName(@Param("adminId") Long adminId);
}

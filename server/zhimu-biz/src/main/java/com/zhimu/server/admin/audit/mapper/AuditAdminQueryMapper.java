package com.zhimu.server.admin.audit.mapper;

import com.zhimu.server.admin.audit.query.ChangeRequestHistoryRow;
import com.zhimu.server.admin.audit.query.FarmBriefRow;
import com.zhimu.server.admin.audit.query.FarmChangeStatsRow;
import com.zhimu.server.admin.audit.query.QueueStatRow;
import com.zhimu.server.admin.audit.query.RecoveryFarmRow;
import com.zhimu.server.admin.audit.query.RecoveryVerifyLogRow;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminItemVO;
import com.zhimu.server.admin.audit.vo.FarmAuditItemVO;
import com.zhimu.server.admin.audit.vo.FarmChangeItemVO;
import com.zhimu.server.admin.audit.vo.RecoveryItemVO;
import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmChange;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核中心只读查询 Mapper（管理端-01）：跨表读取队列、详情与统计，写操作一律调用归属模块 Service。
 * 自定义 SQL 不经过多租户插件，所有养殖场表显式过滤 deleted = 0。
 */
public interface AuditAdminQueryMapper {

    // ===== ADM-R01 待审核数 =====

    List<QueueStatRow> selectQueueStats(@Param("overdueBefore") LocalDateTime overdueBefore);

    long countSellableDatePending();

    // ===== ADM-R02/R03 注册审核 =====

    List<FarmAuditItemVO> selectFarmAudits(@Param("status") String status,
                                           @Param("keyword") String keyword,
                                           @Param("categoryId") Long categoryId,
                                           @Param("provincePrefix") String provincePrefix,
                                           @Param("submitType") String submitType,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    long countFarmAudits(@Param("status") String status,
                         @Param("keyword") String keyword,
                         @Param("categoryId") Long categoryId,
                         @Param("provincePrefix") String provincePrefix,
                         @Param("submitType") String submitType);

    long countOverdueFarmAudits(@Param("keyword") String keyword,
                                @Param("categoryId") Long categoryId,
                                @Param("provincePrefix") String provincePrefix,
                                @Param("submitType") String submitType,
                                @Param("overdueBefore") LocalDateTime overdueBefore);

    List<String> selectOverdueFarmNames(@Param("keyword") String keyword,
                                        @Param("categoryId") Long categoryId,
                                        @Param("provincePrefix") String provincePrefix,
                                        @Param("submitType") String submitType,
                                        @Param("overdueBefore") LocalDateTime overdueBefore,
                                        @Param("limit") int limit);

    FarmAudit selectFarmAudit(@Param("id") Long id);

    Farm selectFarmIncludingDeleted(@Param("id") Long id);

    User selectUser(@Param("id") Long id);

    List<FarmAudit> selectFarmAuditsOfFarm(@Param("farmId") Long farmId);

    String selectAdminName(@Param("id") Long id);

    List<FarmBriefRow> selectOwnerFarms(@Param("ownerUserId") Long ownerUserId,
                                        @Param("excludeFarmId") Long excludeFarmId);

    List<FileEntity> selectFiles(@Param("ids") List<Long> ids);

    // ===== ADM-R06/R07 资料变更 =====

    List<FarmChangeItemVO> selectFarmChanges(@Param("status") String status,
                                             @Param("keyword") String keyword,
                                             @Param("changeItem") String changeItem,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    long countFarmChanges(@Param("status") String status,
                          @Param("keyword") String keyword,
                          @Param("changeItem") String changeItem);

    FarmChangeStatsRow selectFarmChangeStats(@Param("keyword") String keyword,
                                             @Param("changeItem") String changeItem,
                                             @Param("since") LocalDateTime since,
                                             @Param("now") LocalDateTime now);

    FarmChange selectFarmChange(@Param("id") Long id);

    long countFarmStock(@Param("farmId") Long farmId);

    long countFarmActiveMembers(@Param("farmId") Long farmId);

    /** 资料变更通过前复核：所有者名下其他未删除养殖场是否同名 */
    long countOwnerFarmName(@Param("ownerUserId") Long ownerUserId,
                            @Param("excludeFarmId") Long excludeFarmId,
                            @Param("name") String name);

    // ===== ADM-R10/R11 修改申请 =====

    List<ChangeRequestAdminItemVO> selectChangeRequests(@Param("status") String status,
                                                        @Param("keyword") String keyword,
                                                        @Param("sourceModule") String sourceModule,
                                                        @Param("impact") String impact,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);

    long countChangeRequests(@Param("status") String status,
                             @Param("keyword") String keyword,
                             @Param("sourceModule") String sourceModule,
                             @Param("impact") String impact);

    long countChangeRequestsByStatus(@Param("status") String status,
                                     @Param("keyword") String keyword,
                                     @Param("sourceModule") String sourceModule,
                                     @Param("impact") String impact);

    ChangeRequest selectChangeRequest(@Param("id") Long id);

    String selectFarmName(@Param("id") Long id);

    String selectUserName(@Param("id") Long id);

    String selectBatchNo(@Param("id") Long id);

    List<ChangeRequestHistoryRow> selectChangeRequestHistory(@Param("bizType") String bizType,
                                                             @Param("bizId") Long bizId,
                                                             @Param("excludeId") Long excludeId,
                                                             @Param("limit") int limit);

    // ===== ADM-R14/R15 账号找回 =====

    List<RecoveryItemVO> selectRecoveries(@Param("tab") String tab,
                                          @Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    long countRecoveries(@Param("tab") String tab, @Param("keyword") String keyword);

    long countRecoveryByStatus(@Param("status") String status);

    AccountRecovery selectAccountRecovery(@Param("id") Long id);

    User selectUserByPhone(@Param("phone") String phone);

    LocalDateTime selectLatestRecoveryCodeCreatedAt(@Param("userId") Long userId);

    List<RecoveryFarmRow> selectRecoveryFarms(@Param("userId") Long userId);

    List<RecoveryVerifyLogRow> selectRecoveryVerifyLogs(@Param("userId") Long userId,
                                                        @Param("oldPhone") String oldPhone,
                                                        @Param("limit") int limit);

    long countUserRecoveriesSince(@Param("userId") Long userId,
                                  @Param("since") LocalDateTime since,
                                  @Param("excludeId") Long excludeId);
}

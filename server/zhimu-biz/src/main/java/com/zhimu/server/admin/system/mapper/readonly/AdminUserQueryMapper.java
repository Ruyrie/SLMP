package com.zhimu.server.admin.system.mapper.readonly;

import com.zhimu.server.admin.system.query.AdminLoginLogRow;
import com.zhimu.server.admin.system.query.AdminUserFarmRow;
import com.zhimu.server.admin.system.query.AdminUserListRow;
import com.zhimu.server.biz.account.entity.User;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** ADM-S10/S11 用户账号只读查询：列表聚合与详情关联。 */
public interface AdminUserQueryMapper {

    List<AdminUserListRow> selectUsers(@Param("status") String status,
                                       @Param("keyword") String keyword,
                                       @Param("role") String role,
                                       @Param("registeredFrom") LocalDate registeredFrom,
                                       @Param("registeredTo") LocalDate registeredTo,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countUsers(@Param("status") String status,
                    @Param("keyword") String keyword,
                    @Param("role") String role,
                    @Param("registeredFrom") LocalDate registeredFrom,
                    @Param("registeredTo") LocalDate registeredTo);

    long countByStatus(@Param("status") String status,
                       @Param("keyword") String keyword,
                       @Param("role") String role,
                       @Param("registeredFrom") LocalDate registeredFrom,
                       @Param("registeredTo") LocalDate registeredTo);

    User selectUser(@Param("id") Long id);

    List<AdminUserFarmRow> selectUserFarms(@Param("userId") Long userId);

    List<AdminLoginLogRow> selectLoginLogs(@Param("userId") Long userId,
                                           @Param("since") LocalDateTime since,
                                           @Param("limit") int limit);

    LocalDateTime selectLatestRecoveryCodeCreatedAt(@Param("userId") Long userId);

    String selectLatestRecoveryStatus(@Param("userId") Long userId);
}

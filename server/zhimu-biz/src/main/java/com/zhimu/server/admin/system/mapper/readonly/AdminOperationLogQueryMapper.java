package com.zhimu.server.admin.system.mapper.readonly;

import com.zhimu.server.biz.common.entity.OperationLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 管理端操作日志只读查询（t_operation_log，公共能力 biz.common 的表）。
 * 按任务书允许在 admin.system.mapper.readonly 直接写 SELECT，禁止写操作；
 * 可见性由 Service 按操作人权限生成 allowedOpTypes，SQL 显式追加（管理员本人日志始终可见）。
 */
public interface AdminOperationLogQueryMapper {

    List<OperationLog> selectAdminLogs(@Param("viewerId") Long viewerId,
                                       @Param("allVisible") boolean allVisible,
                                       @Param("allowedOpTypes") Collection<String> allowedOpTypes,
                                       @Param("dateFrom") LocalDate dateFrom,
                                       @Param("dateTo") LocalDate dateTo,
                                       @Param("filterAdminId") Long filterAdminId,
                                       @Param("opType") String opType,
                                       @Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countAdminLogs(@Param("viewerId") Long viewerId,
                        @Param("allVisible") boolean allVisible,
                        @Param("allowedOpTypes") Collection<String> allowedOpTypes,
                        @Param("dateFrom") LocalDate dateFrom,
                        @Param("dateTo") LocalDate dateTo,
                        @Param("filterAdminId") Long filterAdminId,
                        @Param("opType") String opType,
                        @Param("keyword") String keyword);

    /** 按主键读取单条；可见性由 Service 判断，不可见按不存在处理 */
    OperationLog selectLogById(@Param("id") Long id);

    /** 同一对象（biz_type + biz_id）的其他日志，最多 limit 条，含用户端日志 */
    List<OperationLog> selectRelated(@Param("bizType") String bizType,
                                     @Param("bizId") Long bizId,
                                     @Param("excludeId") Long excludeId,
                                     @Param("limit") int limit);
}

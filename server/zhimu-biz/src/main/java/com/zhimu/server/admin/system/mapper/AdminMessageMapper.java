package com.zhimu.server.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.system.entity.AdminMessage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端站内消息（t_admin_message）。merge_key 去重（同一接收人同一键只保留一条），
 * 自定义 SQL 显式过滤 deleted = 0，因为 XML 不经过 @TableLogic 拼接。
 */
public interface AdminMessageMapper extends BaseMapper<AdminMessage> {

    AdminMessage selectByMergeKey(@Param("adminId") Long adminId, @Param("mergeKey") String mergeKey);

    int updateReadByIds(@Param("adminId") Long adminId, @Param("ids") List<Long> ids,
                        @Param("readAt") LocalDateTime readAt);

    int updateReadAll(@Param("adminId") Long adminId, @Param("readAt") LocalDateTime readAt);

    long countUnread(@Param("adminId") Long adminId);

    long countAll(@Param("adminId") Long adminId);
}

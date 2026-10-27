package com.zhimu.server.biz.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.account.entity.AccountCancel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 注销记录 Mapper */
public interface AccountCancelMapper extends BaseMapper<AccountCancel> {

    @Select("SELECT * FROM t_account_cancel WHERE user_id = #{userId} FOR UPDATE")
    AccountCancel selectByUserForUpdate(@Param("userId") Long userId);

    @Select("SELECT user_id FROM t_account_cancel WHERE cleanup_status IN ('PENDING','WAITING','FAILED','RUNNING') "
            + "AND (next_run_at IS NULL OR next_run_at <= #{now}) ORDER BY next_run_at, id LIMIT 100")
    List<Long> selectDueUserIds(@Param("now") LocalDateTime now);

    /** 显式写 NULL，避免 updateById 的默认非空策略保留过时的错误/下一次时间。 */
    @Update("UPDATE t_account_cancel SET personal_cleaned_at=#{row.personalCleanedAt}, "
            + "phone_released_at=#{row.phoneReleasedAt}, farm_cleaned_at=#{row.farmCleanedAt}, "
            + "phone_release_at=#{row.phoneReleaseAt}, farm_cleanup_at=#{row.farmCleanupAt}, "
            + "cleanup_status=#{row.cleanupStatus}, retry_count=#{row.retryCount}, last_error=#{row.lastError}, "
            + "next_run_at=#{row.nextRunAt}, updated_at=#{now} WHERE id=#{row.id} AND user_id=#{row.userId}")
    int updateCleanupProgress(@Param("row") AccountCancel row, @Param("now") LocalDateTime now);
}

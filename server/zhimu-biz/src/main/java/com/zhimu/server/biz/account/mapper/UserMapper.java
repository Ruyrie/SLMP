package com.zhimu.server.biz.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.account.entity.User;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 用户账号 Mapper（复杂 SQL 见 resources/mapper/account/UserMapper.xml）。 */
public interface UserMapper extends BaseMapper<User> {

    /** 按手机号查账号（含已注销）；phone 为空返回 null */
    User selectByPhone(@Param("phone") String phone);

    /** 注销、找回审核等并发场景锁定本人行 */
    User selectByIdForUpdate(@Param("id") Long id);

    /**
     * 原子累加恢复码填错次数：本轮第一次填错超过 24 小时则从 1 重新计数，否则加 1。
     *
     * @return 更新行数
     */
    int increaseRecoveryFail(@Param("id") Long id, @Param("windowStart") LocalDateTime windowStart,
                             @Param("now") LocalDateTime now);

    /** 清零找回填错字段 */
    int clearRecoveryFailure(@Param("id") Long id);

    /** token_version 加 1 并写入失效原因 */
    int bumpTokenVersion(@Param("id") Long id, @Param("reason") String reason, @Param("now") LocalDateTime now);
}

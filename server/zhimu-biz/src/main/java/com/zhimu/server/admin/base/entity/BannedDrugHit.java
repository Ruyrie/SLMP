package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 禁用药命中拦截记录（t_banned_drug_hit）：只追加。
 * 没有 updated_at / version，也不存在逻辑删除，因此不继承 BaseEntity。
 */
@Getter
@Setter
@TableName("t_banned_drug_hit")
public class BannedDrugHit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bannedDrugId;
    /** BANNED_HIT_SOURCE：SEARCH / ARCHIVE / STOCK_IN / AI_QA */
    private String hitSource;
    private Long farmId;
    private Long userId;
    private String keyword;
    private Long refId;
    private LocalDateTime hitAt;
}

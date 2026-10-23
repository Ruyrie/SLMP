package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 禁用药清单（t_banned_drug）：按名称/别名命中，U11、模块08/09/11 调用拦截。 */
@Getter
@Setter
@TableName("t_banned_drug")
public class BannedDrug extends BaseAdminEntity {

    private String name;
    /** 别名，顿号分隔，例如 克伦特罗、瘦肉精 */
    private String alias;
    /** BANNED_CLASS：BETA_AGONIST / ANTIBACTERIAL / NITROFURAN / HORMONE / SEDATIVE / PESTICIDE / OTHER */
    private String bannedClass;
    /** 从目录标记时指向目录项 */
    private Long catalogId;
    private String reason;
    private LocalDateTime listedAt;
    private Long listedBy;
    private Integer sortNo;
    /** 近 30 天命中次数；BaseStatJob 每小时刷新 */
    @TableField("hit_count_30d")
    private Integer hitCount30d;
}

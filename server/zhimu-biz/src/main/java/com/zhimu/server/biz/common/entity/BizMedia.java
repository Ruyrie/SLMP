package com.zhimu.server.biz.common.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 业务记录与文件关联（t_biz_media，05 号 5.3 节）。
 * 唯一键 (biz_type, biz_id, file_id) 不含 deleted：重新绑定同一文件时先查含已删除的行再复活，不能直接 insert。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_biz_media")
@FarmScoped
public class BizMedia extends AuditEntity {

    private String bizType;
    private Long bizId;
    private Long fileId;
    private Long farmId;
    private Integer sortNo;
    private String sourceBizType;
    private Long sourceBizId;

    @TableLogic
    private Integer deleted;
}

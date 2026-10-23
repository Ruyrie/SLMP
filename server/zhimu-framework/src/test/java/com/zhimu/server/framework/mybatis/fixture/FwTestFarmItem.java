package com.zhimu.server.framework.mybatis.fixture;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

/** 测试用养殖场表 */
@Getter
@Setter
@FarmScoped
@TableName("t_fwtest_farm_item")
public class FwTestFarmItem extends AuditEntity {

    private Long farmId;

    private String name;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}

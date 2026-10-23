package com.zhimu.server.framework.mybatis.fixture;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 测试用全局表（没有 farm_id，不受多租户影响） */
@Getter
@Setter
@TableName("t_fwtest_global_item")
public class FwTestGlobalItem extends BaseEntity {

    private String name;
}

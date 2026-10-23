package com.zhimu.server.framework.mybatis;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在实体类上，声明该表属于养殖场（带 farm_id 列）：有 FarmContext 时，多租户插件对该表的查询、更新、删除自动加
 * farm_id = 当前养殖场，新增时未显式设置 farm_id 则自动补上。没有标注的表一律不加条件（显式登记，宁缺毋滥）。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FarmScoped {
}

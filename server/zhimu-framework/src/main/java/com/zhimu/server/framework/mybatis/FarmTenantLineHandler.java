package com.zhimu.server.framework.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.zhimu.server.common.context.FarmContext;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;

import java.util.List;

/**
 * 多租户：租户字段 farm_id，租户值取 {@link FarmContext#farmId()}。
 * 以下情况不加条件：没有养殖场上下文、处于 {@link TenantSkip} 范围、表没有在 {@link FarmTableRegistry} 登记。
 * 新增语句已显式给出 farm_id 列时保持原值，不重复追加。
 */
@RequiredArgsConstructor
public class FarmTenantLineHandler implements TenantLineHandler {

    public static final String TENANT_COLUMN = "farm_id";

    private final FarmTableRegistry registry;

    @Override
    public Expression getTenantId() {
        return new LongValue(FarmContext.farmId());
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return TenantSkip.isSkipped() || FarmContext.farmId() == null || !registry.contains(tableName);
    }

    @Override
    public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        return columns.stream()
                .map(column -> column.getColumnName().replace("`", ""))
                .anyMatch(name -> name.equalsIgnoreCase(tenantIdColumn));
    }
}

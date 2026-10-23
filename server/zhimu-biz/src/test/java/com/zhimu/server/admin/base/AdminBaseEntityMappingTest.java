package com.zhimu.server.admin.base;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.admin.base.entity.BannedDrug;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * admin.base 实体的列名回归：MyBatis-Plus 驼峰转换遇到数字会丢掉下划线
 * （hitCount30d → hit_count30d），必须在字段上显式声明 @TableField。
 * 运行态冒烟报告：/api/v1/base/drug-catalog 与模块09 药品接口因该列名 500。
 */
class AdminBaseEntityMappingTest {

    @BeforeAll
    static void initTableInfo() {
        AdminBaseTableInfo.ensure();
    }

    @Test
    void bannedDrugHitCountColumnMatchesDdl() {
        TableInfo info = TableInfoHelper.getTableInfo(BannedDrug.class);
        assertThat(info).isNotNull();

        TableFieldInfo field = info.getFieldList().stream()
                .filter(item -> "hitCount30d".equals(item.getProperty()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("BannedDrug.hitCount30d 未注册到 TableInfo"));

        assertThat(field.getColumn()).isEqualTo("hit_count_30d");
        assertThat(info.getAllSqlSelect()).contains("hit_count_30d");
        assertThat(info.getAllSqlSelect()).doesNotContain("hit_count30d");
    }
}

package com.zhimu.server.framework.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * Mapper 约定：接口放在 {@code com.zhimu.server.<模块>.mapper} 包（含子包）下，继承 BaseMapper，由这里的 @MapperScan
 * 统一扫描，不需要再标 @Mapper；mapper 包下不要放其他接口。XML 放在 resources/mapper/&lt;模块&gt;/XxxMapper.xml。
 * <p>
 * 插件顺序：多租户 → 分页 → 乐观锁 → 防全表更新删除。
 * <ul>
 *     <li>多租户：只对标了 {@link FarmScoped} 的实体表生效，见 {@link FarmTenantLineHandler}；跨养殖场查询用 {@link TenantSkip}</li>
 *     <li>分页：MySQL，单页最多 {@value #MAX_PAGE_SIZE} 条，页码超出不回到首页</li>
 *     <li>乐观锁：实体的 version 字段标 {@code @Version}，冲突时更新返回 0 行，由 Service 抛 40902</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.zhimu.server.**.mapper")
public class MybatisPlusConfig {

    public static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public FarmTableRegistry farmTableRegistry() {
        return new FarmTableRegistry();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(FarmTableRegistry farmTableRegistry) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new FarmTenantLineHandler(farmTableRegistry)));
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}

package com.zhimu.server.framework.mybatis;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 养殖场表登记：启动后从 TableInfoHelper 收集标了 {@link FarmScoped} 的实体表名，多租户插件只对这些表加 farm_id 条件。
 * 启动后才注册的实体（例如懒加载的 Mapper）在第一次遇到其表名时补查一次。
 */
@Slf4j
public class FarmTableRegistry implements SmartInitializingSingleton {

    private final Set<String> tables = ConcurrentHashMap.newKeySet();

    @Override
    public void afterSingletonsInstantiated() {
        refresh();
        log.info("多租户养殖场表 {} 张：{}", tables.size(), new TreeSet<>(tables));
    }

    /** 重新从 TableInfoHelper 收集 */
    public void refresh() {
        for (TableInfo info : TableInfoHelper.getTableInfos()) {
            if (isFarmScoped(info)) {
                tables.add(normalize(info.getTableName()));
            }
        }
    }

    /** 补登记没有实体、只在 Mapper XML 中使用的养殖场表 */
    public void register(String tableName) {
        tables.add(normalize(tableName));
    }

    public boolean contains(String tableName) {
        if (tableName == null) {
            return false;
        }
        String name = normalize(tableName);
        if (tables.contains(name)) {
            return true;
        }
        TableInfo info = TableInfoHelper.getTableInfo(name);
        if (info != null && isFarmScoped(info)) {
            tables.add(name);
            return true;
        }
        return false;
    }

    public Set<String> tables() {
        return Set.copyOf(tables);
    }

    private static boolean isFarmScoped(TableInfo info) {
        return info.getEntityType() != null
                && AnnotatedElementUtils.hasAnnotation(info.getEntityType(), FarmScoped.class);
    }

    /** 去掉反引号与库名前缀，统一小写 */
    static String normalize(String tableName) {
        String name = tableName.replace("`", "").trim();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return name.toLowerCase(Locale.ROOT);
    }
}

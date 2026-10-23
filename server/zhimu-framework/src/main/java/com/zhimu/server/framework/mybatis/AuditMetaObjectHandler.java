package com.zhimu.server.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.util.BeijingTime;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充。字段须在实体中标注 {@code @TableField(fill = ...)}（{@link BaseEntity}、{@link AuditEntity} 已标注）：
 * <ul>
 *     <li>新增：created_at、updated_at 填北京时间，created_by 填当前用户 ID，没有则填管理员 ID，都没有填 0；只填为 null 的字段</li>
 *     <li>修改：updated_at 总是刷新为当前时间（从库里查出的实体再更新时也会刷新）</li>
 * </ul>
 * 注意：{@code update(null, wrapper)} 没有实体，无法填充；需要刷新 updated_at 时传一个空实体 {@code update(new Xxx(), wrapper)}。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String CREATED_BY = "createdBy";

    /** 系统生成的记录 created_by 为 0 */
    public static final long SYSTEM_OPERATOR = 0L;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = BeijingTime.now();
        strictInsertFill(metaObject, CREATED_AT, () -> now, LocalDateTime.class);
        strictInsertFill(metaObject, UPDATED_AT, () -> now, LocalDateTime.class);
        strictInsertFill(metaObject, CREATED_BY, AuditMetaObjectHandler::currentOperatorId, Long.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        TableInfo tableInfo = findTableInfo(metaObject);
        if (tableInfo == null) {
            return;
        }
        boolean hasUpdatedAt = tableInfo.getFieldList().stream()
                .anyMatch(field -> UPDATED_AT.equals(field.getProperty())
                        && LocalDateTime.class.equals(field.getPropertyType())
                        && field.isWithUpdateFill());
        if (hasUpdatedAt) {
            metaObject.setValue(UPDATED_AT, BeijingTime.now());
        }
    }

    /** 当前操作人：用户 → 管理员 → 系统（0） */
    public static Long currentOperatorId() {
        Long userId = UserContext.userId();
        if (userId != null) {
            return userId;
        }
        Long adminId = AdminContext.adminId();
        return adminId != null ? adminId : SYSTEM_OPERATOR;
    }
}

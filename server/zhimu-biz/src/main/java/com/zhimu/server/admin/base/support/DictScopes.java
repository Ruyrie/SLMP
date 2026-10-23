package com.zhimu.server.admin.base.support;

import com.zhimu.server.admin.base.entity.DictItemScope;

import java.util.List;
import java.util.Objects;

/** 字典适用范围匹配：传 category_id 时返回“无范围 + 该品类畜种 + 该品类”的项；只传 species 时返回无范围与该畜种的项。 */
public final class DictScopes {

    public static final String SPECIES = "SPECIES";
    public static final String CATEGORY = "CATEGORY";

    private DictScopes() {
    }

    public static boolean none(List<DictItemScope> scopes) {
        return scopes == null || scopes.isEmpty();
    }

    public static boolean matches(List<DictItemScope> scopes, String species, Long categoryId) {
        if (none(scopes)) {
            return true;
        }
        for (DictItemScope scope : scopes) {
            if (CATEGORY.equals(scope.getScopeType()) && categoryId != null
                    && Objects.equals(scope.getCategoryId(), categoryId)) {
                return true;
            }
            if (SPECIES.equals(scope.getScopeType()) && species != null
                    && species.equals(scope.getSpecies())) {
                return true;
            }
        }
        return false;
    }
}

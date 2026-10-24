package com.zhimu.server.admin.content.support;

import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 管理员姓名快照：列表的“更新 · 操作人”、按人筛选、指派给。 */
@Component
@RequiredArgsConstructor
public class ContentNames {

    private final AdminMapper adminMapper;

    public String adminName(Long adminId) {
        if (adminId == null) {
            return null;
        }
        Admin admin = adminMapper.selectById(adminId);
        return admin == null ? null : admin.getName();
    }

    public Map<Long, String> adminNames(Collection<Long> ids) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return result;
        }
        for (Admin admin : adminMapper.selectBatchIds(distinct)) {
            result.put(admin.getId(), admin.getName());
        }
        return result;
    }
}

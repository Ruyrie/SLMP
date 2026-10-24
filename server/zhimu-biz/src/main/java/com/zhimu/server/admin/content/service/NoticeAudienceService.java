package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.entity.NoticeTarget;
import com.zhimu.server.admin.content.mapper.AudienceRow;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.NameRow;
import com.zhimu.server.admin.content.mapper.NoticeAudienceMapper;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.NoticeVos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通知接收范围解析（ADM-C03 与通知分发共用）：
 * ALL = 全部正常账号，角色取已通过审核养殖场中的最高在场角色；
 * CONDITION = 已通过审核、未停用、未冻结养殖场的角色在范围内成员；
 * FARM = 所选养殖场（已通过审核）的全部在场成员，用户端标签养殖场取交集最小 ID。
 * 一个账号只出现一次。
 */
@Service
@RequiredArgsConstructor
public class NoticeAudienceService {

    private static final Map<String, Integer> ROLE_RANK = Map.of("OWNER", 3, "MANAGER", 2, "STAFF", 1, "NONE", 0);
    private static final Set<String> ROLES = Set.of("OWNER", "MANAGER", "STAFF");

    private final NoticeAudienceMapper audienceMapper;
    private final ContentLookupMapper lookupMapper;

    /** 解析后的接收人；farmId 为 null 表示全部用户/按条件范围（消息不挂养殖场标签） */
    public record Recipient(Long userId, String userName, String role, Long farmId) {
    }

    public record Result(List<Recipient> recipients, int farmCount) {
    }

    /** ADM-C03 预计送达 */
    public NoticeVos.AudienceVO estimate(ContentRequests.AudienceRequest request) {
        Result result = resolve(request.getScopeType(), request.getRoles(), request.getProvinceCodes(),
                request.getCategoryIds(), request.getFarmIds(), true);
        NoticeVos.AudienceVO vo = new NoticeVos.AudienceVO();
        vo.setAccountCount(result.recipients().size());
        vo.setFarmCount(result.farmCount());
        return vo;
    }

    /** 发送时重新解析接收人；条件不合法或为空由调用方决定是否报错 */
    public Result resolveForNotice(Notice notice, List<NoticeTarget> targets) {
        List<String> roles = new ArrayList<>();
        List<String> provinces = new ArrayList<>();
        List<Long> categories = new ArrayList<>();
        List<Long> farms = new ArrayList<>();
        for (NoticeTarget target : targets) {
            switch (target.getTargetType()) {
                case "ROLE" -> roles.add(target.getTargetValue());
                case "PROVINCE" -> provinces.add(target.getTargetValue());
                case "CATEGORY" -> categories.add(toLong(target.getTargetValue()));
                case "FARM" -> farms.add(toLong(target.getTargetValue()));
                default -> {
                }
            }
        }
        return resolve(notice.getScopeType(), roles, provinces, categories, farms, true);
    }

    /**
     * @param validate false 时用于草稿的预计送达：条件不完整返回 0，不报错
     */
    public Result resolve(String scopeType, List<String> roles, List<String> provinceCodes,
                          List<Long> categoryIds, List<Long> farmIds, boolean validate) {
        String scope = scopeType == null || scopeType.isBlank() ? "ALL" : scopeType.trim().toUpperCase();
        List<String> safeRoles = cleanRoles(roles);
        List<String> safeProvinces = cleanStrings(provinceCodes);
        List<Long> safeCategories = cleanIds(categoryIds);
        List<Long> safeFarms = cleanIds(farmIds);
        if (validate) {
            validate(scope, safeRoles, safeProvinces, safeFarms);
        } else if (!"ALL".equals(scope) && !"CONDITION".equals(scope) && !"FARM".equals(scope)) {
            return new Result(List.of(), 0);
        }
        return switch (scope) {
            case "CONDITION" -> {
                if (safeRoles.isEmpty()) {
                    yield new Result(List.of(), 0);
                }
                yield aggregate(audienceMapper.selectConditionMembers(safeRoles, safeProvinces, safeCategories),
                        false, false);
            }
            case "FARM" -> {
                if (safeFarms.isEmpty()) {
                    yield new Result(List.of(), 0);
                }
                yield aggregate(audienceMapper.selectFarmMembers(safeFarms), true, true);
            }
            default -> aggregateAll();
        };
    }

    private Result aggregateAll() {
        List<AudienceRow> users = audienceMapper.selectNormalUsers();
        List<AudienceRow> memberships = audienceMapper.selectMembershipsOfNormalUsers();
        Map<Long, Recipient> byUser = new LinkedHashMap<>();
        for (AudienceRow user : users) {
            byUser.put(user.getUserId(), new Recipient(user.getUserId(), user.getUserName(), "NONE", null));
        }
        Map<Long, String> roleByUser = new HashMap<>();
        for (AudienceRow membership : memberships) {
            if (membership.getUserId() == null || !byUser.containsKey(membership.getUserId())) {
                continue;
            }
            String current = roleByUser.get(membership.getUserId());
            if (current == null || rank(membership.getRole()) > rank(current)) {
                roleByUser.put(membership.getUserId(), membership.getRole());
            }
        }
        for (Map.Entry<Long, String> entry : roleByUser.entrySet()) {
            Recipient recipient = byUser.get(entry.getKey());
            byUser.put(entry.getKey(), new Recipient(recipient.userId(), recipient.userName(), entry.getValue(), null));
        }
        Set<Long> farms = new HashSet<>();
        memberships.forEach(m -> {
            if (m.getFarmId() != null) {
                farms.add(m.getFarmId());
            }
        });
        return new Result(new ArrayList<>(byUser.values()), farms.size());
    }

    private Result aggregate(List<AudienceRow> rows, boolean farmScope, boolean minFarmId) {
        Map<Long, Recipient> byUser = new LinkedHashMap<>();
        Map<Long, String> roleByUser = new HashMap<>();
        Map<Long, Long> farmByUser = new HashMap<>();
        Set<Long> farms = new HashSet<>();
        for (AudienceRow row : rows) {
            if (row.getUserId() == null) {
                continue;
            }
            byUser.putIfAbsent(row.getUserId(), new Recipient(row.getUserId(), row.getUserName(),
                    "NONE", farmScope ? row.getFarmId() : null));
            if (row.getFarmId() != null) {
                farms.add(row.getFarmId());
            }
            String current = roleByUser.get(row.getUserId());
            if (current == null || rank(row.getRole()) > rank(current)) {
                roleByUser.put(row.getUserId(), row.getRole());
            }
            if (row.getFarmId() != null) {
                long candidate = row.getFarmId();
                Long existing = farmByUser.get(row.getUserId());
                if (existing == null || (minFarmId && candidate < existing)) {
                    farmByUser.put(row.getUserId(), candidate);
                }
            }
        }
        for (Map.Entry<Long, Recipient> entry : byUser.entrySet()) {
            Recipient recipient = entry.getValue();
            String role = roleByUser.getOrDefault(entry.getKey(), "NONE");
            Long farmId = farmScope ? farmByUser.get(entry.getKey()) : null;
            entry.setValue(new Recipient(recipient.userId(), recipient.userName(), role, farmId));
        }
        return new Result(new ArrayList<>(byUser.values()), farms.size());
    }

    /** 校验接收范围（写接口用），返回清洗后的值由调用方写入 t_notice_target */
    public void validate(String scope, List<String> roles, List<String> provinceCodes, List<Long> farmIds) {
        if (!Set.of("ALL", "CONDITION", "FARM").contains(scope)) {
            throw ContentErrors.field("scope_type", "请选择接收范围");
        }
        if ("CONDITION".equals(scope)) {
            if (roles.isEmpty()) {
                throw ContentErrors.field("roles", "至少选择一个角色");
            }
            for (String role : roles) {
                if (!ROLES.contains(role)) {
                    throw ContentErrors.field("roles", "角色不正确");
                }
            }
        }
        if ("FARM".equals(scope)) {
            if (farmIds.isEmpty()) {
                throw ContentErrors.field("farm_ids", "请选择至少一个养殖场");
            }
            if (farmIds.size() > 200) {
                throw ContentErrors.field("farm_ids", "一次最多选择 200 个养殖场");
            }
        }
        if (provinceCodes.size() > 34) {
            throw ContentErrors.field("province_codes", "地区最多选择 34 个");
        }
        for (String code : provinceCodes) {
            if (code == null || !code.matches("\\d{6}")) {
                throw ContentErrors.field("province_codes", "地区编码不正确");
            }
        }
    }

    public List<String> cleanRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream().filter(StringUtils::hasText).map(s -> s.trim().toUpperCase())
                .distinct().toList();
    }

    public List<String> cleanStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    public List<Long> cleanIds(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    /** 接收条件文字：角色 + 地区 + 品类，用于列表“接收范围” */
    public String conditionText(List<String> roles, List<String> provinceCodes, List<Long> categoryIds) {
        List<String> parts = new ArrayList<>();
        if (roles != null && !roles.isEmpty()) {
            List<String> roleTexts = roles.stream().map(ContentTexts::noticeRole).toList();
            parts.add(String.join("、", roleTexts));
        }
        if (provinceCodes != null && !provinceCodes.isEmpty()) {
            List<String> names = provinceCodes.stream().map(ContentTexts::provinceName).toList();
            parts.add(String.join("、", names));
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            List<NameRow> names = lookupMapper.selectCategoryNames(categoryIds);
            parts.add(String.join("、", names.stream().map(NameRow::getName).toList()));
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /** 养殖场名称（详情回填） */
    public List<NoticeVos.FarmBrief> farms(List<Long> farmIds) {
        if (farmIds == null || farmIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<NoticeVos.FarmBrief> result = new ArrayList<>();
        for (var farm : audienceMapper.selectFarms(farmIds)) {
            NoticeVos.FarmBrief brief = new NoticeVos.FarmBrief();
            brief.setFarmId(farm.getFarmId());
            brief.setName(farm.getName());
            result.add(brief);
        }
        result.sort(Comparator.comparing(NoticeVos.FarmBrief::getFarmId));
        return result;
    }

    public List<NoticeVos.FarmBrief> searchFarms(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<NoticeVos.FarmBrief> result = new ArrayList<>();
        for (var farm : audienceMapper.searchFarms(keyword.trim())) {
            NoticeVos.FarmBrief brief = new NoticeVos.FarmBrief();
            brief.setFarmId(farm.getFarmId());
            brief.setName(farm.getName());
            result.add(brief);
        }
        return result;
    }

    private static int rank(String role) {
        return ROLE_RANK.getOrDefault(role == null ? "NONE" : role, 0);
    }

    private static Long toLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    /** t_notice_target 中 role/province 之外的值顺序稳定 */
    public Set<String> distinct(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                set.add(value.trim());
            }
        }
        return set;
    }
}

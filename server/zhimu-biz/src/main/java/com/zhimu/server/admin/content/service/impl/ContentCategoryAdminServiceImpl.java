package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.request.CategoryOrderRequest;
import com.zhimu.server.admin.content.request.ContentCategorySaveRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.vo.ContentCategoryVO;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.CourseCategoryMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 分类（ADM-C40–C43）。
 */
@Service
@RequiredArgsConstructor
public class ContentCategoryAdminServiceImpl implements ContentCategoryAdminService {

    private final CourseCategoryMapper categoryMapper;
    private final CourseMapper courseMapper;
    private final CourseTopicMapper topicMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final ContentLogs logs;

    @Override
    public List<ContentCategoryVO> list(String status) {
        var wrapper = Wrappers.<CourseCategory>lambdaQuery().orderByAsc(CourseCategory::getSortNo)
                .orderByAsc(CourseCategory::getId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(CourseCategory::getStatus, status.toUpperCase());
        }
        List<CourseCategory> rows = categoryMapper.selectList(wrapper);
        Map<Long, Integer> courseCounts = counts(true);
        Map<Long, Integer> knowledgeCounts = counts(false);
        List<ContentCategoryVO> result = new ArrayList<>();
        for (CourseCategory row : rows) {
            result.add(toVO(row, courseCounts.getOrDefault(row.getId(), 0),
                    knowledgeCounts.getOrDefault(row.getId(), 0)));
        }
        return result;
    }

    private Map<Long, Integer> counts(boolean course) {
        Map<Long, Integer> map = new LinkedHashMap<>();
        if (course) {
            for (Course row : courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                    .select(Course::getCategoryId))) {
                if (row.getCategoryId() != null) {
                    map.merge(row.getCategoryId(), 1, Integer::sum);
                }
            }
        } else {
            for (AiKnowledge row : knowledgeMapper.selectList(Wrappers.<AiKnowledge>lambdaQuery()
                    .select(AiKnowledge::getCategoryId))) {
                if (row.getCategoryId() != null) {
                    map.merge(row.getCategoryId(), 1, Integer::sum);
                }
            }
        }
        return map;
    }

    @Override
    @Transactional
    public ContentCategoryVO create(ContentCategorySaveRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        ensureNameFree(name, null);
        CourseCategory entity = new CourseCategory();
        entity.setName(name);
        entity.setStatus(request.getStatus() == null ? "ENABLED" : normalizeStatus(request.getStatus()));
        entity.setSortNo(request.getSortNo() == null ? nextSortNo() : request.getSortNo());
        entity.setUpdatedBy(AdminContext.adminId());
        entity.setVersion(0);
        categoryMapper.insert(entity);
        writeLog("CATEGORY_CREATE", entity.getId(), name, null);
        return toVO(entity, 0, 0);
    }

    @Override
    @Transactional
    public ContentCategoryVO update(Long id, ContentCategorySaveRequest request) {
        CourseCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw ContentErrors.notFound();
        }
        if (request.getVersion() != null && !Objects.equals(request.getVersion(), entity.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        String name = request.getName() == null ? entity.getName() : request.getName().trim();
        ensureNameFree(name, id);
        String status = request.getStatus() == null ? entity.getStatus() : normalizeStatus(request.getStatus());
        if ("DISABLED".equals(status) && !"DISABLED".equals(entity.getStatus())) {
            int courses = countCourses(id);
            int knowledge = countKnowledge(id);
            if (courses + knowledge > 0) {
                throw ContentErrors.stateChanged("分类下还有 " + courses + " 个课程和 " + knowledge
                        + " 条知识，请先改到其他分类");
            }
        }
        boolean renamed = !Objects.equals(name, entity.getName());
        String oldStatus = entity.getStatus();
        entity.setName(name);
        entity.setStatus(status);
        if (request.getSortNo() != null) {
            entity.setSortNo(request.getSortNo());
        }
        entity.setUpdatedBy(AdminContext.adminId());
        if (categoryMapper.updateById(entity) == 0) {
            throw ContentErrors.versionConflict();
        }
        if (renamed) {
            rename(id, name);
        }
        String action = !"DISABLED".equals(oldStatus) && "DISABLED".equals(status)
                ? "CATEGORY_DISABLE" : "CATEGORY_UPDATE";
        writeLog(action, id, name, null);
        return toVO(entity, countCourses(id), countKnowledge(id));
    }

    @Override
    @Transactional
    public List<ContentCategoryVO> reorder(CategoryOrderRequest request) {
        List<CourseCategory> all = categoryMapper.selectList(Wrappers.<CourseCategory>lambdaQuery()
                .orderByAsc(CourseCategory::getSortNo).orderByAsc(CourseCategory::getId));
        Set<Long> existing = new HashSet<>();
        all.forEach(row -> existing.add(row.getId()));
        Set<Long> given = new HashSet<>(request.getIds());
        if (!existing.equals(given)) {
            throw ContentErrors.param("分类列表已变化，请刷新后重试");
        }
        int sort = 1;
        for (Long id : request.getIds()) {
            categoryMapper.update(null, Wrappers.<CourseCategory>lambdaUpdate()
                    .eq(CourseCategory::getId, id)
                    .set(CourseCategory::getSortNo, sort++)
                    .set(CourseCategory::getUpdatedAt, BeijingTime.now())
                    .set(CourseCategory::getUpdatedBy, AdminContext.adminId()));
        }
        writeLog("CATEGORY_ORDER", null, null, "分类顺序：" + request.getIds());
        return list(null);
    }

    @Override
    public CourseCategory requireEnabled(Long id) {
        if (id == null) {
            throw ContentErrors.field("category_id", "请选择分类");
        }
        CourseCategory category = categoryMapper.selectById(id);
        if (category == null || !"ENABLED".equals(category.getStatus())) {
            throw ContentErrors.field("category_id", "请选择启用的分类");
        }
        return category;
    }

    @Override
    public String nameOf(Long id) {
        if (id == null) {
            return null;
        }
        CourseCategory category = categoryMapper.selectById(id);
        return category == null ? null : category.getName();
    }

    @Override
    public void rename(Long categoryId, String name) {
        courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                .eq(Course::getCategoryId, categoryId)
                .set(Course::getCategoryName, name)
                .set(Course::getUpdatedAt, BeijingTime.now()));
        topicMapper.update(null, Wrappers.<CourseTopic>lambdaUpdate()
                .eq(CourseTopic::getCategoryId, categoryId)
                .set(CourseTopic::getCategoryName, name)
                .set(CourseTopic::getUpdatedAt, BeijingTime.now()));
        knowledgeMapper.update(null, Wrappers.<AiKnowledge>lambdaUpdate()
                .eq(AiKnowledge::getCategoryId, categoryId)
                .set(AiKnowledge::getCategoryName, name)
                .set(AiKnowledge::getUpdatedAt, BeijingTime.now()));
    }

    private int countCourses(Long categoryId) {
        return Math.toIntExact(courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCategoryId, categoryId)));
    }

    private int countKnowledge(Long categoryId) {
        return Math.toIntExact(knowledgeMapper.selectCount(Wrappers.<AiKnowledge>lambdaQuery()
                .eq(AiKnowledge::getCategoryId, categoryId)));
    }

    private void ensureNameFree(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw ContentErrors.field("name", "请填写分类名称");
        }
        var wrapper = Wrappers.<CourseCategory>lambdaQuery().eq(CourseCategory::getName, name);
        if (excludeId != null) {
            wrapper.ne(CourseCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw ContentErrors.duplicate("已有同名分类");
        }
    }

    private int nextSortNo() {
        List<CourseCategory> all = categoryMapper.selectList(Wrappers.<CourseCategory>lambdaQuery()
                .orderByDesc(CourseCategory::getSortNo).last("LIMIT 1"));
        return all.isEmpty() || all.get(0).getSortNo() == null ? 1 : all.get(0).getSortNo() + 1;
    }

    private static String normalizeStatus(String status) {
        String value = status.toUpperCase();
        if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) {
            throw ContentErrors.field("status", "状态不正确");
        }
        return value;
    }

    private ContentCategoryVO toVO(CourseCategory entity, int courseCount, int knowledgeCount) {
        ContentCategoryVO vo = new ContentCategoryVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSortNo(entity.getSortNo());
        vo.setStatus(entity.getStatus());
        vo.setStatusText("ENABLED".equals(entity.getStatus()) ? "启用" : "停用");
        vo.setCourseCount(courseCount);
        vo.setKnowledgeCount(knowledgeCount);
        vo.setVersion(entity.getVersion());
        return vo;
    }

    private void writeLog(String action, Long bizId, String targetName, String extra) {
        logs.record(OperationLogEntry.builder()
                .opType("COURSE")
                .opAction(action)
                .targetName(targetName)
                .bizId(bizId)
                .extraText(extra)
                .build());
    }
}

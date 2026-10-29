package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.request.CourseTopicSaveRequest;
import com.zhimu.server.admin.content.request.TopicStatusRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.service.CourseTopicAdminService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentMediaSupport;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.CourseTopicVO;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.FileScene;
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

/** 课程专题（ADM-C36–C39）。 */
@Service
@RequiredArgsConstructor
public class CourseTopicAdminServiceImpl implements CourseTopicAdminService {

    private final CourseTopicMapper topicMapper;
    private final CourseMapper courseMapper;
    private final ContentCategoryAdminService categoryService;
    private final ContentMediaSupport media;
    private final ContentLogs logs;

    @Override
    public List<CourseTopicVO> list(String status) {
        var wrapper = Wrappers.<CourseTopic>lambdaQuery().orderByAsc(CourseTopic::getSortNo)
                .orderByAsc(CourseTopic::getId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(CourseTopic::getStatus, status.toUpperCase());
        }
        List<CourseTopicVO> result = new ArrayList<>();
        for (CourseTopic topic : topicMapper.selectList(wrapper)) {
            result.add(toVO(topic, courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                    .eq(Course::getTopicId, topic.getId())
                    .orderByAsc(Course::getSectionNo).orderByAsc(Course::getId))));
        }
        return result;
    }

    @Override
    @Transactional
    public CourseTopicVO create(CourseTopicSaveRequest request) {
        String name = nameOf(request);
        ensureNameFree(name, null);
        CourseCategory category = categoryService.requireEnabled(request.getCategoryId());
        FileEntity cover = request.getCoverFileId() == null ? null
                : media.requireCover(request.getCoverFileId(), FileScene.TOPIC_COVER);
        CourseTopic topic = new CourseTopic();
        topic.setName(name);
        topic.setCategoryId(category.getId());
        topic.setCategoryName(category.getName());
        topic.setIntro(blankToNull(request.getIntro()));
        topic.setCoverFileId(cover == null ? null : cover.getId());
        topic.setCoverUrl(cover == null ? null : media.publicUrl(cover));
        // 新建专题默认未上架：编排好至少 1 节已上架课程后再手动上架
        topic.setStatus("OFF_SHELF");
        topic.setSortNo(request.getSortNo() == null ? nextSortNo() : request.getSortNo());
        topic.setUpdatedBy(AdminContext.requireAdminId());
        topic.setVersion(0);
        topicMapper.insert(topic);
        applyCourses(topic, request.getCourses());
        media.markBound(topic.getCoverFileId(), true);
        writeLog("TOPIC_CREATE", topic.getId(), name, null, null, null);
        return toVO(topic, coursesOf(topic.getId()));
    }

    @Override
    @Transactional
    public CourseTopicVO update(Long id, CourseTopicSaveRequest request) {
        CourseTopic topic = requireTopic(id);
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), topic.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        String name = nameOf(request);
        ensureNameFree(name, id);
        CourseCategory category = categoryService.requireEnabled(request.getCategoryId());
        FileEntity cover = request.getCoverFileId() == null ? null
                : media.requireCover(request.getCoverFileId(), FileScene.TOPIC_COVER);
        Map<String, Object> before = snapshot(topic);
        Long oldCover = topic.getCoverFileId();
        topic.setName(name);
        topic.setCategoryId(category.getId());
        topic.setCategoryName(category.getName());
        topic.setIntro(blankToNull(request.getIntro()));
        if (cover != null) {
            topic.setCoverFileId(cover.getId());
            topic.setCoverUrl(media.publicUrl(cover));
        }
        if (request.getSortNo() != null) {
            topic.setSortNo(request.getSortNo());
        }
        topic.setUpdatedBy(AdminContext.requireAdminId());
        if (topicMapper.updateById(topic) == 0) {
            throw ContentErrors.versionConflict();
        }
        applyCourses(topic, request.getCourses());
        if (cover != null && !Objects.equals(oldCover, cover.getId())) {
            media.markBound(oldCover, false);
            media.markBound(cover.getId(), true);
        }
        writeLog("TOPIC_UPDATE", id, name, null, before, snapshot(topic));
        return toVO(topic, coursesOf(id));
    }

    @Override
    @Transactional
    public CourseTopicVO changeStatus(Long id, TopicStatusRequest request) {
        CourseTopic topic = requireTopic(id);
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), topic.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        String status = request.getStatus().toUpperCase();
        if (!"ON_SHELF".equals(status) && !"OFF_SHELF".equals(status)) {
            throw ContentErrors.field("status", "状态不正确");
        }
        String old = topic.getStatus();
        if (status.equals(old)) {
            throw ContentErrors.stateChanged("专题状态未变化，列表已刷新");
        }
        if ("OFF_SHELF".equals(status)) {
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw ContentErrors.reasonEmpty("请填写下架原因");
            }
            topic.setOffReason(request.getReason().trim());
        } else {
            if (onShelfCourseCount(id) == 0) {
                throw ContentErrors.stateChanged("专题里还没有已上架的课程，请先编排至少 1 节已上架课程再上架");
            }
            topic.setOffReason(null);
        }
        topic.setStatus(status);
        topic.setUpdatedBy(AdminContext.requireAdminId());
        if (topicMapper.updateById(topic) == 0) {
            throw ContentErrors.versionConflict();
        }
        if ("ON_SHELF".equals(status)) {
            // updateById 跳过 null，下架原因要显式清空
            topicMapper.update(null, Wrappers.<CourseTopic>lambdaUpdate()
                    .eq(CourseTopic::getId, id)
                    .set(CourseTopic::getOffReason, null));
        }
        writeLog("OFF_SHELF".equals(status) ? "TOPIC_OFF_SHELF" : "TOPIC_ON_SHELF", id, topic.getName(),
                "OFF_SHELF".equals(status) ? request.getReason() : null, Map.of("status", old),
                Map.of("status", status));
        return toVO(topic, coursesOf(id));
    }

    /** 按提交顺序重写节次 1..n；从其他专题移入的课程报 40001。 */
    private void applyCourses(CourseTopic topic, List<CourseTopicSaveRequest.TopicCourseRequest> courses) {
        List<CourseTopicSaveRequest.TopicCourseRequest> list = courses == null ? List.of() : courses;
        Set<Long> keep = new HashSet<>();
        int section = 1;
        for (CourseTopicSaveRequest.TopicCourseRequest item : list) {
            Course course = courseMapper.selectById(item.getCourseId());
            if (course == null) {
                throw ContentErrors.field("courses", "课程不存在或已删除");
            }
            if (course.getTopicId() != null && !Objects.equals(course.getTopicId(), topic.getId())) {
                throw ContentErrors.field("courses", "课程已属于其他专题");
            }
            if (!Objects.equals(course.getTopicId(), topic.getId()) && !"ON_SHELF".equals(course.getStatus())) {
                throw ContentErrors.field("courses", "只能编排已上架的课程：“" + course.getTitle() + "”还没有上架");
            }
            keep.add(course.getId());
            course.setTopicId(topic.getId());
            course.setTopicName(topic.getName());
            course.setSectionNo(section++);
            course.setUpdatedBy(AdminContext.requireAdminId());
            courseMapper.updateById(course);
        }
        for (Course course : courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTopicId, topic.getId()))) {
            if (!keep.contains(course.getId())) {
                courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                        .eq(Course::getId, course.getId())
                        .set(Course::getTopicId, null)
                        .set(Course::getTopicName, null)
                        .set(Course::getSectionNo, null)
                        .set(Course::getUpdatedBy, AdminContext.requireAdminId()));
            }
        }
    }

    private long onShelfCourseCount(Long topicId) {
        Long count = courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTopicId, topicId)
                .eq(Course::getStatus, "ON_SHELF"));
        return count == null ? 0 : count;
    }

    private List<Course> coursesOf(Long topicId) {
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTopicId, topicId)
                .orderByAsc(Course::getSectionNo).orderByAsc(Course::getId));
    }

    private CourseTopic requireTopic(Long id) {
        CourseTopic topic = id == null ? null : topicMapper.selectById(id);
        if (topic == null) {
            throw ContentErrors.notFound();
        }
        return topic;
    }

    private String nameOf(CourseTopicSaveRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw ContentErrors.field("name", "请填写专题名称");
        }
        if (name.length() > 20) {
            throw ContentErrors.field("name", "专题名称不能超过 20 字");
        }
        return name;
    }

    private void ensureNameFree(String name, Long excludeId) {
        var wrapper = Wrappers.<CourseTopic>lambdaQuery().eq(CourseTopic::getName, name);
        if (excludeId != null) {
            wrapper.ne(CourseTopic::getId, excludeId);
        }
        if (topicMapper.selectCount(wrapper) > 0) {
            throw ContentErrors.duplicate("已有同名专题");
        }
    }

    private int nextSortNo() {
        List<CourseTopic> last = topicMapper.selectList(Wrappers.<CourseTopic>lambdaQuery()
                .orderByDesc(CourseTopic::getSortNo).last("LIMIT 1"));
        return last.isEmpty() || last.get(0).getSortNo() == null ? 1 : last.get(0).getSortNo() + 1;
    }

    private CourseTopicVO toVO(CourseTopic topic, List<Course> courses) {
        CourseTopicVO vo = new CourseTopicVO();
        vo.setId(topic.getId());
        vo.setName(topic.getName());
        vo.setCategoryId(topic.getCategoryId());
        vo.setCategoryName(topic.getCategoryName());
        vo.setIntro(topic.getIntro());
        vo.setCoverFileId(topic.getCoverFileId());
        vo.setCoverUrl(media.coverUrl(topic));
        vo.setStatus(topic.getStatus());
        vo.setStatusText("OFF_SHELF".equals(topic.getStatus())
                && (topic.getOffReason() == null || topic.getOffReason().isBlank())
                ? "未上架" : ContentTexts.contentStatus(topic.getStatus()));
        vo.setOffReason(topic.getOffReason());
        vo.setSortNo(topic.getSortNo());
        vo.setVersion(topic.getVersion());
        vo.setUpdatedAt(topic.getUpdatedAt());
        int duration = 0;
        int onShelf = 0;
        for (Course course : courses) {
            CourseTopicVO.TopicCourseVO item = new CourseTopicVO.TopicCourseVO();
            item.setCourseId(course.getId());
            item.setTitle(course.getTitle());
            item.setSectionNo(course.getSectionNo());
            item.setDurationSec(course.getDurationSec());
            item.setStatus(course.getStatus());
            vo.getCourses().add(item);
            if ("ON_SHELF".equals(course.getStatus())) {
                onShelf++;
                duration += course.getDurationSec() == null ? 0 : course.getDurationSec();
            }
        }
        vo.setCourseCount(onShelf);
        vo.setTotalDurationSec(duration);
        vo.setDurationText(ContentTexts.durationText(duration));
        return vo;
    }

    private Map<String, Object> snapshot(CourseTopic topic) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", topic.getName());
        map.put("category_id", topic.getCategoryId());
        map.put("status", topic.getStatus());
        map.put("sort_no", topic.getSortNo());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void writeLog(String action, Long id, String name, String reason, Object before, Object after) {
        Map<String, Object> beforeFields = new LinkedHashMap<>();
        Map<String, Object> afterFields = new LinkedHashMap<>();
        if (before instanceof Map<?, ?> map) {
            map.forEach((k, v) -> beforeFields.put(String.valueOf(k), v));
        }
        if (after instanceof Map<?, ?> map) {
            map.forEach((k, v) -> afterFields.put(String.valueOf(k), v));
        }
        logs.record(OperationLogEntry.builder()
                .opType("COURSE")
                .opAction(action)
                .targetName(name)
                .bizId(id)
                .reason(reason)
                .before(beforeFields.isEmpty() ? null : beforeFields)
                .after(afterFields.isEmpty() ? null : afterFields)
                .build());
    }
}

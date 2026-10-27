package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.mapper.ContentApplicableMapper;
import com.zhimu.server.admin.content.mapper.ContentStatMapper;
import com.zhimu.server.admin.content.query.AdminCourseQuery;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentMediaSupport;
import com.zhimu.server.admin.content.support.ContentNames;
import com.zhimu.server.admin.content.vo.CourseRowVO;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.CourseCategoryMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CourseMediaMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.service.TranscodeService;
import com.zhimu.server.common.api.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercise list/count query contracts using fixture rows and their actual bound filter values. */
class CourseAdminPageCountsTest {
    private final List<Course> courses = new ArrayList<>();
    private CourseAdminServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ContentTableInfo.ensure();
        CourseMapper courseMapper = mock(CourseMapper.class);
        CourseMediaMapper mediaMapper = mock(CourseMediaMapper.class);
        ContentStatMapper stats = mock(ContentStatMapper.class);
        ContentMediaSupport media = mock(ContentMediaSupport.class);
        ContentNames names = mock(ContentNames.class);
        when(names.adminNames(anyList())).thenReturn(Map.of());
        when(media.variantMap(any())).thenReturn(Map.of());
        when(mediaMapper.selectPlayableByCourseIds(anyList())).thenReturn(List.of());
        when(stats.selectPlayAggAll()).thenReturn(List.of());
        when(stats.selectFinishAgg()).thenReturn(List.of());
        when(courseMapper.selectCount(any())).thenAnswer(call -> (long) matching(call.getArgument(0)).size());
        when(courseMapper.selectPage(any(Page.class), any())).thenAnswer(call -> {
            Page<Course> page = call.getArgument(0);
            List<Course> rows = matching(call.getArgument(1));
            page.setTotal(rows.size());
            int from = (int) Math.min(rows.size(), (page.getCurrent() - 1) * page.getSize());
            int to = (int) Math.min(rows.size(), from + page.getSize());
            page.setRecords(rows.subList(from, to));
            return page;
        });
        service = new CourseAdminServiceImpl(courseMapper, mock(CourseTopicMapper.class),
                mock(CourseCategoryMapper.class), mediaMapper, mock(AiKnowledgeMapper.class),
                mock(FileMapper.class), mock(ContentApplicableMapper.class), stats,
                mock(ContentCategoryAdminService.class), media, names, mock(ContentLogs.class),
                mock(TranscodeService.class));
    }

    @Test
    void switchingFromAllToEmptyOffShelfDoesNotEraseOtherTabCounts() {
        for (long id = 1; id <= 13; id++) courses.add(course(id, "ON_SHELF", 10, 100, "护理课程 " + id, "护理专题", "SUCCESS"));
        courses.add(course(14, "DRAFT", 10, 100, "护理草稿", "护理专题", "WAITING"));
        assertStatusSequence(new AdminCourseQuery(), Map.of("ALL", 14, "ON_SHELF", 13, "OFF_SHELF", 0, "DRAFT", 1));
    }

    @Test
    void categoryTopicAndTrimmedKeywordRemainSharedByEveryStatusCount() {
        filteredFixture();
        AdminCourseQuery query = new AdminCourseQuery();
        query.setCategoryId(10L);
        query.setTopicId(100L);
        query.setKeyword("  育雏  ");
        assertStatusSequence(query, Map.of("ALL", 4, "ON_SHELF", 2, "OFF_SHELF", 1, "DRAFT", 1));
    }

    @Test
    void individualFiltersDoNotLeakCountsFromOtherCategoriesTopicsOrKeywords() {
        filteredFixture();
        AdminCourseQuery category = new AdminCourseQuery();
        category.setCategoryId(10L);
        assertStatusSequence(category, Map.of("ALL", 6, "ON_SHELF", 3, "OFF_SHELF", 1, "DRAFT", 2));
        AdminCourseQuery topic = new AdminCourseQuery();
        topic.setTopicId(100L);
        assertStatusSequence(topic, Map.of("ALL", 6, "ON_SHELF", 4, "OFF_SHELF", 1, "DRAFT", 1));
        AdminCourseQuery keyword = new AdminCourseQuery();
        keyword.setKeyword("育雏");
        assertStatusSequence(keyword, Map.of("ALL", 6, "ON_SHELF", 3, "OFF_SHELF", 1, "DRAFT", 2));
    }

    @Test
    void transcodeFilterIsRetainedWhileSelectedPublicationStatusIsIgnoredForCounts() {
        filteredFixture();
        AdminCourseQuery query = new AdminCourseQuery();
        query.setCategoryId(10L);
        query.setTopicId(100L);
        query.setKeyword("育雏");
        query.setTranscodeStatus("SUCCESS");
        assertStatusSequence(query, Map.of("ALL", 3, "ON_SHELF", 2, "OFF_SHELF", 1, "DRAFT", 0));
    }

    @Test
    void tabCountsAreNotDerivedFromTheCurrentPageOrMutateTheRequestedStatus() {
        for (long id = 1; id <= 13; id++) courses.add(course(id, "ON_SHELF", 10, 100, "护理课程 " + id, "护理专题", "SUCCESS"));
        courses.add(course(14, "DRAFT", 10, 100, "护理草稿", "护理专题", "WAITING"));
        AdminCourseQuery query = new AdminCourseQuery();
        query.setStatus("on_shelf");
        query.setPage(2);
        query.setSize(5);
        PageVO<CourseRowVO> result = service.page(query);
        assertThat(result.getList()).hasSize(5).allMatch(row -> "ON_SHELF".equals(row.getStatus()));
        assertThat(result.getTotal()).isEqualTo(13);
        assertThat(result.getTabCounts()).containsEntry("ALL", 14).containsEntry("DRAFT", 1);
        assertThat(query.getStatus()).isEqualTo("on_shelf");
    }

    private void assertStatusSequence(AdminCourseQuery query, Map<String, Integer> counts) {
        for (String status : List.of("ALL", "OFF_SHELF", "ON_SHELF", "DRAFT")) {
            query.setStatus(status);
            PageVO<CourseRowVO> result = service.page(query);
            assertThat(result.getTabCounts()).as("tab counts while viewing %s", status).isEqualTo(counts);
            assertThat(result.getTotal()).as("selected tab total for %s", status).isEqualTo(counts.get(status).longValue());
            assertThat(result.getList()).hasSize(Math.min(query.sizeOrDefault(), counts.get(status)));
            if (!"ALL".equals(status)) {
                assertThat(result.getList()).allMatch(row -> status.equals(row.getStatus()));
            }
            assertThat(query.getStatus()).isEqualTo(status);
        }
    }

    private void filteredFixture() {
        courses.add(course(1, "ON_SHELF", 10, 100, "育雏温度", "科学养殖", "SUCCESS"));
        courses.add(course(2, "DRAFT", 10, 100, "育雏草稿", "科学养殖", "WAITING"));
        courses.add(course(3, "OFF_SHELF", 10, 100, "育雏旧课", "科学养殖", "SUCCESS"));
        courses.add(course(4, "ON_SHELF", 20, 100, "育雏分类外", "科学养殖", "SUCCESS"));
        courses.add(course(5, "DRAFT", 10, 200, "育雏专题外", "科学养殖", "SUCCESS"));
        courses.add(course(6, "ON_SHELF", 10, 100, "其他课程", "其他专题", "SUCCESS"));
        courses.add(course(7, "ON_SHELF", 10, 100, "课程七", "育雏专题", "SUCCESS"));
    }

    private Course course(long id, String status, long categoryId, long topicId, String title, String topicName, String transcode) {
        Course course = new Course();
        course.setId(id);
        course.setCreatedBy(1L);
        course.setUpdatedBy(1L);
        course.setStatus(status);
        course.setCategoryId(categoryId);
        course.setTopicId(topicId);
        course.setTitle(title);
        course.setTopicName(topicName);
        course.setTranscodeStatus(transcode);
        course.setUpdatedAt(LocalDateTime.of(2026, 10, 26, 10, 0));
        return course;
    }

    private List<Course> matching(LambdaQueryWrapper<Course> query) {
        // This mapper fixture consumes generated bindings, including duplicate contradictory status predicates.
        query.getSqlSegment();
        List<Object> statuses = bindings(query, "status", "=");
        List<Object> categories = bindings(query, "category_id", "=");
        List<Object> topics = bindings(query, "topic_id", "=");
        List<Object> transcodes = bindings(query, "transcode_status", "=");
        List<Object> titleKeywords = bindings(query, "title", "LIKE");
        List<Object> topicKeywords = bindings(query, "topic_name", "LIKE");
        return courses.stream().filter(course -> same(statuses, course.getStatus())
                && same(categories, course.getCategoryId()) && same(topics, course.getTopicId())
                && same(transcodes, course.getTranscodeStatus())
                && ((titleKeywords.isEmpty() && topicKeywords.isEmpty())
                || like(titleKeywords, course.getTitle()) || like(topicKeywords, course.getTopicName()))).toList();
    }

    private List<Object> bindings(LambdaQueryWrapper<Course> query, String field, String operator) {
        Pattern pattern = Pattern.compile("\\b" + field + "\\s+" + Pattern.quote(operator)
                + "\\s+#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");
        Matcher matcher = pattern.matcher(query.getSqlSegment());
        List<Object> values = new ArrayList<>();
        while (matcher.find()) values.add(query.getParamNameValuePairs().get(matcher.group(1)));
        return values;
    }

    private boolean same(List<Object> expected, Object actual) {
        return expected.stream().allMatch(value -> Objects.equals(value, actual));
    }

    private boolean like(List<Object> expected, String actual) {
        return actual != null && expected.stream().anyMatch(value -> actual.contains(value.toString().replace("%", "")));
    }
}

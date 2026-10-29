package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.request.CourseTopicSaveRequest;
import com.zhimu.server.admin.content.request.TopicStatusRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentMediaSupport;
import com.zhimu.server.admin.content.vo.CourseTopicVO;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M16 专题上下架规则；清空专题简介要真正写 null。 */
class CourseTopicAdminServiceImplTest {

    private final CourseTopicMapper topicMapper = mock(CourseTopicMapper.class);
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final ContentCategoryAdminService categoryService = mock(ContentCategoryAdminService.class);
    private final ContentLogs logs = mock(ContentLogs.class);
    private CourseTopicAdminServiceImpl service;
    private CourseTopic topic;

    @BeforeEach
    void setUp() {
        ContentTableInfo.ensure();
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        service = new CourseTopicAdminServiceImpl(topicMapper, courseMapper, categoryService,
                mock(ContentMediaSupport.class), logs);
        CourseCategory category = new CourseCategory();
        category.setId(5101L);
        category.setName("饲养管理");
        when(categoryService.requireEnabled(5101L)).thenReturn(category);
        topic = new CourseTopic();
        topic.setId(7001L);
        topic.setName("母猪产前护理");
        topic.setCategoryId(5101L);
        topic.setIntro("旧简介");
        topic.setStatus("OFF_SHELF");
        topic.setVersion(3);
        when(topicMapper.selectById(7001L)).thenReturn(topic);
        when(topicMapper.selectCount(any())).thenReturn(0L);
        when(topicMapper.updateById(any(CourseTopic.class))).thenReturn(1);
        when(topicMapper.selectList(any())).thenReturn(List.of());
        when(courseMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void createDefaultsToNotOnShelf() {
        CourseTopicSaveRequest request = new CourseTopicSaveRequest();
        request.setName("新专题");
        request.setCategoryId(5101L);

        CourseTopicVO vo = service.create(request);

        assertThat(vo.getStatus()).isEqualTo("OFF_SHELF");
        assertThat(vo.getStatusText()).isEqualTo("未上架");
    }

    @Test
    void onShelfRequiresPublishedCourse() {
        when(courseMapper.selectCount(any())).thenReturn(0L);
        TopicStatusRequest request = new TopicStatusRequest();
        request.setStatus("ON_SHELF");
        request.setVersion(3);

        assertThatThrownBy(() -> service.changeStatus(7001L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("还没有已上架的课程");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateWithBlankIntroWritesNull() {
        CourseTopicSaveRequest request = new CourseTopicSaveRequest();
        request.setName("母猪产前护理");
        request.setCategoryId(5101L);
        request.setIntro("  ");
        request.setVersion(3);

        service.update(7001L, request);

        ArgumentCaptor<Wrapper<CourseTopic>> sets = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(topicMapper, atLeastOnce()).update(isNull(), sets.capture());
        assertThat(sets.getAllValues()).anySatisfy(w -> assertThat(w.getSqlSet()).contains("intro"));
    }

    @Test
    void updateLogKeepsOnlyChangedFieldsWithCategoryName() {
        topic.setCategoryName("饲养管理");
        topic.setSortNo(3);
        when(categoryService.nameOf(5101L)).thenReturn("饲养管理");
        CourseTopicSaveRequest request = new CourseTopicSaveRequest();
        request.setName("母猪产前护理");
        request.setCategoryId(5101L);
        request.setIntro("新简介");
        request.setVersion(3);

        service.update(7001L, request);

        ArgumentCaptor<OperationLogEntry> entry = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(logs).record(entry.capture());
        assertThat(entry.getValue().getOpAction()).isEqualTo("TOPIC_UPDATE");
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) entry.getValue().getBefore();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) entry.getValue().getAfter();
        assertThat(before).containsOnlyKeys("intro")
                .containsEntry("intro", Map.of("name", "简介", "display", "旧简介"));
        assertThat(after).containsOnlyKeys("intro")
                .containsEntry("intro", Map.of("name", "简介", "display", "新简介"));
    }
}

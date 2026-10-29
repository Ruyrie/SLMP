package com.zhimu.server.admin.content.service.impl;

import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.entity.KnowledgeVersion;
import com.zhimu.server.admin.content.entity.ContentApplicable;
import com.zhimu.server.admin.content.mapper.ContentApplicableMapper;
import com.zhimu.server.admin.content.mapper.ContentStatMapper;
import com.zhimu.server.admin.content.mapper.KnowledgeTaskMapper;
import com.zhimu.server.admin.content.mapper.KnowledgeVersionMapper;
import com.zhimu.server.admin.content.request.KnowledgePublishRequest;
import com.zhimu.server.admin.content.request.KnowledgeRollbackRequest;
import com.zhimu.server.admin.content.request.KnowledgeSaveRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.service.KnowledgeParagraphService;
import com.zhimu.server.admin.content.service.KnowledgeTaskService;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentNames;
import com.zhimu.server.admin.content.support.KnowledgeHtml;
import com.zhimu.server.admin.content.vo.KnowledgeVO;
import com.zhimu.server.admin.content.vo.StatAggregates;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeParagraphMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADM-C48 发布、ADM-C51 回退、版本冲突与没有草稿。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeAdminServiceImplTest {

    @Mock private AiKnowledgeMapper knowledgeMapper;
    @Mock private AiKnowledgeParagraphMapper paragraphMapper;
    @Mock private KnowledgeVersionMapper versionMapper;
    @Mock private KnowledgeTaskMapper taskMapper;
    @Mock private ContentApplicableMapper applicableMapper;
    @Mock private ContentStatMapper statMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private ContentCategoryAdminService categoryService;
    @Mock private KnowledgeParagraphService paragraphService;
    @Mock private KnowledgeTaskService taskService;
    @Mock private ContentNames names;
    @Mock private ContentLogs logs;

    @InjectMocks private KnowledgeAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        ContentTableInfo.ensure();
        ReflectionTestUtils.setField(service, "frequentCiteThreshold", 50);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "郑琳", Set.of("OPERATOR"), Set.of(), 0, "t"));
        lenient().when(categoryService.nameOf(any())).thenReturn("用药规范");
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    private KnowledgeSaveRequest createRequest(Long applicableId) {
        CourseCategory contentCategory = new CourseCategory();
        contentCategory.setId(7L);
        contentCategory.setName("饲养知识");
        when(categoryService.requireEnabled(7L)).thenReturn(contentCategory);
        KnowledgeSaveRequest request = new KnowledgeSaveRequest();
        request.setSourceType("PLATFORM");
        request.setCategoryId(7L);
        request.setTitle("新建知识");
        request.setContent("<p data-para-key=\"p1\">知识正文</p>");
        request.setApplicableCategoryIds(List.of(applicableId));
        request.setSubmitAction("PUBLISH");
        return request;
    }

    @Test
    void publishesNewKnowledgeWithProductCategoryId() {
        KnowledgeSaveRequest request = createRequest(101L);
        StatAggregates.CategoryRef category = new StatAggregates.CategoryRef();
        category.setId(101L);
        category.setName("育肥猪");
        category.setStatus("ENABLED");
        when(statMapper.selectProductCategories(List.of(101L))).thenReturn(List.of(category));
        when(paragraphService.resolve(null, request.getContent())).thenReturn(KnowledgeHtml.parse(request.getContent(), null));
        doAnswer(call -> { call.<AiKnowledge>getArgument(0).setId(31L); return 1; }).when(knowledgeMapper).insert(any(AiKnowledge.class));
        doAnswer(call -> { call.<KnowledgeVersion>getArgument(0).setId(41L); return 1; }).when(versionMapper).insert(any(KnowledgeVersion.class));

        KnowledgeVO result = service.create(request);

        assertThat(result.getStatus()).isEqualTo("ON_SHELF");
        assertThat(result.getId()).isEqualTo(31L);
        ArgumentCaptor<ContentApplicable> saved = ArgumentCaptor.forClass(ContentApplicable.class);
        verify(applicableMapper).insert(saved.capture());
        assertThat(saved.getValue().getCategoryId()).isEqualTo(101L);
        assertThat(saved.getValue().getBizId()).isEqualTo(31L);
    }

    @Test
    void rejectsBreedIdsThatDoNotIdentifyProductCategoriesBeforeCreatingKnowledge() {
        KnowledgeSaveRequest request = createRequest(1101L);
        when(statMapper.selectProductCategories(List.of(1101L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BizException.class)
                .hasMessageContaining("适用品类不存在或已停用");
        verify(knowledgeMapper, never()).insert(any(AiKnowledge.class));
    }

    @Test
    void publishDraftCreatesNextVersionAndSwitchesOnline() {
        AiKnowledge knowledge = knowledge("DRAFT", 0, 0);
        knowledge.setTitle("旧标题");
        KnowledgeVersion draft = version(11L, null, "DRAFT", "新标题",
                "<p data-para-key=\"p1\">正文</p>");
        KnowledgeHtml.Parsed parsed = KnowledgeHtml.parse(draft.getContent(), null);

        when(knowledgeMapper.selectById(1L)).thenReturn(knowledge);
        when(versionMapper.selectDraft(1L)).thenReturn(draft);
        when(versionMapper.selectMaxVersionNo(1L)).thenReturn(3);
        when(versionMapper.updateById(any(KnowledgeVersion.class))).thenReturn(1);
        when(knowledgeMapper.updateById(any(AiKnowledge.class))).thenReturn(1);
        when(paragraphService.resolve(eq(1L), eq(draft.getContent()))).thenReturn(parsed);

        KnowledgePublishRequest request = new KnowledgePublishRequest();
        request.setChangeNote("补充：可出栏日期以批次档案为准");
        request.setVersion(0);
        KnowledgeVO vo = service.publish(1L, request);

        assertThat(vo.getOnlineVersionNo()).isEqualTo(4);
        assertThat(knowledge.getStatus()).isEqualTo("ON_SHELF");
        assertThat(knowledge.getTitle()).isEqualTo("新标题");
        assertThat(knowledge.getVersionNo()).isEqualTo(4);
        assertThat(draft.getStatus()).isEqualTo("PUBLISHED");
        assertThat(draft.getVersionNo()).isEqualTo(4);
        assertThat(draft.getDraftFlag()).isNull();
        assertThat(draft.getPublishedAt()).isNotNull();
        // updateById 跳过 null：发布后必须另写一条 UPDATE 把 draft_flag 置空（H9）
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<KnowledgeVersion>> updates =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(versionMapper, org.mockito.Mockito.atLeastOnce()).update(org.mockito.ArgumentMatchers.isNull(),
                updates.capture());
        assertThat(updates.getAllValues()).anySatisfy(wrapper -> assertThat(wrapper.getSqlSet()).contains("draft_flag"));
        verify(paragraphService).replace(1L, 4, parsed, true);
        verify(taskService).completeForKnowledge(1L, 4);
    }

    @Test
    void publishWithoutDraftReturns40903() {
        AiKnowledge knowledge = knowledge("ON_SHELF", 3, 2);
        when(knowledgeMapper.selectById(1L)).thenReturn(knowledge);
        when(versionMapper.selectDraft(1L)).thenReturn(null);
        KnowledgePublishRequest request = new KnowledgePublishRequest();
        request.setChangeNote("修改");
        request.setVersion(2);

        assertThatThrownBy(() -> service.publish(1L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    assertThat(((BizException) e).getCode()).isEqualTo(40903);
                    assertThat(e).hasMessage("没有可发布的草稿");
                });
    }

    @Test
    void publishWithStaleVersionReturns40902() {
        AiKnowledge knowledge = knowledge("ON_SHELF", 3, 2);
        when(knowledgeMapper.selectById(1L)).thenReturn(knowledge);
        KnowledgePublishRequest request = new KnowledgePublishRequest();
        request.setChangeNote("修改");
        request.setVersion(1);

        assertThatThrownBy(() -> service.publish(1L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40902));
    }

    @Test
    void rollbackCopiesTargetAndGeneratesNewVersion() {
        AiKnowledge knowledge = knowledge("ON_SHELF", 3, 5);
        knowledge.setOnlineVersionId(50L);
        KnowledgeVersion online = version(50L, 3, "PUBLISHED", "线上标题",
                "<p data-para-key=\"p2\">线上正文</p>");
        KnowledgeVersion target = version(40L, 2, "PUBLISHED", "旧标题",
                "<p data-para-key=\"p1\">旧正文</p>");
        target.setApplicableCategoryIds("101");

        when(knowledgeMapper.selectById(1L)).thenReturn(knowledge);
        when(versionMapper.selectById(50L)).thenReturn(online);
        when(versionMapper.selectByVersionNo(1L, 2)).thenReturn(target);
        when(versionMapper.selectMaxVersionNo(1L)).thenReturn(3);
        when(statMapper.countCitationMessagesByVersion(eq(1L), eq(3), any())).thenReturn(8L);
        when(knowledgeMapper.updateById(any(AiKnowledge.class))).thenReturn(1);
        doAnswer(invocation -> {
            KnowledgeVersion inserted = invocation.getArgument(0);
            inserted.setId(60L);
            return 1;
        }).when(versionMapper).insert(any(KnowledgeVersion.class));

        KnowledgeRollbackRequest request = new KnowledgeRollbackRequest();
        request.setTargetVersionNo(2);
        request.setReason("版本 3 漏掉了沙丁胺醇");
        request.setVersion(5);
        KnowledgeVO vo = service.rollback(1L, request);

        assertThat(vo.getNewVersionNo()).isEqualTo(4);
        assertThat(vo.getAffectedAnswers30d()).isEqualTo(8);
        assertThat(knowledge.getVersionNo()).isEqualTo(4);
        assertThat(knowledge.getOnlineVersionId()).isEqualTo(60L);
        assertThat(knowledge.getTitle()).isEqualTo("旧标题");
        verify(paragraphService).replace(eq(1L), eq(4), any(), eq(true));
    }

    private static AiKnowledge knowledge(String status, int versionNo, int version) {
        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setId(1L);
        knowledge.setTitle("休药期计算与出栏");
        knowledge.setCategoryId(10L);
        knowledge.setCategoryName("用药规范");
        knowledge.setSourceType("PLATFORM");
        knowledge.setDisplayType("ARTICLE");
        knowledge.setIsMajorEpidemic(0);
        knowledge.setStatus(status);
        knowledge.setVersionNo(versionNo);
        knowledge.setVersion(version);
        return knowledge;
    }

    private static KnowledgeVersion version(Long id, Integer no, String status, String title, String content) {
        KnowledgeVersion version = new KnowledgeVersion();
        version.setId(id);
        version.setKnowledgeId(1L);
        version.setVersionNo(no);
        version.setStatus(status);
        version.setDraftFlag("DRAFT".equals(status) ? 1 : null);
        version.setTitle(title);
        version.setCategoryId(10L);
        version.setDisplayType("ARTICLE");
        version.setIsMajorEpidemic(0);
        version.setContent(content);
        version.setContentText(KnowledgeHtml.plainText(content));
        version.setChangeNote("修改");
        version.setPublishedBy(1L);
        return version;
    }
}

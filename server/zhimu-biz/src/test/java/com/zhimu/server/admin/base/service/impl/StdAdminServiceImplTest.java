package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.FeedStdVersion;
import com.zhimu.server.admin.base.entity.StdWeightPoint;
import com.zhimu.server.admin.base.entity.StdWeightVersion;
import com.zhimu.server.admin.base.event.StdWeightEffectiveEvent;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.FeedStdStageMapper;
import com.zhimu.server.admin.base.mapper.FeedStdVersionMapper;
import com.zhimu.server.admin.base.mapper.StdWeightPointMapper;
import com.zhimu.server.admin.base.mapper.StdWeightVersionMapper;
import com.zhimu.server.admin.base.request.StdPublishRequest;
import com.zhimu.server.admin.base.request.StdRollbackRequest;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.service.StdAdminService;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D07–D13 关键分支：无草稿 40903、版本冲突 40902、发布版本号递增、回退复制目标版本。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StdAdminServiceImplTest {

    @Mock private BreedMapper breedMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private StdWeightVersionMapper weightVersionMapper;
    @Mock private StdWeightPointMapper pointMapper;
    @Mock private FeedStdVersionMapper feedVersionMapper;
    @Mock private FeedStdStageMapper stageMapper;
    @Mock private DictItemMapper dictItemMapper;
    @Mock private BaseLogs logs;
    @Mock private ApplicationEventPublisher eventPublisher;

    private StdAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.base.AdminBaseTableInfo.ensure();
        service = new StdAdminServiceImpl(breedMapper, categoryMapper, weightVersionMapper, pointMapper,
                feedVersionMapper, stageMapper, dictItemMapper, logs, eventPublisher);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        Breed breed = new Breed();
        breed.setId(1101L);
        breed.setCategoryId(101L);
        breed.setName("杜长大");
        breed.setStatus("ENABLED");
        when(breedMapper.selectById(1101L)).thenReturn(breed);
        Category category = new Category();
        category.setId(101L);
        category.setName("育肥猪");
        category.setSpecies("PIG");
        category.setIsBreeding(false);
        category.setStatus("ENABLED");
        when(categoryMapper.selectById(101L)).thenReturn(category);
        lenient().when(weightVersionMapper.update(any(), any())).thenReturn(1);
        lenient().when(feedVersionMapper.update(any(), any())).thenReturn(1);
        lenient().when(weightVersionMapper.insert(any(StdWeightVersion.class))).thenAnswer(invocation -> {
            StdWeightVersion version = invocation.getArgument(0);
            version.setId(999L);
            return 1;
        });
        lenient().when(pointMapper.insert((StdWeightPoint) any())).thenReturn(1);
        lenient().when(stageMapper.insert((com.zhimu.server.admin.base.entity.FeedStdStage) any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void publishWithoutDraftReturns40903() {
        when(weightVersionMapper.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.publish(1101L, "weight", publishRequest(0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40903))
                .hasMessage("草稿已被其他管理员处理，请刷新");
    }

    @Test
    void publishWithWrongDraftVersionReturns40902() {
        when(weightVersionMapper.selectList(any())).thenReturn(List.of(draft(0)));
        assertThatThrownBy(() -> service.publish(1101L, "weight", publishRequest(5)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40902));
    }

    @Test
    void publishImmediateIncrementsVersionAndPublishesEvent() {
        StdWeightVersion draft = draft(0);
        StdWeightVersion previous = version(1, "PUBLISHED");
        when(weightVersionMapper.selectList(any())).thenReturn(List.of(draft), List.of(previous));
        when(pointMapper.selectList(any())).thenReturn(List.of(point(80, "51.00"), point(90, "62.00"),
                point(100, "73.00")));

        StdAdminService.PublishResult result = service.publish(1101L, "weight", publishRequest(0));
        assertThat(result.version().getVersionNo()).isEqualTo(2);
        assertThat(result.version().getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.message()).isEqualTo("已发布第 2 版，用户端已按新标准重新计算");
        verify(eventPublisher).publishEvent(any(StdWeightEffectiveEvent.class));
    }

    @Test
    void publishOnDateKeepsScheduledAndFormatsDate() {
        StdWeightVersion draft = draft(0);
        draft.setEffectiveMode("ON_DATE");
        draft.setEffectiveDate(com.zhimu.server.common.util.BeijingTime.today().plusDays(2));
        when(weightVersionMapper.selectList(any())).thenReturn(List.of(draft), List.of(version(1, "PUBLISHED")));
        when(pointMapper.selectList(any())).thenReturn(List.of(point(80, "51.00"), point(90, "62.00")));

        StdAdminService.PublishResult result = service.publish(1101L, "weight", publishRequest(0));
        assertThat(result.version().getStatus()).isEqualTo("SCHEDULED");
        assertThat(result.message()).contains("将于");
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(StdWeightEffectiveEvent.class));
    }

    @Test
    void rollbackCopiesTargetAndGeneratesNewVersion() {
        StdWeightVersion online = version(2, "PUBLISHED");
        StdWeightVersion target = version(1, "SUPERSEDED");
        when(weightVersionMapper.selectList(any())).thenReturn(List.of(online), List.of(target), List.of(online));
        List<StdWeightPoint> points = List.of(point(90, "62.00"), point(100, "73.00"));
        when(pointMapper.selectList(any())).thenReturn(points, points);

        StdRollbackRequest request = new StdRollbackRequest();
        request.setTargetVersionNo(1);
        request.setReason("标准体重有误");
        request.setOnlineVersionNo(2);
        StdAdminService.PublishResult result = service.rollback(1101L, "weight", request);
        assertThat(result.version().getVersionNo()).isEqualTo(3);
        assertThat(result.version().getRollbackFromNo()).isEqualTo(1);
        assertThat(result.version().getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.message()).isEqualTo("已回退到第 1 版的内容，生成第 3 版");
        verify(eventPublisher).publishEvent(any(StdWeightEffectiveEvent.class));
        verify(pointMapper, org.mockito.Mockito.times(2)).insert((StdWeightPoint) any());
    }

    @Test
    void rollbackTargetOnlineReturns40903() {
        StdWeightVersion online = version(2, "PUBLISHED");
        when(weightVersionMapper.selectList(any())).thenReturn(List.of(online));
        StdRollbackRequest request = new StdRollbackRequest();
        request.setTargetVersionNo(2);
        request.setReason("x");
        request.setOnlineVersionNo(2);
        assertThatThrownBy(() -> service.rollback(1101L, "weight", request))
                .isInstanceOf(BizException.class)
                .hasMessage("当前已是第 2 版，不需要回退");
    }

    @Test
    void discardWithoutDraftReturns40903() {
        when(weightVersionMapper.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.discard(1101L, "weight", 0))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40903));
    }

    @Test
    void discardWithWrongVersionReturns40902() {
        when(weightVersionMapper.selectList(any())).thenReturn(List.of(draft(3)));
        assertThatThrownBy(() -> service.discard(1101L, "weight", 0))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40902));
    }

    @Test
    void publishWithoutUpdateNoteReturns40004() {
        StdPublishRequest request = new StdPublishRequest();
        request.setVersion(0);
        assertThatThrownBy(() -> service.publish(1101L, "weight", request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004))
                .hasMessage("请填写更新说明");
    }

    @Test
    void rollbackWithoutReasonReturns40004() {
        StdRollbackRequest request = new StdRollbackRequest();
        request.setTargetVersionNo(1);
        request.setOnlineVersionNo(2);
        assertThatThrownBy(() -> service.rollback(1101L, "weight", request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004))
                .hasMessage("请填写回退原因");
    }

    @Test
    void feedPublishWithoutDraftReturns40903() {
        when(feedVersionMapper.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.publish(1101L, "feed", publishRequest(0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40903));
    }

    private static StdPublishRequest publishRequest(int version) {
        StdPublishRequest request = new StdPublishRequest();
        request.setUpdateNote("下调育肥后期标准体重");
        request.setVersion(version);
        return request;
    }

    private static StdWeightVersion draft(int version) {
        StdWeightVersion draft = new StdWeightVersion();
        draft.setId(900L);
        draft.setBreedId(1101L);
        draft.setStatus("DRAFT");
        draft.setDraftFlag(1);
        draft.setToleranceRate(new BigDecimal("0.1000"));
        draft.setSuggestedWeighTimes(2);
        draft.setEffectiveMode("IMMEDIATE");
        draft.setVersion(version);
        return draft;
    }

    private static StdWeightVersion version(int versionNo, String status) {
        StdWeightVersion version = new StdWeightVersion();
        version.setId(100L + versionNo);
        version.setBreedId(1101L);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setToleranceRate(new BigDecimal("0.1000"));
        version.setSuggestedWeighTimes(2);
        version.setEffectiveMode("IMMEDIATE");
        version.setPublishedAt(LocalDateTime.now());
        version.setVersion(1);
        return version;
    }

    private static StdWeightPoint point(int dayAge, String weight) {
        StdWeightPoint point = new StdWeightPoint();
        point.setVersionId(100L);
        point.setDayAge(dayAge);
        point.setStdWeight(new BigDecimal(weight));
        return point;
    }
}

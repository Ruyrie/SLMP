package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.mapper.ContentStatMapper;
import com.zhimu.server.admin.content.support.ContentExportService;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.vo.ContentExportVO;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CoursePlayRecord;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CoursePlayRecordMapper;
import com.zhimu.server.biz.ai.mapper.CourseProgressMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M14：课程没有播放记录时导出不能因为 null 统计值报 500。 */
class CourseStatExportTest {

    @BeforeAll
    static void initTables() {
        ContentTableInfo.ensure();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CoursePlayRecord.class);
        TableInfoHelper.initTableInfo(assistant, CourseProgress.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportWithoutPlayRecordsWritesPlaceholders() {
        CourseMapper courseMapper = mock(CourseMapper.class);
        CoursePlayRecordMapper playMapper = mock(CoursePlayRecordMapper.class);
        CourseProgressMapper progressMapper = mock(CourseProgressMapper.class);
        ContentExportService exportService = mock(ContentExportService.class);
        CourseStatServiceImpl service = new CourseStatServiceImpl(courseMapper, mock(CourseTopicMapper.class),
                playMapper, progressMapper, mock(ContentStatMapper.class), exportService, mock(ContentLogs.class));
        Course course = new Course();
        course.setId(61L);
        course.setTitle("仔猪腹泻的识别");
        course.setStatus("ON_SHELF");
        course.setOnShelfAt(LocalDateTime.now().minusDays(3));
        when(courseMapper.selectById(61L)).thenReturn(course);
        when(playMapper.selectList(any())).thenReturn(List.of());
        when(progressMapper.selectList(any())).thenReturn(List.of());
        ContentExportVO exported = new ContentExportVO();
        exported.setRowCount(1);
        when(exportService.export(anyString(), anyString(), anyList(), anyList(), anyString(), anyLong(),
                anyString())).thenReturn(exported);

        service.export(61L);

        ArgumentCaptor<List<List<Object>>> rows = ArgumentCaptor.forClass(List.class);
        verify(exportService).export(anyString(), eq("课程数据"), anyList(), rows.capture(), eq("COURSE"),
                eq(61L), anyString());
        assertThat(rows.getValue()).anySatisfy(row -> assertThat(row).containsExactly("完播率", "—"));
        assertThat(rows.getValue()).allSatisfy(row -> assertThat(row).doesNotContainNull());
    }
}

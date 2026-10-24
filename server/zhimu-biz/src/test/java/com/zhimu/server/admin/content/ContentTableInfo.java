package com.zhimu.server.admin.content;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.admin.content.entity.ContentApplicable;
import com.zhimu.server.admin.content.entity.CourseStatDaily;
import com.zhimu.server.admin.content.entity.KnowledgeTask;
import com.zhimu.server.admin.content.entity.KnowledgeVersion;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/** 纯单元测试没有 MyBatis 上下文，LambdaQueryWrapper 使用前需初始化表信息缓存（幂等）。 */
public final class ContentTableInfo {

    private static boolean initialized;

    private ContentTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(Course.class, CourseCategory.class, CourseTopic.class,
                AiKnowledge.class, AiKnowledgeParagraph.class, KnowledgeVersion.class, KnowledgeTask.class,
                ContentApplicable.class, CourseStatDaily.class, FileEntity.class, FileVariant.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}

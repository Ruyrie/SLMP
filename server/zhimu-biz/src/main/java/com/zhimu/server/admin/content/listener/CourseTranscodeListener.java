package com.zhimu.server.admin.content.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.system.api.AdminMessageCommand;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.event.FileTranscodeFinishedEvent;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 转码完成回写：更新 t_course.transcode_status / duration_sec（不改 version，避免与编辑冲突），
 * 失败时给课程创建人写一条 ADMIN 站内消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseTranscodeListener {

    private final CourseMapper courseMapper;
    private final FileMapper fileMapper;
    private final AdminMessageService adminMessageService;

    @EventListener
    @Transactional
    public void onFinished(FileTranscodeFinishedEvent event) {
        FileEntity file = fileMapper.selectById(event.fileId());
        if (file == null) {
            return;
        }
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getVideoFileId, event.fileId()));
        for (Course course : courses) {
            var update = Wrappers.<Course>lambdaUpdate()
                    .eq(Course::getId, course.getId())
                    .set(Course::getTranscodeStatus, file.getTranscodeStatus())
                    .set(Course::getUpdatedAt, BeijingTime.now());
            if (event.success() && file.getDurationMs() != null) {
                update.set(Course::getDurationSec, (int) Math.round(file.getDurationMs() / 1000.0));
            }
            courseMapper.update(null, update);
            if (!event.success()) {
                notifyFailure(course, event.reason());
            }
        }
    }

    private void notifyFailure(Course course, String reason) {
        Long receiver = course.getCreatedBy() != null ? course.getCreatedBy() : fileCreatedBy(course.getVideoFileId());
        if (receiver == null) {
            return;
        }
        AdminMessageCommand command = AdminMessageCommand.builder()
                .adminId(receiver)
                .msgType("TRANSCODE_FAILED")
                .level("IMPORTANT")
                .title("课程转码失败")
                .summary("「" + course.getTitle() + "」转码失败，请重试"
                        + (reason == null || reason.isBlank() ? "" : "：" + reason))
                .bizType("COURSE")
                .bizId(course.getId())
                .routePage("WA-C03")
                .routeParams(Map.of("course_id", course.getId()))
                .mergeKey("course-transcode:" + course.getId())
                .build();
        try {
            adminMessageService.send(command);
        } catch (RuntimeException e) {
            log.warn("转码失败消息发送失败 course_id={}", course.getId(), e);
        }
    }

    private Long fileCreatedBy(Long fileId) {
        if (fileId == null) {
            return null;
        }
        FileEntity file = fileMapper.selectById(fileId);
        return file == null ? null : file.getCreatedBy();
    }
}

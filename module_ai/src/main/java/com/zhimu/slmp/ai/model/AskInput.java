package com.zhimu.slmp.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.model.request.AiAskRequest;
import com.zhimu.slmp.ai.model.vo.LinkedBatchVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次提问的内容（点发送时冻结）：会话、文字、附图、关联批次、课程背景与幂等键。
 * 服务端模式转为 {@link AiAskRequest}；测试直连模式按它组装提示词与图片。“重试”沿用同一个 idemKey。
 */
public final class AskInput {

    /** 一张已附的图片：服务端 file_id 与显示地址（本地压缩文件或 URL） */
    public static final class Attach {
        public final long fileId;
        @NonNull
        public final String displayUri;
        /** 本地压缩后的 JPEG（测试直连时读取并转 base64）；服务端图片为 null */
        @Nullable
        public final String localPath;

        public Attach(long fileId, @NonNull String displayUri, @Nullable String localPath) {
            this.fileId = fileId;
            this.displayUri = displayUri;
            this.localPath = localPath;
        }
    }

    @Nullable
    private final Long sessionId;
    @NonNull
    private final String questionText;
    @NonNull
    private final List<Attach> images;
    @Nullable
    private final LinkedBatchVO batch;
    @Nullable
    private final CourseContext course;
    @NonNull
    private final String idemKey;

    public AskInput(@Nullable Long sessionId, @NonNull String questionText, @NonNull List<Attach> images,
                    @Nullable LinkedBatchVO batch, @Nullable CourseContext course, @NonNull String idemKey) {
        this.sessionId = sessionId;
        this.questionText = questionText;
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
        this.batch = batch;
        this.course = course;
        this.idemKey = idemKey;
    }

    @Nullable
    public Long getSessionId() {
        return sessionId;
    }

    @NonNull
    public String getQuestionText() {
        return questionText;
    }

    @NonNull
    public List<Attach> getImages() {
        return images;
    }

    @Nullable
    public LinkedBatchVO getBatch() {
        return batch;
    }

    @Nullable
    public CourseContext getCourse() {
        return course;
    }

    @NonNull
    public String getIdemKey() {
        return idemKey;
    }

    /** 会话在首问后才创建：重试时带上新会话 ID */
    @NonNull
    public AskInput withSession(@Nullable Long id) {
        return new AskInput(id, questionText, images, batch, course, idemKey);
    }

    /** 附图上传完成后补上 file_id */
    @NonNull
    public AskInput withImages(@NonNull List<Attach> list) {
        return new AskInput(sessionId, questionText, list, batch, course, idemKey);
    }

    @NonNull
    public AiAskRequest toRequest() {
        AiAskRequest r = new AiAskRequest();
        r.setSessionId(sessionId);
        r.setQuestionText(questionText.isEmpty() ? null : questionText);
        List<Long> ids = new ArrayList<>();
        for (Attach a : images) {
            if (a.fileId > 0) {
                ids.add(a.fileId);
            }
        }
        r.setImageFileIds(ids.isEmpty() ? null : ids);
        r.setBatchId(batch == null ? null : batch.getBatchId());
        r.setContextCourseId(course == null ? null : course.getCourseId());
        return r;
    }
}

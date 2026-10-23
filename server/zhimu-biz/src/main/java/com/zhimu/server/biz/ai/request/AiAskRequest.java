package com.zhimu.server.biz.ai.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * API-K06 提问（请求体 snake_case）：session_id 新会话不传；question_text 与 image_file_ids 至少一项。
 */
@Getter
@Setter
public class AiAskRequest {

    private Long sessionId;
    private String questionText;
    private List<Long> imageFileIds;
    private Long batchId;
    private Long contextCourseId;
}

package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.query.CourseQuery;
import com.zhimu.server.biz.ai.query.TopicQuery;
import com.zhimu.server.biz.ai.request.CourseProgressRequest;
import com.zhimu.server.biz.ai.vo.ContinueWatchingVO;
import com.zhimu.server.biz.ai.vo.CourseCardVO;
import com.zhimu.server.biz.ai.vo.CourseCategoryVO;
import com.zhimu.server.biz.ai.vo.CoursePlayVO;
import com.zhimu.server.biz.ai.vo.ProgressResultVO;
import com.zhimu.server.biz.ai.vo.TopicDetailVO;
import com.zhimu.server.biz.ai.vo.TopicVO;
import com.zhimu.server.biz.ai.vo.WatchHistoryVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/**
 * API-K14–K21：课程分类、继续观看、专题、课程列表与播放、进度上报、观看记录。
 */
public interface CourseService {

    List<CourseCategoryVO> categories();

    ContinueWatchingVO continueWatching(Long userId);

    PageVO<TopicVO> topics(Long userId, TopicQuery query);

    TopicDetailVO topicDetail(Long userId, Long topicId);

    PageVO<CourseCardVO> courses(Long userId, CourseQuery query);

    CoursePlayVO coursePlay(Long userId, Long courseId);

    ProgressResultVO reportProgress(Long userId, Long courseId, CourseProgressRequest request);

    PageVO<WatchHistoryVO> history(Long userId, int page, int size);
}

package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.request.FeedStdDraftRequest;
import com.zhimu.server.admin.base.request.StdPublishRequest;
import com.zhimu.server.admin.base.request.StdRollbackRequest;
import com.zhimu.server.admin.base.request.StdWeightDraftRequest;
import com.zhimu.server.admin.base.vo.FeedStdVO;
import com.zhimu.server.admin.base.vo.StdImpactVO;
import com.zhimu.server.admin.base.vo.StdVersionVO;
import com.zhimu.server.admin.base.vo.StdWeightVO;

import java.util.List;

/** ADM-D07–D13 标准体重 / 饲喂标准草稿、发布、版本与回退。 */
public interface StdAdminService {

    StdWeightVO weight(Long breedId);

    FeedStdVO feed(Long breedId);

    StdWeightVO saveWeightDraft(Long breedId, StdWeightDraftRequest request);

    FeedStdVO saveFeedDraft(Long breedId, FeedStdDraftRequest request);

    /** ADM-D09 放弃草稿；version 为草稿乐观锁 */
    void discard(Long breedId, String stdType, Integer version);

    StdImpactVO impact(Long breedId, String stdType);

    PublishResult publish(Long breedId, String stdType, StdPublishRequest request);

    List<StdVersionVO> versions(Long breedId, String stdType);

    PublishResult rollback(Long breedId, String stdType, StdRollbackRequest request);

    /** 发布/回退结果：data 为 StdVersionVO，message 为成功文案 */
    record PublishResult(StdVersionVO version, String message) {
    }
}

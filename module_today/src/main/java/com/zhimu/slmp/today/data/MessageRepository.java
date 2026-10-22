package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.Pager;
import androidx.paging.PagingData;
import androidx.paging.PagingSource;
import androidx.paging.rxjava3.PagingRx;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.today.model.request.MessageOpenRequest;
import com.zhimu.slmp.today.model.request.MessageReadAllRequest;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.model.vo.MessageDeleteResult;
import com.zhimu.slmp.today.model.vo.MessageDetailVO;
import com.zhimu.slmp.today.model.vo.MessagePageVO;
import com.zhimu.slmp.today.model.vo.NoticePayload;
import com.zhimu.slmp.today.model.vo.OpenResultVO;
import com.zhimu.slmp.today.model.vo.ReadAllResult;
import com.zhimu.slmp.today.model.vo.TransferPayload;
import com.zhimu.slmp.today.model.vo.UnreadCountVO;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 消息（API-T12–T18），只按当前用户。详情的 payload 按 msg_type 转换为 NoticePayload / TransferPayload。
 */
@FeatureScope
public class MessageRepository {

    private final TodayApi api;
    private final Gson gson;
    @Nullable
    private volatile WeakReference<PagingSource<?, ?>> source;

    @Inject
    public MessageRepository(@NonNull TodayApi api, @NonNull Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    /** API-T12 消息列表（分页） */
    @NonNull
    public Flowable<PagingData<MessageBriefVO>> pager(@Nullable String category, @Nullable Long farmId,
                                                      boolean unreadOnly, @Nullable Consumer<MessagePageVO> onFirstPage) {
        return PagingRx.getFlowable(new Pager<>(TodoRepository.config(), () -> {
            MessagePagingSource s = new MessagePagingSource(api, category, farmId, unreadOnly, onFirstPage);
            source = new WeakReference<>(s);
            return s;
        }));
    }

    /** 使最近一次创建的消息数据源失效（下拉刷新、撤销删除后） */
    public void invalidate() {
        WeakReference<PagingSource<?, ?>> ref = source;
        PagingSource<?, ?> s = ref == null ? null : ref.get();
        if (s != null) {
            s.invalidate();
        }
    }

    /** API-T13 */
    @NonNull
    public Single<UnreadCountVO> getUnreadCount(@Nullable String category, @Nullable Long farmId) {
        return api.getUnreadCount(category, farmId).compose(RxResult.unwrap());
    }

    /** API-T14 */
    @NonNull
    public Single<ReadAllResult> readAll(@NonNull MessageReadAllRequest body) {
        return api.readAll(body).compose(RxResult.unwrap());
    }

    /** API-T15 */
    @NonNull
    public Single<OpenResultVO> open(long messageId, @NonNull MessageOpenRequest body) {
        return api.openMessage(messageId, body).compose(RxResult.unwrap());
    }

    /** API-T16：按 msg_type 转换 payload */
    @NonNull
    public Single<MessageDetailVO> getDetail(long messageId) {
        return api.getMessageDetail(messageId).compose(RxResult.unwrap()).map(this::convertPayload);
    }

    /** API-T17 */
    @NonNull
    public Single<MessageDeleteResult> delete(long messageId) {
        return api.deleteMessage(messageId).compose(RxResult.unwrap());
    }

    /** API-T18 */
    @NonNull
    public Single<MessageBriefVO> restore(long messageId) {
        return api.restoreMessage(messageId).compose(RxResult.unwrap());
    }

    @NonNull
    MessageDetailVO convertPayload(@NonNull MessageDetailVO detail) {
        JsonObject payload = detail.getPayload();
        if (payload == null) {
            return detail;
        }
        String type = detail.getMsgType();
        if (MessageBriefVO.TYPE_NOTICE.equals(type)) {
            detail.setNotice(gson.fromJson(payload, NoticePayload.class));
        } else if (MessageBriefVO.TYPE_OWNER_TRANSFER.equals(type) || "TRANSFER_RESULT".equals(type)) {
            detail.setTransfer(gson.fromJson(payload, TransferPayload.class));
        }
        return detail;
    }
}

package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.model.vo.MessagePageVO;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * 消息列表分页（API-T12）：sort_time 倒序，每页 20 条；第 1 页到达时回调（filtered_unread、has_more）。
 */
public class MessagePagingSource extends PagedSource<MessageBriefVO> {

    public MessagePagingSource(@NonNull TodayApi api, @Nullable String category, @Nullable Long farmId,
                               boolean unreadOnly, @Nullable Consumer<MessagePageVO> onFirstPage) {
        super((page, size) -> api.getMessages(category, farmId, unreadOnly, page, size)
                        .compose(RxResult.unwrap())
                        .map(vo -> (PageVO<MessageBriefVO>) vo),
                onFirstPage == null ? null : vo -> onFirstPage.accept((MessagePageVO) vo));
    }
}

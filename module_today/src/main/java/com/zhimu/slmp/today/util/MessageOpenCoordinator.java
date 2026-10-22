package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.today.data.MessageRepository;
import com.zhimu.slmp.today.model.request.MessageOpenRequest;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.model.vo.OpenResultVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Maybe;

/**
 * 打开消息（技术文档 2.6.7）：
 * <ul>
 * <li>FARM_INVITE：不跳转，在列表内同意或拒绝；</li>
 * <li>route 为 T07（平台通知、转让、冻结）：直接打开 T07，由 API-T16 标为已读；</li>
 * <li>其余调 API-T15：OK 按 link 打开（need_switch 先切换）；DELETED 进 T08；NO_PERMISSION、NOT_MEMBER、FARM_FROZEN 进 U03；
 * NO_JUMP 只标已读（从推送打开时进入 T06）。total_unread 写入 ITodoService。</li>
 * </ul>
 * 返回 empty 表示不跳转。
 */
@FeatureScope
public class MessageOpenCoordinator {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_DELETED = "DELETED";
    public static final String STATUS_NO_PERMISSION = "NO_PERMISSION";
    public static final String STATUS_NOT_MEMBER = "NOT_MEMBER";
    public static final String STATUS_FARM_FROZEN = "FARM_FROZEN";
    public static final String STATUS_NO_JUMP = "NO_JUMP";

    private final MessageRepository repository;
    private final LinkNavigator navigator;
    private final TodayServiceImpl todoService;

    @Inject
    public MessageOpenCoordinator(@NonNull MessageRepository repository, @NonNull LinkNavigator navigator,
                                  @NonNull TodayServiceImpl todoService) {
        this.repository = repository;
        this.navigator = navigator;
        this.todoService = todoService;
    }

    /** 消息中心点击 */
    @NonNull
    public Maybe<NavTarget> open(@NonNull MessageBriefVO m) {
        if (m.isInvite()) {
            return Maybe.empty();
        }
        if ("T07".equals(m.getRoute())) {
            return Maybe.just(NavTarget.of("T07", RouterPath.EXTRA_MESSAGE_ID, m.getMessageId()));
        }
        return openById(m.getMessageId(), null, false);
    }

    /**
     * 按消息 ID 打开（推送点击时 fromPush 为真，push_id 用于记录点击）。
     */
    @NonNull
    public Maybe<NavTarget> openById(long messageId, @Nullable Long pushId, boolean fromPush) {
        return repository.open(messageId, new MessageOpenRequest(pushId))
                .flatMapMaybe(result -> {
                    todoService.setUnread(result.getTotalUnread());
                    return resolve(messageId, result, fromPush);
                });
    }

    @NonNull
    Maybe<NavTarget> resolve(long messageId, @NonNull OpenResultVO r, boolean fromPush) {
        String status = r.getTargetStatus() == null ? STATUS_OK : r.getTargetStatus();
        switch (status) {
            case STATUS_DELETED:
                return Maybe.just(NavTarget.recordInvalid(r.getPageTitle(), r.getDeletedTitle(), r.getDeletedDesc(),
                        messageId));
            case STATUS_NO_PERMISSION:
            case STATUS_NOT_MEMBER:
            case STATUS_FARM_FROZEN:
                return Maybe.just(NavTarget.noPermission(r.getDenyTitle(), r.getDenyDesc(), "返回消息中心"));
            case STATUS_NO_JUMP:
                return fromPush ? Maybe.just(NavTarget.of("T06")) : Maybe.empty();
            default:
                if (r.getLink() == null || !LinkNavigator.isKnownRoute(r.getLink().getRoute())) {
                    return fromPush ? Maybe.just(NavTarget.of("T06")) : Maybe.empty();
                }
                return navigator.forLink(r.getLink()).toMaybe();
        }
    }
}

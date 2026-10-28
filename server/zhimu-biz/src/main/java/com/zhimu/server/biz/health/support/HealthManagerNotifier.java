package com.zhimu.server.biz.health.support;

import com.zhimu.server.biz.health.query.FarmMemberRow;
import com.zhimu.server.biz.health.vo.NotifyResultVO;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 员工“通知场长”（API-D27 用药记录、免疫记录共用）：只有员工可发、只能通知本人登记的记录；
 * 发给本场场长（没有场长时发给所有者），同一员工同一记录 10 分钟内只发一次（超出返回 TOO_FREQUENT 与 cooldown_seconds）。
 */
@Component
@RequiredArgsConstructor
public class HealthManagerNotifier {

    private static final int COOLDOWN_SECONDS = 600;

    private final HealthSupport support;
    private final MessageService messageService;
    private final StringRedisTemplate stringRedisTemplate;

    /** 通知内容 */
    public record Notice(String rateScope, Long recordId, Long createdBy, Long executorId, String recordName,
                         String summary, BizType bizType, String route, Map<String, Object> extras) {
    }

    public NotifyResultVO notify(Notice notice) {
        if (!support.staffView()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只有员工可以通知场长");
        }
        Long userId = UserContext.userId();
        if (userId == null || !(userId.equals(notice.createdBy()) || userId.equals(notice.executorId()))) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只能通知本人登记的记录");
        }
        List<FarmMemberRow> managers = receivers();
        if (managers.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "本场没有可通知的场长");
        }
        NotifyResultVO vo = new NotifyResultVO();
        for (FarmMemberRow row : managers) {
            vo.getNotifiedNames().add(row.getName());
        }
        String key = "rate:" + notice.rateScope() + ":" + userId + ":" + notice.recordId();
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                Duration.ofSeconds(COOLDOWN_SECONDS));
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(ErrorCode.TOO_FREQUENT,
                    "已通知" + String.join("、", vo.getNotifiedNames()) + "，请勿重复发送")
                    .with("cooldown_seconds", COOLDOWN_SECONDS);
        }
        Set<Long> receivers = new LinkedHashSet<>();
        for (FarmMemberRow row : managers) {
            receivers.add(row.getUserId());
        }
        MessageCommand.MessageCommandBuilder builder = MessageCommand.builder()
                .msgType(MsgType.STAFF_NOTIFY)
                .farmId(support.farmId())
                .receiverUserIds(receivers)
                .title(support.userName(userId) + "请你处理" + notice.recordName())
                .summary(notice.summary())
                .bizType(notice.bizType())
                .bizId(notice.recordId())
                .route(notice.route());
        if (notice.extras() != null) {
            notice.extras().forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.extra(k, v);
                }
            });
        }
        messageService.send(builder.build());
        return vo;
    }

    /** 场长；本场没有场长时为所有者 */
    public List<FarmMemberRow> receivers() {
        List<FarmMemberRow> managers = new ArrayList<>();
        for (FarmMemberRow row : support.members()) {
            if ("MANAGER".equals(row.getRole())) {
                managers.add(row);
            }
        }
        if (managers.isEmpty()) {
            for (FarmMemberRow row : support.members()) {
                if ("OWNER".equals(row.getRole())) {
                    managers.add(row);
                }
            }
        }
        return managers;
    }
}

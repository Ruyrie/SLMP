package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgLevel;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 模块02 站内消息：全部在事务提交后经 MessageService 写入（模块03 落地前是只写日志的临时实现）。
 * 文案按模块02 文档 2.8 节；本模块不发送短信。
 */
@Component
@RequiredArgsConstructor
public class FarmNotify {

    private final ObjectProvider<MessageService> messageServiceProvider;

    private MessageService service() {
        return messageServiceProvider.getIfAvailable();
    }

    public void auditResult(Farm farm, Long auditId, boolean approved, String reason) {
        String title = farm.getName() + "注册审核" + (approved ? "已通过" : "未通过");
        String summary = approved ? "点击查看养殖场信息" : "原因：" + nullSafe(reason);
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.FARM_AUDIT_RESULT)
                .level(MsgLevel.IMPORTANT)
                .farmId(farm.getId())
                .receiverUserId(farm.getOwnerUserId())
                .title(title)
                .summary(summary)
                .content(summary)
                .bizType(BizType.FARM_AUDIT)
                .bizId(auditId)
                .route(approved ? "F05" : "F03")
                .extra("farm_id", farm.getId())
                .build()));
    }

    public void changeResult(Farm farm, Long changeId, boolean approved, String reason) {
        String title = farm.getName() + "资料变更" + (approved ? "已通过" : "未通过");
        String summary = approved ? "新的名称、地址与资质图已生效" : "原因：" + nullSafe(reason);
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.FARM_CHANGE_RESULT)
                .level(MsgLevel.IMPORTANT)
                .farmId(farm.getId())
                .receiverUserId(farm.getOwnerUserId())
                .title(title)
                .summary(summary)
                .content(summary)
                .bizType(BizType.FARM_CHANGE)
                .bizId(changeId)
                .route("F05")
                .extra("farm_id", farm.getId())
                .build()));
    }

    public void invite(Farm farm, FarmInvite invite, String inviterName) {
        String roleText = "MANAGER".equals(invite.getRole()) ? "场长" : "员工";
        String title = inviterName + "邀请你加入" + farm.getName();
        String summary = roleText + " · 7 天内有效";
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.FARM_INVITE)
                .farmId(farm.getId())
                .receiverUserId(invite.getInviteeUserId())
                .title(title)
                .summary(summary)
                .content(title + "（" + summary + "）")
                .bizType(BizType.FARM_INVITE)
                .bizId(invite.getId())
                .route("T06")
                .extra("invite_id", invite.getId())
                .extra("farm_id", farm.getId())
                .build()));
    }

    public void inviteAccepted(Farm farm, FarmMember member, String memberName) {
        String title = memberName + "已同意邀请，加入" + farm.getName();
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.INVITE_ACCEPTED)
                .farmId(farm.getId())
                .receiverUserId(farm.getOwnerUserId())
                .title(title)
                .summary(title)
                .content(title)
                .bizType(BizType.FARM_MEMBER)
                .bizId(member.getId())
                .route("F07")
                .extra("farm_id", farm.getId())
                .build()));
    }

    public void roleChanged(Farm farm, FarmMember member, String roleText) {
        String title = "你在" + farm.getName() + "的角色已改为" + roleText;
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.MEMBER_ROLE_CHANGED)
                .farmId(farm.getId())
                .receiverUserId(member.getUserId())
                .title(title)
                .summary(title)
                .content(title)
                .bizType(BizType.FARM_MEMBER)
                .bizId(member.getId())
                .route("F05")
                .extra("farm_id", farm.getId())
                .build()));
    }

    public void memberRemoved(Farm farm, FarmMember member) {
        String title = "你已被移出" + farm.getName();
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.MEMBER_REMOVED)
                .farmId(farm.getId())
                .receiverUserId(member.getUserId())
                .title(title)
                .summary(title)
                .content(title)
                .bizType(BizType.FARM_MEMBER)
                .bizId(member.getId())
                .route("F01")
                .extra("farm_id", farm.getId())
                .build()));
    }

    public void farmDisabled(Farm farm, Collection<Long> receiverUserIds) {
        farmStatus(farm, receiverUserIds, MsgType.FARM_DISABLED, "FARM_DISABLED",
                farm.getName() + "已停用，数据只能查看");
    }

    public void farmEnabled(Farm farm, Collection<Long> receiverUserIds) {
        farmStatus(farm, receiverUserIds, MsgType.FARM_ENABLED, "FARM_ENABLED",
                farm.getName() + "已重新启用");
    }

    private void farmStatus(Farm farm, Collection<Long> receiverUserIds, MsgType type, String action,
                            String title) {
        List<Long> receivers = receiverUserIds == null ? List.of() : receiverUserIds.stream().toList();
        if (receivers.isEmpty()) {
            return;
        }
        AfterCommit.run(() -> {
            MessageCommand.MessageCommandBuilder builder = MessageCommand.builder()
                    .msgType(type)
                    .farmId(farm.getId())
                    .title(title)
                    .summary(title)
                    .content(title)
                    .bizType(BizType.FARM)
                    .bizId(farm.getId())
                    .route("F05")
                    .extra("farm_id", farm.getId())
                    .extra("action", action);
            receivers.forEach(builder::receiverUserId);
            send(builder.build());
        });
    }

    public void frozen(Farm farm, Collection<Long> receiverUserIds, String reason) {
        String title = farm.getName() + "已被平台冻结";
        String content = title + "。原因：" + nullSafe(reason);
        sendToMembers(farm, receiverUserIds, MsgType.FARM_FROZEN, "FARM_FROZEN", "F05", title, content);
    }

    public void unfrozen(Farm farm, Collection<Long> receiverUserIds) {
        String title = farm.getName() + "已解冻";
        sendToMembers(farm, receiverUserIds, MsgType.FARM_UNFROZEN, "FARM_UNFROZEN", "F05", title, title);
    }

    public void farmDeleted(Farm farm, Collection<Long> receiverUserIds) {
        String title = farm.getName() + "已被所有者删除";
        sendToMembers(farm, receiverUserIds, MsgType.FARM_DELETED, "FARM_DELETED", "F01", title, title);
    }

    private void sendToMembers(Farm farm, Collection<Long> receiverUserIds, MsgType type, String action,
                               String route, String title, String content) {
        List<Long> receivers = receiverUserIds == null ? List.of() : receiverUserIds.stream().toList();
        if (receivers.isEmpty()) {
            return;
        }
        AfterCommit.run(() -> {
            MessageCommand.MessageCommandBuilder builder = MessageCommand.builder()
                    .msgType(type)
                    .level(MsgLevel.IMPORTANT)
                    .farmId(farm.getId())
                    .title(title)
                    .summary(content)
                    .content(content)
                    .bizType(BizType.FARM)
                    .bizId(farm.getId())
                    .route(route)
                    .extra("farm_id", farm.getId())
                    .extra("action", action);
            receivers.forEach(builder::receiverUserId);
            send(builder.build());
        });
    }

    public void transferCreated(Farm farm, FarmTransfer transfer, String fromUserName) {
        String title = fromUserName + "请你接收" + farm.getName() + "的所有权";
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.OWNER_TRANSFER)
                .farmId(farm.getId())
                .receiverUserId(transfer.getToUserId())
                .title(title)
                .summary("7 天内有效")
                .content(title + "，7 天内有效")
                .bizType(BizType.FARM_TRANSFER)
                .bizId(transfer.getId())
                .route("T07")
                .extra("transfer_id", transfer.getId())
                .extra("farm_id", farm.getId())
                .build()));
    }

    public void transferAccepted(Farm farm, FarmTransfer transfer, String toName) {
        String title = toName + "已接收" + farm.getName() + "的所有权，你现在是" + farm.getName() + "的场长";
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.TRANSFER_RESULT)
                .farmId(farm.getId())
                .receiverUserId(transfer.getFromUserId())
                .title(title)
                .summary(title)
                .content(title)
                .bizType(BizType.FARM_TRANSFER)
                .bizId(transfer.getId())
                .route("T07")
                .extra("transfer_id", transfer.getId())
                .extra("farm_id", farm.getId())
                .extra("action", "ACCEPTED")
                .build()));
    }

    public void transferRejected(Farm farm, FarmTransfer transfer, String toName) {
        String title = toName + "拒绝接收" + farm.getName() + "的所有权";
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.TRANSFER_RESULT)
                .farmId(farm.getId())
                .receiverUserId(transfer.getFromUserId())
                .title(title)
                .summary(title)
                .content(title)
                .bizType(BizType.FARM_TRANSFER)
                .bizId(transfer.getId())
                .route("T07")
                .extra("transfer_id", transfer.getId())
                .extra("farm_id", farm.getId())
                .extra("action", "REJECTED")
                .build()));
    }

    public void transferExpired(Farm farm, FarmTransfer transfer, String toName) {
        String title = "转让请求已过期，" + toName + " 7 天未确认";
        AfterCommit.run(() -> send(MessageCommand.builder()
                .msgType(MsgType.TRANSFER_RESULT)
                .farmId(farm.getId())
                .receiverUserId(transfer.getFromUserId())
                .title(title)
                .summary(title)
                .content(title)
                .bizType(BizType.FARM_TRANSFER)
                .bizId(transfer.getId())
                .route("T07")
                .extra("transfer_id", transfer.getId())
                .extra("farm_id", farm.getId())
                .extra("action", "EXPIRED")
                .build()));
    }

    private void send(MessageCommand command) {
        MessageService service = service();
        if (service != null) {
            service.send(command);
        }
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }
}

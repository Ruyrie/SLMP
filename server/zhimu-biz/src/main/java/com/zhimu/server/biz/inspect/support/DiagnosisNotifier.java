package com.zhimu.server.biz.inspect.support;

import com.zhimu.server.biz.inspect.convert.DiagnosisConvert;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.mapper.InspectReferenceMapper;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 诊断完成 / 失败的站内消息（AI_DIAGNOSIS）。接收人与诊断详情“已通知 xx”同源：
 * 上报人 + 所有者 + 场长（本场在职成员，按用户去重）。跳转与示例数据一致：route I06，biz ABNORMAL_REPORT。
 * 在诊断结果事务提交后调用；发送失败只记日志，不影响诊断结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisNotifier {

    public static final String ROUTE = "I06";

    private final InspectReferenceMapper referenceMapper;
    private final MessageService messageService;

    /** 诊断完成后要通知的人：user_id → 姓名（顺序：上报人、所有者、场长） */
    public Map<Long, String> recipients(AbnormalReport report) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (report == null || report.getFarmId() == null) {
            return result;
        }
        List<FarmMemberRow> members = referenceMapper.selectMembers(report.getFarmId());
        if (members == null) {
            return result;
        }
        for (FarmMemberRow member : members) {
            if (Objects.equals(member.getUserId(), report.getReporterId())) {
                result.put(member.getUserId(), member.getName());
            }
        }
        for (FarmMemberRow member : members) {
            if (member.getUserId() != null && ("OWNER".equals(member.getRole()) || "MANAGER".equals(member.getRole()))) {
                result.putIfAbsent(member.getUserId(), member.getName());
            }
        }
        return result;
    }

    public static String names(Map<Long, String> recipients) {
        return recipients.values().stream().filter(Objects::nonNull).distinct()
                .reduce((a, b) -> a + "、" + b).orElse(null);
    }

    /** 诊断成功：标题“AI 诊断：疑似 xx”，标签为紧急程度 */
    public void diagnosed(AbnormalReport report, Map<Long, String> recipients, String penName, String batchNo,
                          String topCause) {
        Map<String, Object> extras = extras(report, recipients, penName, batchNo);
        extras.put("urgency", report.getUrgency());
        extras.put("urgency_text", DiagnosisConvert.urgencyText(report.getUrgency()));
        extras.put("result", "SUCCESS");
        String title = topCause == null || topCause.isBlank() ? "AI 诊断完成" : "AI 诊断：疑似" + topCause;
        send(report, recipients, title, penName, extras);
    }

    /** 诊断失败：提示可直接登记处理结果 */
    public void failed(AbnormalReport report, Map<Long, String> recipients, String penName, String batchNo,
                       String reasonText) {
        Map<String, Object> extras = extras(report, recipients, penName, batchNo);
        extras.put("result", "FAILED");
        extras.put("fail_reason_text", reasonText);
        String summary = (penName == null ? "" : penName + " · ") + "可以直接登记处理结果";
        send(report, recipients, "AI 诊断失败" + (reasonText == null ? "" : "：" + reasonText), summary, extras);
    }

    private Map<String, Object> extras(AbnormalReport report, Map<Long, String> recipients, String penName,
                                       String batchNo) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("pen_name", penName);
        extras.put("batch_no", batchNo);
        extras.put("reporter_name", recipients.get(report.getReporterId()));
        extras.put("reported_at", BeijingTime.format(report.getReportedAt()));
        extras.put("sick_count", report.getSickCount() == null ? 0 : report.getSickCount());
        return extras;
    }

    private void send(AbnormalReport report, Map<Long, String> recipients, String title, String summary,
                      Map<String, Object> extras) {
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        extras.forEach((key, value) -> {
            if (value != null) {
                clean.put(key, value);
            }
        });
        try {
            messageService.send(MessageCommand.builder()
                    .msgType(MsgType.AI_DIAGNOSIS)
                    .farmId(report.getFarmId())
                    .receiverUserIds(recipients.keySet())
                    .title(title)
                    .summary(summary)
                    .bizType(BizType.ABNORMAL_REPORT)
                    .bizId(report.getId())
                    .route(ROUTE)
                    .extras(clean)
                    .build());
        } catch (RuntimeException e) {
            log.warn("AI 诊断消息发送失败 reportId={}", report.getId(), e);
        }
    }
}

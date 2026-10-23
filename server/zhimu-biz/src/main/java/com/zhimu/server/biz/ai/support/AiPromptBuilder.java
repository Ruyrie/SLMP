package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiScene;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组织提问提示词：助手角色、回答边界、类型/主题标记、引用写法、今天日期、批次与课程背景、检索资料、最近 10 轮。
 */
@Component
public class AiPromptBuilder {

    public AiChatRequest build(String question, FarmContext.FarmMember member, LinkedBatchVO batch,
                               String courseTitle, List<RetrievalHit> hits, List<AiMessage> history,
                               List<String> imageUrls) {
        AiChatRequest.AiChatRequestBuilder builder = AiChatRequest.builder()
                .scene(AiScene.CHAT)
                .maxTokens(2000);
        builder.message(com.zhimu.server.integration.ai.AiMessage.system(systemPrompt(member, batch,
                courseTitle, hits)));
        for (AiMessage message : history) {
            String text = message.getQuestionText();
            if (text != null && !text.isBlank()) {
                builder.message(com.zhimu.server.integration.ai.AiMessage.user(text));
            }
            String answer = message.getAnswerText();
            if (answer != null && !answer.isBlank()) {
                builder.message(com.zhimu.server.integration.ai.AiMessage.assistant(
                        answer.length() > 1000 ? answer.substring(0, 1000) : answer));
            }
        }
        String finalQuestion = question == null ? "" : question.trim();
        if (finalQuestion.isEmpty()) {
            finalQuestion = "请根据图片判断猪只的健康状况，并给出养殖管理建议。";
        }
        builder.message(com.zhimu.server.integration.ai.AiMessage.user(finalQuestion, imageUrls));
        return builder.build();
    }

    private String systemPrompt(FarmContext.FarmMember member, LinkedBatchVO batch, String courseTitle,
                                List<RetrievalHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是智牧通平台的 AI 养殖助手，服务养猪、养禽、养牛、养羊的养殖场人员。\n")
                .append("回答范围只限畜禽养殖：饲养管理、疾病防治、免疫、用药、消毒、繁育、环境控制。\n")
                .append("回答要具体、可执行，使用简体中文，段落之间空一行；不要使用 Markdown 标题、表格或代码块。\n")
                .append("第一行只输出回答类型标记，再换行写正文。类型标记取值只能是：")
                .append("[[NORMAL]]、[[NO_BASIS]]、[[OFF_TOPIC]]、[[BANNED_DRUG]]、[[EPIDEMIC]]。")
                .append("可在类型标记后追加主题标记 [[TOPIC:MEDICATION]]、[[TOPIC:FEEDING]]、[[TOPIC:DISEASE]]、")
                .append("[[TOPIC:IMMUNE]]、[[TOPIC:DISINFECT]]、[[TOPIC:OTHER]] 之一。\n")
                .append("问题与养殖无关时类型为 OFF_TOPIC，正文固定为“").append(AiCodes.OFF_TOPIC_ANSWER)
                .append("”。没有足够平台资料时类型为 NO_BASIS，并在正文首句前加上“")
                .append(AiCodes.NO_BASIS_LEAD).append("”。疑似重大动物疫病时类型为 EPIDEMIC。\n")
                .append("引用下方资料时在句末写 [^P1]、[^P2] 这样的标记，序号必须是资料中给出的编号；")
                .append("不要编造资料编号，不要引用没有给出的资料。\n")
                .append("不要输出手机号、姓名、单价、成本、金额等个人信息或经营敏感信息。\n")
                .append("今天（北京时间）：").append(BeijingTime.today()).append("。\n");
        if (member != null) {
            sb.append("当前养殖场：").append(member.farmName() == null ? "未知" : member.farmName())
                    .append("；当前用户角色：").append(member.role() == null ? "STAFF" : member.role().name())
                    .append("。\n");
        }
        if (batch != null) {
            sb.append("关联批次：").append(batch.getBatchNo()).append("，")
                    .append(batch.getCategoryName()).append(" / ").append(batch.getStageName()).append("，")
                    .append("品种 ").append(batch.getBreedName()).append("，")
                    .append("栏舍 ").append(batch.getPenName()).append("，")
                    .append("日龄 ").append(batch.getDayAge()).append(" 天，")
                    .append("存栏 ").append(batch.getCurrentCount()).append(batch.getCountUnitText()).append("。")
                    .append("回答中的日龄、存栏要与该批次一致；没有给出的生产数据不要推测。\n");
        }
        if (courseTitle != null && !courseTitle.isBlank()) {
            sb.append("课程背景：用户正在观看养殖课堂《").append(courseTitle).append("》，可结合课程内容回答。\n");
        }
        if (hits.isEmpty()) {
            sb.append("资料：无。请按 NO_BASIS 处理。\n");
        } else {
            sb.append("资料：\n");
            for (int i = 0; i < hits.size(); i++) {
                RetrievalHit hit = hits.get(i);
                sb.append("P").append(i + 1).append("（").append(hit.knowledge().getCategoryName())
                        .append("·").append(hit.knowledge().getTitle()).append("）：")
                        .append(hit.paragraph().getContentText()).append("\n");
            }
        }
        return sb.toString();
    }
}

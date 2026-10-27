package com.zhimu.slmp.ai.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.CourseContext;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.LinkedBatchVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.common.ai.AiTurn;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试直连千问时的提示词：角色与范围、当前养殖场、关联批次与课程背景、回答类型标记（见 {@link AnswerTypeTag}）与
 * 特殊回答规则（UI 文档 1.3 节第 8、9 条）；历史轮次只带文字，最多 10 轮。
 */
public final class DirectPrompt {

    /** 参与上下文的最近轮数 */
    public static final int CONTEXT_ROUNDS = 10;

    private DirectPrompt() {
    }

    @NonNull
    public static String system(@Nullable String farmName, @Nullable String species, @Nullable LinkedBatchVO batch,
                                @Nullable CourseContext course, @NonNull LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是“智牧通”App 的 AI 养殖助手，面向养殖户、场长和饲养员，回答饲养管理、常见病防治、用药规范三类问题。\n");
        sb.append("今天是").append(today.getYear()).append("年").append(today.getMonthValue()).append("月")
                .append(today.getDayOfMonth()).append("日。");
        if (farmName != null && !farmName.isEmpty()) {
            sb.append("当前养殖场：").append(farmName);
            if (species != null && !species.isEmpty()) {
                sb.append("（").append(species).append("）");
            }
            sb.append("。");
        }
        sb.append('\n');
        if (batch != null) {
            sb.append("用户关联了批次：").append(batch.getBatchNo()).append("，")
                    .append(nz(batch.getBreedName())).append(nz(AiText.stageOf(batch))).append("，")
                    .append(nz(batch.getPenName())).append("，").append(batch.getDayAge()).append(" 日龄，存栏 ")
                    .append(batch.getCurrentCount()).append(" ").append(nz(batch.getCountUnitText()))
                    .append("。回答时结合该批次的品种、日龄和存栏。\n");
        }
        if (course != null) {
            sb.append("用户正在学习课程《").append(course.getTitle()).append("》，问题可能与该课程有关。\n");
        }
        sb.append("回答要求：\n")
                .append("1. 第一行只输出回答类型标记，取值之一：[[NORMAL]]、[[NO_BASIS]]、[[OFF_TOPIC]]、[[BANNED_DRUG]]、[[EPIDEMIC]]；从第二行开始写正文。\n")
                .append("2. 与畜禽养殖无关的问题：标记 [[OFF_TOPIC]]，正文只写“这个问题与畜禽养殖无关，我不能回答。”\n")
                .append("3. 询问克伦特罗、莱克多巴胺、沙丁胺醇等食品动物禁用药物或禁用物质：标记 [[BANNED_DRUG]]，明确说明不能使用。\n")
                .append("4. 症状疑似非洲猪瘟、口蹄疫、高致病性禽流感等重大动物疫病：标记 [[EPIDEMIC]]，提醒尽快联系执业兽医并向当地动物防疫机构报告，并给出隔离、停止调运等措施。\n")
                .append("5. 你目前没有接入平台知识库。涉及具体兽药用量、休药期、免疫程序等需要权威标准而你没有把握时：标记 [[NO_BASIS]]，保留必要的不确定性说明，不给出未经核实的具体数值；其余情况标记 [[NORMAL]]。来源说明由界面独立展示，正文直接回答问题，不以“未找到平台知识依据”等模板句开头。\n")
                .append("6. 用简体中文，语气平实，300 字以内，分 1 至 3 段，段与段之间空一行；不要使用 Markdown 符号（如 **、#、-、表格）。\n")
                .append("7. 用药建议要提醒遵照兽医指导和休药期规定；不要编造具体数据。");
        return sb.toString();
    }

    /** 本次提问的文字（只有图片时给出默认说明） */
    @NonNull
    public static String user(@NonNull String question, int imageCount) {
        String q = question.trim();
        if (q.isEmpty()) {
            q = "请看看这些照片里的情况，可能是什么问题，应该怎么处理？";
        }
        return imageCount > 0 ? q + "\n（附 " + imageCount + " 张图片）" : q;
    }

    /** 历史轮次（只取已完成、参与上下文的最近 10 轮） */
    @NonNull
    public static List<AiTurn> history(@NonNull List<AiMessageVO> rounds) {
        List<AiMessageVO> usable = new ArrayList<>();
        for (AiMessageVO m : rounds) {
            if (AiCodes.STATUS_ANSWERED.equals(m.getStatus()) && m.isInContext() && m.getAnswerText() != null) {
                usable.add(m);
            }
        }
        int from = Math.max(0, usable.size() - CONTEXT_ROUNDS);
        List<AiTurn> turns = new ArrayList<>();
        for (AiMessageVO m : usable.subList(from, usable.size())) {
            String q = m.getQuestionText() == null || m.getQuestionText().isEmpty() ? "（图片提问）" : m.getQuestionText();
            turns.add(AiTurn.user(q));
            turns.add(AiTurn.assistant(AiText.stripCitations(m.getAnswerText())));
        }
        return turns;
    }

    /** 直连回答去掉 Markdown 加粗星号 */
    @NonNull
    public static String clean(@NonNull String text) {
        return text.replace("*", "");
    }

    @NonNull
    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }
}

package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeParagraphMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量检索：题干与知识段落做二元字组重合度打分，取前 5 段、同一条目最多 3 段。
 * 管理端-02 的全文索引上线后替换本实现，接口不变。
 */
@Service
@RequiredArgsConstructor
public class AiRetrievalService {

    private static final int MAX_HITS = 5;
    private static final int MAX_PER_KNOWLEDGE = 3;

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiKnowledgeParagraphMapper paragraphMapper;

    public List<RetrievalHit> search(String question, String extra) {
        String query = AiText.compact((question == null ? "" : question) + (extra == null ? "" : extra));
        if (query.isEmpty()) {
            return List.of();
        }
        List<AiKnowledge> knowledgeList = knowledgeMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiKnowledge>lambdaQuery()
                        .eq(AiKnowledge::getStatus, "ON_SHELF")
                        .orderByAsc(AiKnowledge::getSortNo)
                        .last("LIMIT 200"));
        if (knowledgeList.isEmpty()) {
            return List.of();
        }
        Map<Long, AiKnowledge> knowledgeById = new LinkedHashMap<>();
        for (AiKnowledge knowledge : knowledgeList) {
            knowledgeById.put(knowledge.getId(), knowledge);
        }
        List<AiKnowledgeParagraph> paragraphs = paragraphMapper.selectByKnowledgeIds(new ArrayList<>(knowledgeById.keySet()));
        Set<String> queryGrams = bigrams(query);
        List<Scored> scored = new ArrayList<>();
        for (AiKnowledgeParagraph paragraph : paragraphs) {
            AiKnowledge knowledge = knowledgeById.get(paragraph.getKnowledgeId());
            if (knowledge == null) {
                continue;
            }
            int score = overlap(queryGrams, bigrams(AiText.compact(paragraph.getContentText())));
            score += overlap(queryGrams, bigrams(AiText.compact(knowledge.getTitle()))) * 2;
            if (score > 0) {
                scored.add(new Scored(new RetrievalHit(knowledge, paragraph), score));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        Map<Long, Integer> perKnowledge = new LinkedHashMap<>();
        List<RetrievalHit> result = new ArrayList<>();
        for (Scored item : scored) {
            Long knowledgeId = item.hit.knowledge().getId();
            int count = perKnowledge.getOrDefault(knowledgeId, 0);
            if (count >= MAX_PER_KNOWLEDGE) {
                continue;
            }
            perKnowledge.put(knowledgeId, count + 1);
            result.add(item.hit);
            if (result.size() >= MAX_HITS) {
                break;
            }
        }
        return result;
    }

    /** 禁用药固定回答引用“禁用兽药与禁用物质清单”中含该药名的段落 */
    public RetrievalHit bannedDrugHit(String drugName) {
        List<AiKnowledge> list = knowledgeMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiKnowledge>lambdaQuery()
                        .eq(AiKnowledge::getStatus, "ON_SHELF")
                        .like(AiKnowledge::getTitle, "禁用")
                        .orderByAsc(AiKnowledge::getSortNo)
                        .last("LIMIT 1"));
        if (list.isEmpty()) {
            return null;
        }
        AiKnowledge knowledge = list.get(0);
        List<AiKnowledgeParagraph> paragraphs = paragraphMapper.selectByKnowledgeId(knowledge.getId());
        AiKnowledgeParagraph chosen = null;
        for (AiKnowledgeParagraph paragraph : paragraphs) {
            if (paragraph.getContentText() != null && drugName != null
                    && paragraph.getContentText().contains(drugName)) {
                chosen = paragraph;
                break;
            }
        }
        if (chosen == null && !paragraphs.isEmpty()) {
            chosen = paragraphs.get(0);
        }
        return chosen == null ? null : new RetrievalHit(knowledge, chosen);
    }

    private static Set<String> bigrams(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null) {
            return result;
        }
        for (int i = 0; i + 1 < text.length(); i++) {
            String gram = text.substring(i, i + 2);
            if (!gram.isBlank()) {
                result.add(gram);
            }
        }
        if (result.isEmpty() && !text.isBlank()) {
            result.add(text);
        }
        return result;
    }

    private static int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String item : left) {
            if (right.contains(item)) {
                count++;
            }
        }
        return count;
    }

    private record Scored(RetrievalHit hit, int score) {
    }
}

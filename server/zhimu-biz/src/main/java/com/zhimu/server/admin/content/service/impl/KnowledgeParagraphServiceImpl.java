package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.entity.KnowledgeVersion;
import com.zhimu.server.admin.content.mapper.KnowledgeVersionMapper;
import com.zhimu.server.admin.content.service.KnowledgeParagraphService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentMediaSupport;
import com.zhimu.server.admin.content.support.KnowledgeHtml;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeParagraphMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.UploadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 知识正文清洗、段落拆分与发布写入。 */
@Service
@RequiredArgsConstructor
public class KnowledgeParagraphServiceImpl implements KnowledgeParagraphService {

    private static final Set<String> IMAGE_SCENES = Set.of(FileScene.KNOWLEDGE_MEDIA.name());

    private final KnowledgeVersionMapper versionMapper;
    private final AiKnowledgeParagraphMapper paragraphMapper;
    private final CourseMapper courseMapper;
    private final FileMapper fileMapper;
    private final ContentMediaSupport media;

    @Override
    public Set<String> historicalKeys(Long knowledgeId) {
        Set<String> keys = new HashSet<>();
        for (KnowledgeVersion version : versionMapper.selectPublished(knowledgeId)) {
            keys.addAll(KnowledgeHtml.extractKeys(version.getContent()));
        }
        KnowledgeVersion draft = versionMapper.selectDraft(knowledgeId);
        if (draft != null) {
            keys.addAll(KnowledgeHtml.extractKeys(draft.getContent()));
        }
        for (AiKnowledgeParagraph paragraph : paragraphMapper.selectByKnowledgeId(knowledgeId)) {
            keys.add(paragraph.getParaKey());
        }
        return keys;
    }

    @Override
    public KnowledgeHtml.Parsed resolve(Long knowledgeId, String content) {
        if (content == null || content.isBlank()) {
            throw ContentErrors.field("content", "请填写正文");
        }
        KnowledgeHtml.Parsed parsed = KnowledgeHtml.parse(content, historicalKeys(knowledgeId));
        List<KnowledgeHtml.Paragraph> resolved = new ArrayList<>();
        for (KnowledgeHtml.Paragraph paragraph : parsed.paragraphs()) {
            if ("VIDEO".equals(paragraph.paraType())) {
                if (paragraph.courseId() == null) {
                    throw ContentErrors.field("content", "正文中的视频已下架，请更换");
                }
                Course course = courseMapper.selectById(paragraph.courseId());
                if (course == null || !"ON_SHELF".equals(course.getStatus())) {
                    throw ContentErrors.field("content", "正文中的视频已下架，请更换");
                }
                resolved.add(paragraph.withText(course.getTitle()));
            } else if ("IMAGE".equals(paragraph.paraType())) {
                resolved.add(resolveImage(paragraph));
            } else {
                resolved.add(paragraph);
            }
        }
        String canonical = KnowledgeHtml.render(resolved);
        String text = KnowledgeHtml.textOf(resolved);
        if (text.isBlank() || text.length() > KnowledgeHtml.MAX_TEXT_LENGTH) {
            throw ContentErrors.field("content", "正文需为 1–20000 字");
        }
        return new KnowledgeHtml.Parsed(canonical, text, resolved);
    }

    private KnowledgeHtml.Paragraph resolveImage(KnowledgeHtml.Paragraph paragraph) {
        if (paragraph.imageFileId() != null) {
            FileEntity file = fileMapper.selectById(paragraph.imageFileId());
            if (file == null || !IMAGE_SCENES.contains(file.getScene())
                    || !UploadStatus.READY.name().equals(file.getUploadStatus())) {
                throw ContentErrors.field("content", "正文中的图片不存在或尚未上传完成");
            }
            return paragraph.withImageUrl(media.publicUrl(file));
        }
        if (paragraph.imageUrl() == null) {
            throw ContentErrors.field("content", "正文中的图片地址不正确");
        }
        return paragraph;
    }

    @Override
    @Transactional
    public void replace(Long knowledgeId, Integer versionNo, KnowledgeHtml.Parsed parsed, boolean online) {
        Map<String, Integer> cites = new HashMap<>();
        for (AiKnowledgeParagraph old : paragraphMapper.selectByKnowledgeId(knowledgeId)) {
            cites.put(old.getParaKey(), old.getCiteCount30d() == null ? 0 : old.getCiteCount30d());
        }
        paragraphMapper.delete(Wrappers.<AiKnowledgeParagraph>lambdaQuery()
                .eq(AiKnowledgeParagraph::getKnowledgeId, knowledgeId));
        int no = 1;
        for (KnowledgeHtml.Paragraph paragraph : parsed.paragraphs()) {
            AiKnowledgeParagraph entity = new AiKnowledgeParagraph();
            entity.setKnowledgeId(knowledgeId);
            entity.setParaKey(paragraph.paraKey());
            entity.setParaNo(no++);
            entity.setVersionNo(versionNo);
            entity.setParaType(paragraph.paraType());
            entity.setContentHtml(paragraph.contentHtml());
            entity.setContentText(paragraph.contentText());
            entity.setImageUrl(paragraph.imageUrl());
            entity.setImageFileId(paragraph.imageFileId());
            entity.setCourseId(paragraph.courseId());
            entity.setCiteCount30d(cites.getOrDefault(paragraph.paraKey(), 0));
            entity.setIsOnline(online ? 1 : 0);
            paragraphMapper.insert(entity);
        }
    }

    @Override
    public List<AiKnowledgeParagraph> onlineParagraphs(Long knowledgeId) {
        return paragraphMapper.selectByKnowledgeId(knowledgeId);
    }

    /** 供版本差异使用：把已存版本正文解析为段落（不校验视频 / 图片）。 */
    public List<KnowledgeHtml.Paragraph> parsedParagraphs(String content) {
        return KnowledgeHtml.parse(content, null).paragraphs();
    }

    /** 段落键 → 近 30 天引用次数 */
    public Map<String, Integer> citeCounts(List<AiKnowledgeParagraph> paragraphs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (AiKnowledgeParagraph paragraph : paragraphs) {
            map.put(paragraph.getParaKey(), paragraph.getCiteCount30d() == null ? 0 : paragraph.getCiteCount30d());
        }
        return map;
    }
}

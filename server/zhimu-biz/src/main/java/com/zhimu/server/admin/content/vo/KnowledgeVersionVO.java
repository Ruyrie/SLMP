package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 版本正文与差异（ADM-C50）。 */
@Data
public class KnowledgeVersionVO {

    private Integer versionNo;
    private String title;
    private String categoryName;
    private List<String> applicableCategoryNames = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();
    private String content;
    private List<CompareItem> compare = new ArrayList<>();
    private List<ParagraphDiff> paragraphDiff = new ArrayList<>();

    @Data
    public static class CompareItem {
        private String field;
        private String fieldText;
        private String oldValue;
        private String newValue;
        private Boolean changed;
    }

    @Data
    public static class ParagraphDiff {
        private String paraKey;
        private String diff;
    }
}

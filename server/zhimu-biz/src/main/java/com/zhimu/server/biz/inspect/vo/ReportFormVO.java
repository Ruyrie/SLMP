package com.zhimu.server.biz.inspect.vo;

import com.zhimu.server.common.vo.MediaItemVO;
import lombok.Data;

import java.util.List;

/** API-I14 异常上报表单上下文。source：INSPECTION / DIRECT。 */
@Data
public class ReportFormVO {

    private String source;
    private String sourceText;
    private Long inspectionId;
    private List<ReportPen> pens;
    private ReportPrefill prefill;
    private List<SymptomVO> symptoms;
    private List<String> notifyNames;
    private int maxMediaCount;
    private int maxVideoCount;

    @Data
    public static class ReportPen {
        private long penId;
        private String penName;
        private List<ReportBatch> batches;
    }

    @Data
    public static class ReportBatch {
        private long batchId;
        private String batchNo;
        private long categoryId;
        private int stockCount;
        private String breedTitle;
        private Integer dayAge;
    }

    @Data
    public static class ReportPrefill {
        private Long penId;
        private Long batchId;
        private String description;
        private List<MediaItemVO> media;
    }
}

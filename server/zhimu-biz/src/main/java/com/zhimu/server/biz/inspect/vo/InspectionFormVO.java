package com.zhimu.server.biz.inspect.vo;

import com.zhimu.server.common.vo.OptionVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.util.List;

/** API-I06 登记 / 修改 / 补录表单上下文。mode：REGISTER / MODIFY / BACKFILL。 */
@Data
public class InspectionFormVO {

    private String mode;
    private FormPen pen;
    private List<FormBatch> batches;
    private int stockCount;
    private long categoryId;
    private String inspectDate;
    private UserBriefVO registrant;
    private boolean canChooseExecutor;
    private boolean canManualMark;
    private List<OptionVO> members;
    private long defaultExecutorId;
    private InspectTemplate template;
    private String deadlineTime;
    private boolean deadlinePassed;
    private RegisteredInfo alreadyRegistered;
    private boolean canReport;
    private String abnormalLockedReason;
    private List<String> backfillDates;
    private DateRange backfillRange;
    private InspectionDetailVO record;
    private Double farmLatitude;
    private Double farmLongitude;

    @Data
    public static class FormPen {
        private long penId;
        private String penCode;
        private String penName;
        private List<String> responsibleNames;
    }

    @Data
    public static class FormBatch {
        private long batchId;
        private String batchNo;
        private int stockCount;
    }

    @Data
    public static class InspectTemplate {
        private long templateId;
        private int templateVersion;
        private List<TemplateItem> items;
    }

    @Data
    public static class TemplateItem {
        private long itemId;
        private String itemName;
        private int sortNo;
        private List<TemplateOption> options;
    }

    @Data
    public static class TemplateOption {
        private long optionId;
        private String optionName;
        private Boolean isAbnormal;
        private int sortNo;
    }

    @Data
    public static class RegisteredInfo {
        private long inspectionId;
        private String registrantName;
        private String registeredAt;
    }

    @Data
    public static class DateRange {
        private String from;
        private String to;
    }
}

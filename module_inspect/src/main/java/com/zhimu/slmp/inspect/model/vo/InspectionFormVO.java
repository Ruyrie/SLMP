package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.OptionVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * API-I06 登记 / 修改 / 补录表单上下文（I02、I03）。mode：REGISTER / MODIFY / BACKFILL。
 * <p>
 * farm_latitude、farm_longitude 为客户端暂定字段（文档待定事项 2：InspectionFormVO 未返回养殖场坐标），
 * 用于登记前的现场判定，服务端以自己的计算为准；没有时位置显示“无位置”。
 */
public class InspectionFormVO {

    public static final String MODE_REGISTER = "REGISTER";
    public static final String MODE_MODIFY = "MODIFY";
    public static final String MODE_BACKFILL = "BACKFILL";
    public static final String LOCKED_CHECK = "CHECK";
    public static final String LOCKED_REPORTED = "REPORTED";

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
    /** 客户端暂定字段（待服务端确认） */
    private Double farmLatitude;
    private Double farmLongitude;

    public String getMode() { return mode; }

    public FormPen getPen() { return pen; }

    @NonNull
    public List<FormBatch> getBatches() { return batches == null ? Collections.emptyList() : batches; }

    public int getStockCount() { return stockCount; }

    public long getCategoryId() { return categoryId; }

    public String getInspectDate() { return inspectDate; }

    public UserBriefVO getRegistrant() { return registrant; }

    public boolean isCanChooseExecutor() { return canChooseExecutor; }

    public boolean isCanManualMark() { return canManualMark; }

    @NonNull
    public List<OptionVO> getMembers() { return members == null ? Collections.emptyList() : members; }

    public long getDefaultExecutorId() { return defaultExecutorId; }

    public InspectTemplate getTemplate() { return template; }

    public String getDeadlineTime() { return deadlineTime; }

    public boolean isDeadlinePassed() { return deadlinePassed; }

    @Nullable
    public RegisteredInfo getAlreadyRegistered() { return alreadyRegistered; }

    public boolean isCanReport() { return canReport; }

    @Nullable
    public String getAbnormalLockedReason() { return abnormalLockedReason; }

    @NonNull
    public List<String> getBackfillDates() { return backfillDates == null ? Collections.emptyList() : backfillDates; }

    @Nullable
    public DateRange getBackfillRange() { return backfillRange; }

    @Nullable
    public InspectionDetailVO getRecord() { return record; }

    @Nullable
    public Double getFarmLatitude() { return farmLatitude; }

    @Nullable
    public Double getFarmLongitude() { return farmLongitude; }

    /** 批次号（多个用“、”连接） */
    @NonNull
    public String batchNoText() {
        List<String> nos = new ArrayList<>();
        for (FormBatch b : getBatches()) {
            nos.add(b.batchNo);
        }
        return android.text.TextUtils.join("、", nos);
    }

    /** 模板项目（按 sort_no） */
    @NonNull
    public List<TemplateItem> sortedItems() {
        if (template == null || template.items == null) {
            return Collections.emptyList();
        }
        List<TemplateItem> list = new ArrayList<>(template.items);
        Collections.sort(list, Comparator.comparingInt(i -> i.sortNo));
        return list;
    }

    public static class FormPen {
        private long penId;
        private String penCode;
        private String penName;
        private List<String> responsibleNames;

        public long getPenId() { return penId; }

        public String getPenCode() { return penCode; }

        public String getPenName() { return penName; }

        @NonNull
        public List<String> getResponsibleNames() {
            return responsibleNames == null ? Collections.emptyList() : responsibleNames;
        }
    }

    public static class FormBatch {
        private long batchId;
        private String batchNo;
        private int stockCount;

        public long getBatchId() { return batchId; }

        public String getBatchNo() { return batchNo; }

        public int getStockCount() { return stockCount; }
    }

    public static class InspectTemplate {
        private long templateId;
        private int templateVersion;
        private List<TemplateItem> items;

        public long getTemplateId() { return templateId; }

        public int getTemplateVersion() { return templateVersion; }

        @NonNull
        public List<TemplateItem> getItems() { return items == null ? Collections.emptyList() : items; }
    }

    public static class TemplateItem {
        private long itemId;
        private String itemName;
        private int sortNo;
        private List<TemplateOption> options;

        public long getItemId() { return itemId; }

        public String getItemName() { return itemName; }

        public int getSortNo() { return sortNo; }

        /** 按 sort_no */
        @NonNull
        public List<TemplateOption> getOptions() {
            if (options == null) {
                return Collections.emptyList();
            }
            List<TemplateOption> list = new ArrayList<>(options);
            Collections.sort(list, Comparator.comparingInt(o -> o.sortNo));
            return list;
        }

        @Nullable
        public TemplateOption findOption(long optionId) {
            if (options != null) {
                for (TemplateOption o : options) {
                    if (o.optionId == optionId) {
                        return o;
                    }
                }
            }
            return null;
        }
    }

    public static class TemplateOption {
        private long optionId;
        private String optionName;
        private boolean isAbnormal;
        private int sortNo;

        public long getOptionId() { return optionId; }

        public String getOptionName() { return optionName; }

        public boolean isAbnormal() { return isAbnormal; }

        public int getSortNo() { return sortNo; }
    }

    public static class RegisteredInfo {
        private long inspectionId;
        private String registrantName;
        private String registeredAt;

        public long getInspectionId() { return inspectionId; }

        public String getRegistrantName() { return registrantName; }

        public String getRegisteredAt() { return registeredAt; }
    }

    public static class DateRange {
        private String from;
        private String to;

        public String getFrom() { return from; }

        public String getTo() { return to; }
    }
}

package com.zhimu.server.admin.audit.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-R06 资料变更列表行。 */
@Data
public class FarmChangeItemVO {

    private Long changeId;
    private Long farmId;
    private String farmName;
    private String ownerName;
    /** 查询时为完整号码，Service 输出前打码 */
    private String ownerPhone;
    /** 查询用，Service 转成 change_items 后不输出 */
    @JsonIgnore
    private Boolean nameChanged;
    @JsonIgnore
    private Boolean addressChanged;
    @JsonIgnore
    private Boolean qualChanged;
    private List<String> changeItems = new ArrayList<>();
    private List<String> changeItemsText = new ArrayList<>();
    private String remark;
    private LocalDateTime submittedAt;
    private Integer waitMinutes;
    private String waitText;
    private Boolean overdue;
    private String status;
    private String statusText;
    private String auditorName;
    private LocalDateTime auditedAt;
    private String auditOpinion;
    private String rejectReason;
}

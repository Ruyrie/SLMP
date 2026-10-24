package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** ADM-D17 / D18 添加、编辑免疫项目。 */
@Data
public class ImmuneItemSaveRequest {

    @NotNull(message = "请选择疫苗")
    private Long vaccineId;

    @NotBlank(message = "请填写免次")
    @Size(max = 10, message = "免次最多 10 个字")
    private String doseRound;

    /** SCHEDULE_TYPE：BY_DAY_AGE / BY_DATE */
    @NotBlank(message = "请选择计划时间")
    private String scheduleType;

    private Integer dayAge;

    private Integer planMonth;

    private Integer planDay;

    private Integer advanceDays;

    @NotNull(message = "请填写剂量")
    @DecimalMin(value = "0.01", message = "剂量必须大于 0")
    @Digits(integer = 6, fraction = 2, message = "剂量最多 2 位小数")
    private BigDecimal dose;

    @NotBlank(message = "请选择剂量单位")
    private String doseUnit;

    @NotBlank(message = "请选择接种方式")
    private String vaccinateMethodCode;

    @Size(max = 100, message = "备注最多 100 个字")
    private String remark;

    @NotNull(message = "缺少版本号")
    private Integer version;
}

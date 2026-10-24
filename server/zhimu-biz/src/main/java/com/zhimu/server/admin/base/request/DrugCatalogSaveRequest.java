package com.zhimu.server.admin.base.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** ADM-D26 / D27 新增、编辑兽药疫苗目录。 */
@Data
public class DrugCatalogSaveRequest {

    /** DRUG_CATEGORY：VET_DRUG / VACCINE / DISINFECTANT */
    @NotBlank(message = "请选择类别")
    private String drugCategory;

    @NotBlank(message = "请填写通用名称")
    @Size(max = 40, message = "通用名称最多 40 个字")
    private String name;

    @Size(max = 100, message = "别名最多 100 个字")
    private String alias;

    @NotBlank(message = "请填写剂型")
    @Size(max = 20, message = "剂型最多 20 个字")
    private String dosageForm;

    @NotBlank(message = "请填写规格")
    @Size(max = 20, message = "规格最多 20 个字")
    private String spec;

    @NotBlank(message = "请填写记账单位")
    @Size(max = 10, message = "记账单位最多 10 个字")
    private String measureUnit;

    @NotBlank(message = "请填写包装单位")
    @Size(max = 10, message = "包装单位最多 10 个字")
    private String packUnit;

    @NotNull(message = "请填写每个包装折合数量")
    @DecimalMin(value = "0.001", message = "每个包装折合数量必须大于 0")
    @Digits(integer = 7, fraction = 3, message = "每个包装折合数量最多 3 位小数")
    private BigDecimal packSize;

    private String vaccineDoseUnit;

    private String defaultVaccinateMethodCode;

    @Size(max = 20, message = "推荐浓度最多 20 个字")
    private String concentration;

    private List<String> disinfectMethodCodes = new ArrayList<>();

    /** 兽药、疫苗四个畜种各一行；消毒剂不传 */
    @Valid
    private List<Usage> usages = new ArrayList<>();

    /** 修改休药期时必填 */
    @Size(max = 100, message = "修改原因最多 100 个字")
    private String changeReason;

    /** 编辑时必填 */
    private Integer version;

    @Data
    public static class Usage {
        @NotBlank(message = "请选择畜种")
        private String species;

        private Boolean applicable;

        private String adminRoute;

        private BigDecimal recommendedDose;

        private String doseUnit;

        private Integer courseDays;

        private Integer withdrawalDays;
    }
}

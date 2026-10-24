package com.zhimu.server.admin.base.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-D24 兽药疫苗目录列表筛选。 */
@Getter
@Setter
public class DrugCatalogQuery extends PageQuery {

    /** DRUG_CATEGORY：VET_DRUG / VACCINE / DISINFECTANT，必填 */
    private String drugCategory;

    @Size(max = 40, message = "搜索关键字最多 40 个字")
    private String keyword;

    /** SPECIES：PIG / POULTRY / CATTLE / SHEEP；按 applicable = 1 过滤 */
    private String species;

    @Size(max = 20, message = "剂型最多 20 个字")
    private String dosageForm;

    /** BASE_STATUS：ENABLED / DISABLED */
    private String status;
}

package com.zhimu.server.admin.base.convert;

import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.ImmuneTemplateItem;
import com.zhimu.server.admin.base.entity.InspectItem;
import com.zhimu.server.admin.base.entity.InspectOption;
import com.zhimu.server.admin.base.support.BaseAdminTexts;
import com.zhimu.server.admin.base.vo.ImmuneItemVO;
import com.zhimu.server.admin.base.vo.InspectItemVO;
import com.zhimu.server.admin.base.vo.InspectOptionVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 免疫模板 / 巡检模板实体 → VO。 */
public final class TemplateAdminConvert {

    private TemplateAdminConvert() {
    }

    public static ImmuneItemVO immuneItem(ImmuneTemplateItem item, Map<Long, String> vaccineNames,
                                          Map<String, String> methodNames) {
        ImmuneItemVO vo = new ImmuneItemVO();
        fillImmuneItem(vo, item, vaccineNames, methodNames);
        return vo;
    }

    public static void fillImmuneItem(ImmuneItemVO vo, ImmuneTemplateItem item, Map<Long, String> vaccineNames,
                                      Map<String, String> methodNames) {
        vo.setItemId(item.getId());
        vo.setSortNo(item.getSortNo());
        vo.setVaccineId(item.getVaccineId());
        vo.setVaccineName(vaccineNames == null ? null : vaccineNames.get(item.getVaccineId()));
        vo.setDoseRound(item.getDoseRound());
        vo.setScheduleType(item.getScheduleType());
        vo.setScheduleTypeText(BaseAdminTexts.scheduleType(item.getScheduleType()));
        vo.setDayAge(item.getDayAge());
        vo.setPlanMonth(item.getPlanMonth());
        vo.setPlanDay(item.getPlanDay());
        vo.setAdvanceDays(item.getAdvanceDays());
        vo.setDose(item.getDose());
        vo.setDoseUnit(item.getDoseUnit());
        vo.setDoseUnitText(BaseAdminTexts.vaccineDoseUnit(item.getDoseUnit()));
        vo.setVaccinateMethodCode(item.getVaccinateMethodCode());
        vo.setVaccinateMethodText(methodNames == null ? null : methodNames.get(item.getVaccinateMethodCode()));
        vo.setRemark(item.getRemark());
        vo.setChangeType(item.getChangeType());
    }

    public static InspectItemVO inspectItem(InspectItem item, List<InspectOption> options) {
        InspectItemVO vo = new InspectItemVO();
        fillInspectItem(vo, item, options);
        return vo;
    }

    public static void fillInspectItem(InspectItemVO vo, InspectItem item, List<InspectOption> options) {
        vo.setItemId(item.getId());
        vo.setSortNo(item.getSortNo());
        vo.setItemName(item.getItemName());
        vo.setCheckNote(item.getCheckNote());
        vo.setEnabled(Boolean.TRUE.equals(item.getEnabled()));
        vo.setDisableReason(item.getDisableReason());
        vo.setChangeType(item.getChangeType());
        vo.getOptions().clear();
        if (options != null) {
            for (InspectOption option : options) {
                vo.getOptions().add(new InspectOptionVO(option.getId(), option.getOptionName(),
                        Boolean.TRUE.equals(option.getIsAbnormal())));
            }
        }
    }

    /** “25 日龄”或“每年 9月15日”，用于发布差异文案 */
    public static String scheduleValue(ImmuneTemplateItem item) {
        if (item == null) {
            return null;
        }
        if ("BY_DATE".equals(item.getScheduleType())) {
            return "每年 " + item.getPlanMonth() + "月" + item.getPlanDay() + "日";
        }
        return item.getDayAge() + " 日龄";
    }

    /** “1 头份 / 头” */
    public static String doseValue(BigDecimal dose, String unit) {
        String number = dose == null ? "" : dose.stripTrailingZeros().toPlainString();
        String unitText = BaseAdminTexts.vaccineDoseUnit(unit);
        return unitText == null ? number : number + " " + unitText;
    }

    public static String vaccineName(ImmuneTemplateItem item, Map<Long, String> names) {
        if (names == null) {
            return null;
        }
        return names.get(item.getVaccineId());
    }

    public static String labelWithoutDose(String vaccineName, String doseRound) {
        return (vaccineName == null ? "疫苗" : vaccineName) + " · " + doseRound;
    }

    /** 结果串，例如“正常 / 稀软 / 水样 / 带血” */
    public static String optionsValue(List<InspectOption> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (InspectOption option : options) {
            if (!builder.isEmpty()) {
                builder.append(" / ");
            }
            builder.append(option.getOptionName());
        }
        return builder.toString();
    }

    public static String catalogName(DrugCatalog catalog) {
        return catalog == null ? null : catalog.getName();
    }
}

package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.biz.inspect.entity.InspectionSetting;
import com.zhimu.server.biz.inspect.mapper.InspectionSettingMapper;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.inspect.request.InspectionSettingRequest;
import com.zhimu.server.biz.inspect.service.InspectionSettingService;
import com.zhimu.server.biz.inspect.support.InspectRules;
import com.zhimu.server.biz.inspect.support.InspectSupport;
import com.zhimu.server.biz.inspect.vo.InspectionSettingVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** API-I04 / I05 实现。 */
@Service
@RequiredArgsConstructor
public class InspectionSettingServiceImpl implements InspectionSettingService {

    private static final List<String> QUICK_OPTIONS = List.of("16:00", "17:00", "18:00", "19:00");

    private final InspectionSettingMapper settingMapper;
    private final InspectSupport support;

    @Override
    public InspectionSettingVO current() {
        return toVo(support.setting());
    }

    @Override
    @Transactional
    public InspectionSettingVO update(InspectionSettingRequest request) {
        support.requireManager("只有所有者和场长可以设置巡检截止时间");
        LocalTime deadline = InspectRules.parseDeadline(request == null ? null : request.getDeadlineTime());
        FarmContext.FarmMember member = support.member();
        InspectionSetting setting = settingMapper.selectByFarmId(member.farmId());
        if (setting == null) {
            setting = new InspectionSetting();
            setting.setFarmId(member.farmId());
            setting.setRemindBeforeMin(60);
            setting.setDeadlineTime(deadline);
            setting.setUpdatedBy(member.userId());
            settingMapper.insert(setting);
        } else {
            setting.setDeadlineTime(deadline);
            setting.setUpdatedBy(member.userId());
            settingMapper.updateById(setting);
        }
        return toVo(setting);
    }

    private InspectionSettingVO toVo(InspectionSetting setting) {
        FarmContext.FarmMember member = support.member();
        LocalTime deadline = setting.getDeadlineTime() == null ? InspectRules.DEFAULT_DEADLINE
                : setting.getDeadlineTime();
        InspectionSettingVO vo = new InspectionSettingVO();
        vo.setFarmName(member.farmName());
        vo.setDeadlineTime(InspectRules.formatTime(deadline));
        vo.setRemindTime(InspectRules.formatTime(InspectRules.remindTime(deadline, setting.getRemindBeforeMin())));
        vo.setDefaultDeadline(InspectRules.formatTime(InspectRules.DEFAULT_DEADLINE));
        vo.setQuickOptions(new ArrayList<>(QUICK_OPTIONS));
        vo.setMinTime(InspectRules.formatTime(InspectRules.MIN_DEADLINE));
        vo.setMaxTime(InspectRules.formatTime(InspectRules.MAX_DEADLINE));
        if (member.isManagerView()) {
            List<String> names = new ArrayList<>();
            for (FarmMemberRow row : support.memberList()) {
                if ("OWNER".equals(row.getRole()) || "MANAGER".equals(row.getRole())) {
                    names.add(row.getName());
                }
            }
            vo.setOwnerManagerNames(names);
        } else {
            vo.setOwnerManagerNames(List.of());
        }
        if (setting.getUpdatedBy() != null) {
            vo.setUpdatedByName(support.userName(setting.getUpdatedBy()));
            vo.setUpdatedAt(BeijingTime.format(setting.getUpdatedAt()));
        }
        return vo;
    }
}

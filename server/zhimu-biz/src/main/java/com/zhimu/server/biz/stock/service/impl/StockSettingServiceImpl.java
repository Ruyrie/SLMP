package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.entity.StockSetting;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockSettingMapper;
import com.zhimu.server.biz.stock.request.StockSettingRequest;
import com.zhimu.server.biz.stock.service.StockSettingService;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.vo.StockSettingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 库存设置实现：每场一行，无记录时按默认 30 天；保存后立即生效（读取时按当天计算批号状态）。 */
@Service
@RequiredArgsConstructor
public class StockSettingServiceImpl implements StockSettingService {

    public static final int DEFAULT_NEAR_DAYS = 30;

    private final StockSettingMapper settingMapper;
    private final StockLookups lookups;

    @Override
    public StockSettingVO get() {
        return current();
    }

    @Override
    public StockSettingVO current() {
        StockSetting setting = find();
        StockSettingVO vo = new StockSettingVO();
        if (setting == null) {
            vo.setNearDaysVet(DEFAULT_NEAR_DAYS);
            vo.setNearDaysVaccine(DEFAULT_NEAR_DAYS);
            vo.setNearDaysDisinfectant(DEFAULT_NEAR_DAYS);
            vo.setVersion(0);
            return vo;
        }
        vo.setNearDaysVet(valueOr(setting.getNearDaysVet()));
        vo.setNearDaysVaccine(valueOr(setting.getNearDaysVaccine()));
        vo.setNearDaysDisinfectant(valueOr(setting.getNearDaysDisinfectant()));
        vo.setVersion(setting.getVersion() == null ? 0 : setting.getVersion());
        return vo;
    }

    @Override
    @Transactional
    public StockSettingVO save(StockSettingRequest request) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        StockSetting setting = find();
        int requestVersion = request.getVersion() == null ? 0 : request.getVersion();
        if (setting == null) {
            if (requestVersion != 0) {
                throw StockErrors.versionConflict();
            }
            StockSetting created = new StockSetting();
            created.setFarmId(farmId);
            created.setNearDaysVet(request.getNearDaysVet());
            created.setNearDaysVaccine(request.getNearDaysVaccine());
            created.setNearDaysDisinfectant(request.getNearDaysDisinfectant());
            created.setVersion(0);
            created.setCreatedBy(userId);
            created.setUpdatedBy(userId);
            settingMapper.insert(created);
            lookups.log("UPDATE", "STOCK_SETTING_SAVE", StockCodes.BIZ_STOCK_SETTING, created.getId(),
                    "临期天数设置", null, request.getNearDaysVet());
            return current();
        }
        int currentVersion = setting.getVersion() == null ? 0 : setting.getVersion();
        if (requestVersion != currentVersion) {
            throw StockErrors.versionConflict();
        }
        setting.setNearDaysVet(request.getNearDaysVet());
        setting.setNearDaysVaccine(request.getNearDaysVaccine());
        setting.setNearDaysDisinfectant(request.getNearDaysDisinfectant());
        setting.setUpdatedBy(userId);
        settingMapper.updateById(setting);
        lookups.log("UPDATE", "STOCK_SETTING_SAVE", StockCodes.BIZ_STOCK_SETTING, setting.getId(),
                "临期天数设置", currentVersion, request.getNearDaysVet());
        return current();
    }

    @Override
    public int nearDays(String category) {
        StockSettingVO setting = current();
        return switch (category == null ? "" : category) {
            case StockCodes.CAT_VACCINE -> setting.getNearDaysVaccine();
            case StockCodes.CAT_DISINFECTANT -> setting.getNearDaysDisinfectant();
            default -> setting.getNearDaysVet();
        };
    }

    private StockSetting find() {
        return settingMapper.selectOne(Wrappers.<StockSetting>lambdaQuery().last("LIMIT 1"));
    }

    private static int valueOr(Integer value) {
        return value == null ? DEFAULT_NEAR_DAYS : value;
    }
}
